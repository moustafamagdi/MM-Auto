package com.magdi.auto;
import android.content.Context;import android.content.pm.*;import java.util.*;import org.json.*;import com.magdi.auto.install.*;
/** No titles, paths, URIs, exception messages, shell output or unrelated packages. */
public final class SupportEvents {
 public static void event(String kind,String packageName,String state,String mode){String target=target(packageName);if(target==null)return;SupportReporter.record("auto "+kind+" "+target+" "+state+" "+mode.toLowerCase(Locale.ROOT),System.currentTimeMillis());}
 private static String target(String p){if("com.magdi.auto".equals(p))return "auto";if("com.magdi.iptv".equals(p))return "iptv";if("com.magdi.tube".equals(p))return "tube";return null;}
 static String source(String p){if(p==null)return "unknown";switch(p){case "com.android.vending":return "play";case "com.google.android.packageinstaller":return "google";case "com.android.packageinstaller":return "android";case "com.miui.packageinstaller":return "xiaomi";case "com.samsung.android.packageinstaller":return "samsung";case "com.android.shell":return "shell";default:return "other";}}
 static List<JSONObject> contextEvents(Context c){List<JSONObject> out=new ArrayList<>();try{
  PackageManager pm=c.getPackageManager();long at=System.currentTimeMillis();String mode=InstallManager.mode(c).name().toLowerCase(Locale.ROOT);int permissions=(pm.canRequestPackageInstalls()?1:0)|(ShizukuChannel.running()?2:0)|(ShizukuChannel.granted()?4:0);
  out.add(new JSONObject().put("at",at).put("kind","permission").put("target","auto").put("method",mode).put("code",permissions));
  for(String p:new String[]{"com.magdi.auto","com.magdi.iptv","com.magdi.tube"}){JSONObject e=new JSONObject().put("at",at).put("kind","install-check").put("target",target(p));try{
   PackageInfo pkg=pm.getPackageInfo(p,0);InstallIdentity id=InstallIdentity.read(pm,p);
   e.put("state",id.matchesExpectedPair(pm)?"match":"mismatch").put("installer",source(id.installing)).put("initiator",source(id.initiating));
   String v=pkg.versionName;if(v!=null&&v.matches("[0-9][a-zA-Z0-9._-]{0,63}"))e.put("version",v);
  }catch(PackageManager.NameNotFoundException absent){e.put("state","not-installed");}catch(RuntimeException unavailable){e.put("state","unavailable");}out.add(e);}
 }catch(Exception ignored){}return out;}
 private SupportEvents(){}
}
