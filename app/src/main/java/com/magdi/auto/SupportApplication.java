package com.magdi.auto;
public final class SupportApplication extends android.app.Application {
 @Override public void onCreate(){super.onCreate();SupportReporter.init(this);SupportReporter.record("Process started; version=auto",System.currentTimeMillis());}
}
