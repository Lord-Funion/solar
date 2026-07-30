package dev.lordfunion.rockboxsolar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class SlskdClient {
    static final class Result { String username; JSONObject file; boolean free; int queue; long speed;
        String filename(){return file.optString("filename","");}long size(){return file.optLong("size",0);}int bitrate(){return file.optInt("bitRate",file.optInt("bitrate",0));}
        String label(){return new java.io.File(filename().replace('\\','/')).getName()+"\n"+username+" • "+(size()/1024/1024)+" MiB"+(bitrate()>0?" • "+bitrate()+" kbps":"")+(free?" • slot free":" • queue "+queue);}}
    private final OkHttpClient client; private final String base; private final String key;
    SlskdClient(OkHttpClient c,String b,String k){client=c;base=b.replaceAll("/+$","");key=k;}
    String state()throws Exception{return request("GET","/api/v0/application",null).toString();}
    List<Result> search(String text)throws Exception{
        String id=UUID.randomUUID().toString();JSONObject body=new JSONObject();body.put("id",id);body.put("searchText",text);body.put("fileLimit",1000);body.put("responseLimit",100);body.put("searchTimeout",15000);request("POST","/api/v0/searches",body);
        Thread.sleep(16000L);Object raw=requestAny("GET","/api/v0/searches/"+id+"/responses",null);return parseResults(raw);
    }
    void enqueue(Result r)throws Exception{JSONArray files=new JSONArray();files.put(r.file);requestAny("POST","/api/v0/transfers/downloads/"+encode(r.username),files);}
    private List<Result> parseResults(Object raw){ArrayList<Result> out=new ArrayList<Result>();JSONArray responses=raw instanceof JSONArray?(JSONArray)raw:(raw instanceof JSONObject?((JSONObject)raw).optJSONArray("responses"):null);if(responses==null&&raw instanceof JSONObject){JSONArray data=((JSONObject)raw).optJSONArray("data");if(data!=null)responses=data;}if(responses==null)return out;for(int i=0;i<responses.length();i++){JSONObject response=responses.optJSONObject(i);if(response==null)continue;String user=response.optString("username","");boolean free=response.optBoolean("hasFreeUploadSlot",response.optBoolean("slotFree",false));int queue=response.optInt("queueLength",0);long speed=response.optLong("uploadSpeed",0);JSONArray files=response.optJSONArray("files");if(files==null)continue;for(int f=0;f<files.length();f++){JSONObject file=files.optJSONObject(f);if(file==null)continue;Result x=new Result();x.username=user;x.file=file;x.free=free;x.queue=queue;x.speed=speed;out.add(x);}}return out;}
    private JSONObject request(String method,String path,JSONObject body)throws Exception{Object o=requestAny(method,path,body);if(o instanceof JSONObject)return(JSONObject)o;return new JSONObject().put("data",o);}
    private Object requestAny(String method,String path,Object body)throws Exception{Request.Builder b=new Request.Builder().url(base+path).header("X-API-Key",key).header("Accept","application/json");if("POST".equals(method)){String json=body==null?"{}":body.toString();b.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),json));}else b.get();Response r=client.newCall(b.build()).execute();try{String text=r.body()==null?"":r.body().string();if(!r.isSuccessful())throw new IOException("slskd HTTP "+r.code()+": "+text);if(text.trim().startsWith("["))return new JSONArray(text);return text.trim().length()==0?new JSONObject():new JSONObject(text);}finally{r.close();}}
    private static String encode(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20");}
}
