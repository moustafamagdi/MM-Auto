package com.magdi.auto;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
/** Read-only grant for exactly one sanitized report; no paths supplied by the caller. */
public final class SupportProvider extends ContentProvider {
 static Uri uri(Context c){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".support").appendPath("report.txt").build();}
 private boolean valid(Uri u){return uri(getContext()).equals(u);}
 public boolean onCreate(){return true;}
 public String getType(Uri u){return valid(u)?"text/plain":null;}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException {if(!valid(u)||!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),"reports/MM-support-report.txt"),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] projection,String selection,String[] args,String order){if(!valid(u))return null;File f=new File(getContext().getCacheDir(),"reports/MM-support-report.txt");if(!f.isFile())return null;MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
