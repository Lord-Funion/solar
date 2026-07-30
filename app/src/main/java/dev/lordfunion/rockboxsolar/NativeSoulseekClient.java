package dev.lordfunion.rockboxsolar;
import android.os.Environment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.InflaterInputStream;
final class NativeSoulseekClient {
interface Listener { void onState(String state); void onResultsChanged(); void onTransfer(String state, File file); }
static final class Result {
String username; String filename; String extension; long size; int bitrate; int duration; boolean slotFree; int queueLength;
String label(){return new File(filename.replace('\\','/')).getName()+"\n"+username+" • "+human(size)+(bitrate>0?" • "+bitrate+" kbps":"")+(slotFree?" • slot free":" • queue "+queueLength);}
private static String human(long n){if(n>1024L*1024*1024)return String.format(Locale.US,"%.1f GiB",n/(1024d*1024d*1024d));if(n>1024L*1024)return String.format(Locale.US,"%.1f MiB",n/(1024d*1024d));return(n/1024)+" KiB";}
}
private static final Charset UTF8=Charset.forName("UTF-8");
private final Listener listener; private final ExecutorService pool=Executors.newCachedThreadPool(); private final List<Result> results=Collections.synchronizedList(new ArrayList<Result>());
private final Map<String,Peer> peers=new ConcurrentHashMap<String,Peer>(); private final Map<Integer,Pending> pending=new ConcurrentHashMap<Integer,Pending>();
private volatile Socket server; private volatile ServerSocket listenerSocket; private volatile boolean running; private volatile String username=""; private final Random random=new Random();
NativeSoulseekClient(Listener listener){this.listener=listener;}
List<Result> results(){synchronized(results){return new ArrayList<Result>(results);}}
void connect(final String host,final int port,final String user,final String password){disconnect();running=true;username=user;pool.execute(new Runnable(){public void run(){try{
listener.onState("Opening peer listener…");listenerSocket=new ServerSocket(0);pool.execute(new Runnable(){public void run(){acceptLoop();}});
server=new Socket();server.connect(new InetSocketAddress(host,port),10000);server.setTcpNoDelay(true);sendServer(1,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{str(o,user);str(o,password);u32(o,177);str(o,md5(user+password));u32(o,3001);}});
ServerMessage login=readServer(server.getInputStream());if(login.code!=1)throw new IOException("Expected login response, got "+login.code);Reader r=new Reader(login.payload);boolean ok=r.bool();if(!ok)throw new IOException("Login rejected: "+r.str());String greeting=r.str();
sendServer(2,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{u32(o,listenerSocket.getLocalPort());}});sendServer(35,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{u32(o,0);u32(o,0);}});sendServer(28,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{u32(o,2);}});
listener.onState("Connected as "+user+" • listening on "+listenerSocket.getLocalPort()+" • "+greeting);serverReadLoop();
}catch(final Exception e){listener.onState("Soulseek error: "+message(e));disconnectInternal();}}});}
void search(final String query){if(server==null||!server.isConnected()){listener.onState("Connect first");return;}results.clear();listener.onResultsChanged();final int token=random.nextInt(Integer.MAX_VALUE);try{sendServer(26,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{u32(o,token);str(o,query);}});listener.onState("Searching for “"+query+"”… Direct results require the listen port to be reachable.");}catch(Exception e){listener.onState("Search failed: "+message(e));}}
void queue(final Result result){Peer peer=peers.get(result.username);if(peer==null){listener.onState("The result connection is no longer open. Search again or use the slskd mode.");return;}peer.requested=result;try{peer.send(43,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{str(o,result.filename);}});listener.onState("Queued from "+result.username+": "+result.filename);}catch(Exception e){listener.onState("Queue failed: "+message(e));}}
void disconnect(){running=false;disconnectInternal();results.clear();peers.clear();pending.clear();}
private void disconnectInternal(){close(server);server=null;if(listenerSocket!=null)try{listenerSocket.close();}catch(Exception ignored){}listenerSocket=null;for(Peer p:peers.values())p.close();}
void shutdown(){disconnect();pool.shutdownNow();}
private void acceptLoop(){while(running&&listenerSocket!=null){try{final Socket socket=listenerSocket.accept();socket.setSoTimeout(60000);pool.execute(new Runnable(){public void run(){handleIncoming(socket);}});}catch(Exception e){if(running)listener.onState("Peer listener: "+message(e));break;}}}
private void handleIncoming(Socket socket){try{InputStream in=socket.getInputStream();int length=readU32(in);if(length<1||length>1024*1024)throw new IOException("Invalid peer init length");int initCode=readByte(in);byte[]payload=readFully(in,length-1);Reader init=new Reader(payload);
if(initCode==0){int token=init.u32();handleFileSocket(socket,token);return;}
if(initCode!=1)throw new IOException("Unknown peer init "+initCode);String remote=init.str();String type=init.str();int token=init.u32();
if("F".equals(type)){handleFileSocket(socket,readU32(in));return;}
if(!"P".equals(type)){close(socket);return;}Peer peer=new Peer(remote,socket);peers.put(remote,peer);peer.readLoop();
}catch(Exception e){close(socket);}}
private void handleFileSocket(Socket socket,int token){Pending p=pending.get(token);if(p==null){close(socket);return;}File dir=new File(new File(Environment.getExternalStorageDirectory(),"Music/RockboxSolar"),"Reach");dir.mkdirs();File output=unique(dir,new File(p.filename.replace('\\','/')).getName());File part=new File(output.getAbsolutePath()+".part");long offset=part.exists()?part.length():0;try{OutputStream out=socket.getOutputStream();u64(out,offset);FileOutputStream file=new FileOutputStream(part,true);byte[]buf=new byte[32768];long remaining=p.size-offset,done=offset;while(remaining>0){int n=socket.getInputStream().read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new EOFException("Peer closed early");file.write(buf,0,n);done+=n;remaining-=n;if(done%(1024*1024)<32768)listener.onTransfer("Downloading "+output.getName()+" • "+(done*100/Math.max(1,p.size))+"%",null);}file.flush();file.getFD().sync();file.close();close(socket);if(!part.renameTo(output))throw new IOException("Final rename failed");pending.remove(token);listener.onTransfer("Complete",output);}catch(Exception e){listener.onTransfer("Transfer failed: "+message(e),null);close(socket);}}
private void serverReadLoop(){try{while(running&&server!=null){ServerMessage m=readServer(server.getInputStream());if(m.code==41)listener.onState("Account was logged in elsewhere");}}catch(Exception e){if(running)listener.onState("Server disconnected: "+message(e));}finally{disconnectInternal();}}
private final class Peer{final String user;final Socket socket;final Object lock=new Object();volatile Result requested;Peer(String u,Socket s){user=u;socket=s;}
void send(int code,Writer writer)throws Exception{synchronized(lock){ByteArrayOutputStream payload=new ByteArrayOutputStream();writer.write(payload);OutputStream out=socket.getOutputStream();u32(out,4+payload.size());u32(out,code);out.write(payload.toByteArray());out.flush();}}
void readLoop(){try{InputStream in=socket.getInputStream();while(running&&!socket.isClosed()){int len=readU32(in);if(len<4||len>64*1024*1024)throw new IOException("Invalid peer message length");int code=readU32(in);byte[]data=readFully(in,len-4);if(code==9)parseResults(data,this);else if(code==40)transferRequest(data,this);else if(code==50)listener.onState("Upload denied by "+user+": "+new Reader(data).str());}}catch(Exception ignored){}finally{peers.remove(user);close();}}
void close(){NativeSoulseekClient.close(socket);}
}
private void parseResults(byte[] compressed,Peer peer)throws Exception{byte[]raw=inflate(compressed);Reader r=new Reader(raw);String remote=r.str();int token=r.u32();int count=r.u32();ArrayList<Result>batch=new ArrayList<Result>();for(int i=0;i<count&&r.remaining()>0;i++){r.u8();Result x=new Result();x.username=remote;x.filename=r.str();x.size=r.u64();x.extension=r.str();int attrs=r.u32();for(int a=0;a<attrs;a++){int code=r.u32(),value=r.u32();if(code==0)x.bitrate=value;else if(code==1)x.duration=value;}batch.add(x);}boolean slot=r.remaining()>0&&r.bool();int speed=r.remaining()>=4?r.u32():0;int queue=r.remaining()>=4?r.u32():0;for(Result x:batch){x.slotFree=slot;x.queueLength=queue;}results.addAll(batch);listener.onResultsChanged();}
private void transferRequest(byte[]data,Peer peer)throws Exception{Reader r=new Reader(data);int direction=r.u32(),token=r.u32();String filename=r.str();long size=direction==1?r.u64():0;if(direction!=1)return;Result wanted=peer.requested;if(wanted==null||!wanted.filename.equals(filename)){peer.send(41,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{u32(o,token);bool(o,false);str(o,"Cancelled");}});return;}pending.put(token,new Pending(filename,size));peer.send(41,new Writer(){public void write(ByteArrayOutputStream o)throws Exception{u32(o,token);bool(o,true);}});listener.onTransfer("Peer accepted; waiting for file connection…",null);}
private void sendServer(int code,Writer writer)throws Exception{Socket s=server;if(s==null)throw new IOException("Not connected");ByteArrayOutputStream p=new ByteArrayOutputStream();writer.write(p);synchronized(s){OutputStream out=s.getOutputStream();u32(out,4+p.size());u32(out,code);out.write(p.toByteArray());out.flush();}}
private static ServerMessage readServer(InputStream in)throws Exception{int len=readU32(in);if(len<4||len>64*1024*1024)throw new IOException("Invalid server message length "+len);int code=readU32(in);return new ServerMessage(code,readFully(in,len-4));}
private interface Writer{void write(ByteArrayOutputStream out)throws Exception;}
private static final class ServerMessage{final int code;final byte[]payload;ServerMessage(int c,byte[]p){code=c;payload=p;}}
private static final class Pending{final String filename;final long size;Pending(String f,long s){filename=f;size=s;}}
private static final class Reader{final byte[]d;int p;Reader(byte[]v){d=v;}int remaining(){return d.length-p;}int u8()throws IOException{if(p>=d.length)throw new EOFException();return d[p++]&255;}boolean bool()throws IOException{return u8()!=0;}int u32()throws IOException{if(p+4>d.length)throw new EOFException();int v=(d[p]&255)|((d[p+1]&255)<<8)|((d[p+2]&255)<<16)|((d[p+3]&255)<<24);p+=4;return v;}long u64()throws IOException{long lo=u32()&0xffffffffL,hi=u32()&0xffffffffL;return lo|(hi<<32);}String str()throws IOException{int n=u32();if(n<0||p+n>d.length)throw new EOFException();String s=new String(d,p,n,UTF8);p+=n;return s;}}
private static void str(ByteArrayOutputStream o,String s)throws Exception{byte[]b=s.getBytes(UTF8);u32(o,b.length);o.write(b);}private static void bool(ByteArrayOutputStream o,boolean v){o.write(v?1:0);}private static void u32(OutputStream o,int v)throws IOException{o.write(v&255);o.write((v>>>8)&255);o.write((v>>>16)&255);o.write((v>>>24)&255);}private static void u64(OutputStream o,long v)throws IOException{u32(o,(int)v);u32(o,(int)(v>>>32));}
private static int readByte(InputStream i)throws IOException{int v=i.read();if(v<0)throw new EOFException();return v;}private static int readU32(InputStream i)throws IOException{return readByte(i)|(readByte(i)<<8)|(readByte(i)<<16)|(readByte(i)<<24);}private static byte[]readFully(InputStream in,int n)throws IOException{byte[]b=new byte[n];int p=0;while(p<n){int r=in.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
private static byte[]inflate(byte[]b)throws IOException{InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(b));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]buf=new byte[32768];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);in.close();return out.toByteArray();}
private static String md5(String value)throws Exception{byte[]d=MessageDigest.getInstance("MD5").digest(value.getBytes(UTF8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();}
private static String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private static void close(java.io.Closeable c){if(c!=null)try{c.close();}catch(Exception ignored){}}
private static File unique(File dir,String name){String safe=name.replaceAll("[\\\\/:*?\"<>|]","_");File f=new File(dir,safe);if(!f.exists())return f;for(int i=2;i<10000;i++){f=new File(dir,i+"-"+safe);if(!f.exists())return f;}return new File(dir,System.currentTimeMillis()+"-"+safe);}
}
