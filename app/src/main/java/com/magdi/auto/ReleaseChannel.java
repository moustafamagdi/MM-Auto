package com.magdi.auto;
import android.content.Context;
/** One explicit channel for all three apps. Existing installs default to stable. */
final class ReleaseChannel {
 static boolean preview(Context c){return ReleaseCache.preferences(c).getBoolean("include_preview",false);}
 static long generation(Context c){return ReleaseCache.preferences(c).getLong("channel_generation",0);}
 static void set(Context c,boolean preview){synchronized(ReleaseCache.class){
  if(preview(c)==preview)return;
  if(!ReleaseCache.preferences(c).edit().putBoolean("include_preview",preview)
    .putLong("channel_generation",generation(c)+1).commit())throw new IllegalStateException("Channel save failed");
 }}
 private ReleaseChannel(){}
}
