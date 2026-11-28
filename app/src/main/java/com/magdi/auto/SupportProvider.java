package com.magdi.auto;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
/** Read-only grants to immutable sanitized snapshots. No caller-supplied directory paths. */
public final class SupportProvider extends ContentProvider {
 static Uri uri(Context c,String name){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".support").appendPath(name).build();}
 private boolean valid(Uri u){return u!=null&&"content".equals(u.getScheme())&&(getContext().getPackageName()+".support").equals(u.getAuthority())&&u.getQuery()==null&&u.getFragment()==null&&u.getPathSegments().size()==1&&ReportExport.validName(u.getLastPathSegment());}
 private File file(Uri u){return new File(new File(getContext().getCacheDir(),"reports"),u.getLastPathSegment());}
 public boolean onCreate(){return true;}
 public String getType(Uri u){return valid(u)?"text/plain":null;}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException {if(!valid(u)||!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] projection,String selection,String[] args,String order){if(!valid(u))return null;File f=file(u);if(!f.isFile())return null;MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
