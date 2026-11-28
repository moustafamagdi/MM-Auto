package com.magdi.auto;
import org.json.*;import java.util.regex.*;
/** Only explicitly enumerated diagnostic outcomes; never accepts raw logs. */
final class SupportPolicy {
 static JSONObject event(String text,long at){try{
  if(text.startsWith("Process started; version="))return new JSONObject().put("at",at).put("kind","app-start");
  Matcher m=Pattern.compile("^auto (download|install|install-check|release-check|repair|permission) (auto|iptv|tube) (started|ready|failed|cancelled|pending|finished|expired|match|mismatch|unavailable) (classic|shizuku|root|adb|unknown)$").matcher(text);
  if(!m.matches())return null;
  return new JSONObject().put("at",at).put("kind",m.group(1)).put("target",m.group(2)).put("state",m.group(3)).put("method",m.group(4));
 }catch(Exception e){return null;}}
}
