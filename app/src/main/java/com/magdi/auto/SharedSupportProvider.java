package com.magdi.auto;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
/** No report/account data; only pinned official MM packages can obtain the linking proof. */
public final class SharedSupportProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public Bundle call(String method,String arg,Bundle extras){
  String[] callers=getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
  if(callers==null||callers.length!=1||!SharedSupportIdentity.trusted(getContext().getPackageManager(),callers[0]))throw new SecurityException("Untrusted caller");
  if(!"identity".equals(method)||arg!=null||extras!=null)throw new IllegalArgumentException("Unsupported operation");
  SharedSupportIdentity.Identity id=SharedSupportIdentity.owned(getContext());Bundle b=new Bundle();b.putString("id",id.id);b.putString("secret",id.secret);return b;
 }
 public String getType(Uri u){return null;} public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new SecurityException();}
 public Uri insert(Uri u,ContentValues v){throw new SecurityException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException();}public int delete(Uri u,String s,String[] a){throw new SecurityException();}
}
