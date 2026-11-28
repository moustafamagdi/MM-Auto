package com.magdi.auto;
import android.content.Context;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class ReleaseChannelTest {
 Context context;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();ReleaseCache.preferences(context).edit().clear().commit();}
 JSONObject release(Releases.App app,int code,boolean preview)throws Exception {
  return new JSONObject().put("tag_name","v1.0."+code+(preview?"-preview":"")).put("prerelease",preview)
   .put("body","<!-- mm-version-code: "+code+" -->").put("assets",new JSONArray().put(new JSONObject()
    .put("name",app.canonicalName).put("state","uploaded").put("size",10)
    .put("browser_download_url","https://github.com/moustafamagdi/"+app.repo+"/releases/download/v1.0."+code+"/"+app.canonicalName)));
 }
 @Test public void stableIsDefaultAndNeverFallsBackToPreview()throws Exception {
  assertFalse(ReleaseChannel.preview(context));
  for(Releases.App app:Releases.App.values()){
   JSONArray list=new JSONArray().put(release(app,22,true)).put(release(app,20,false)).put(release(app,21,false));
   assertEquals(21,Releases.selectRelease(app,list,false).versionCode);
   assertEquals(22,Releases.selectRelease(app,list,true).versionCode);
   assertNull(Releases.selectRelease(app,new JSONArray().put(release(app,22,true)),false));
  }
 }
 @Test public void stableOvertakesPreviewAndDraftsAreExcluded()throws Exception {
  Releases.App app=Releases.App.AUTO;
  JSONArray list=new JSONArray().put(release(app,30,true)).put(release(app,31,false)).put(release(app,32,true).put("draft",true));
  assertEquals(31,Releases.selectRelease(app,list,true).versionCode);
 }
 @Test public void switchingChannelsInvalidatesCacheAndInFlightResults()throws Exception {
  Releases.App app=Releases.App.TUBE;ReleaseChannel.set(context,true);
  Releases.Release preview=Releases.parse(app,release(app,22,true));
  ReleaseCache.check(context,app,()->preview);assertNotNull(ReleaseCache.read(context,app));
  ReleaseChannel.set(context,false);assertNull(ReleaseCache.read(context,app));
  try{ReleaseCache.check(context,app,()->preview);fail();}catch(java.io.IOException expected){}
  try{ReleaseCache.check(context,app,()->{ReleaseChannel.set(context,true);ReleaseChannel.set(context,false);return Releases.parse(app,release(app,21,false));});fail();}catch(java.io.IOException expected){}
  assertNull(ReleaseCache.read(context,app));
 }
 @Test public void channelPersistsWithoutChangingInstallationPreferences()throws Exception {
  context.getSharedPreferences("installer",0).edit().putString("sentinel","preserve").commit();
  ReleaseChannel.set(context,true);
  assertTrue(ReleaseChannel.preview(context.createPackageContext(context.getPackageName(),0)));
  assertEquals("preserve",context.getSharedPreferences("installer",0).getString("sentinel",null));
 }
}
