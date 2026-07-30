package dev.lordfunion.rockboxsolar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class DownloadProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri)throws FileNotFoundException{String encoded=uri.getQueryParameter("path");if(encoded==null)throw new FileNotFoundException();File file=new File(encoded);try{String root=Environment.getExternalStorageDirectory().getCanonicalPath()+File.separator;String path=file.getCanonicalPath();if(!path.startsWith(root))throw new FileNotFoundException("Outside external storage");}catch(Exception e){throw new FileNotFoundException(e.getMessage());}return file;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){String p=uri.getQueryParameter("path");return p!=null&&p.toLowerCase().endsWith(".apk")?"application/vnd.android.package-archive":"application/octet-stream";}
    @Override public Cursor query(Uri uri,String[]projection,String selection,String[]args,String sort){try{File f=resolve(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}catch(Exception e){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}@Override public int delete(Uri uri,String s,String[]a){return 0;}@Override public int update(Uri uri,ContentValues v,String s,String[]a){return 0;}
    public static Uri uri(File file){return new Uri.Builder().scheme("content").authority("dev.lordfunion.rockboxsolar.files").path("download").appendQueryParameter("path",file.getAbsolutePath()).build();}
}
