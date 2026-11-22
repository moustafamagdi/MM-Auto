package com.magdi.auto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.magdi.auto.install.ShizukuChannel;
import java.util.Locale;

/** Guides user-owned setup; never changes system settings or starts an installer on resume. */
final class ShizukuGuide {
    static final String PACKAGE = "moe.shizuku.privileged.api";
    enum Stage { INSTALL, START, PERMISSION, READY }
    interface Probe {
        boolean installed(); boolean running(); boolean granted(); boolean blocked();
        void request();
    }
    static Probe system(Activity activity) { return new Probe() {
        public boolean installed() { try { activity.getPackageManager().getPackageInfo(PACKAGE,0); return true; }
            catch(android.content.pm.PackageManager.NameNotFoundException absent) { return false; } }
        public boolean running(){return ShizukuChannel.running();}
        public boolean granted(){return ShizukuChannel.granted();}
        public boolean blocked(){return ShizukuChannel.blocked();}
        public void request(){ShizukuChannel.request();}
    }; }
    static Stage stage(Probe p) {
        // A compatible Shizuku build can expose a live binder under a different package.
        if(p.running())return p.granted()?Stage.READY:Stage.PERMISSION;
        return p.installed()?Stage.START:Stage.INSTALL;
    }
    static boolean isXiaomi(String manufacturer,String brand) {
        String s=(manufacturer+" "+brand).toLowerCase(Locale.ROOT);
        return s.contains("xiaomi")||s.contains("poco")||s.contains("redmi");
    }
    final AlertDialog dialog;
    final Button primary;
    final TextView status,description;
    private final Activity activity;
    private final Probe probe;
    private final Runnable repair;
    private Stage displayed;
    private boolean displayedBlocked;

    ShizukuGuide(Activity activity,String app,Runnable repair) {
        this(activity,app,repair,system(activity),isXiaomi(Build.MANUFACTURER,Build.BRAND));
    }
    ShizukuGuide(Activity activity,String app,Runnable repair,Probe probe,boolean xiaomi) {
        this.activity=activity;this.probe=probe;this.repair=repair;
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(20*activity.getResources().getDisplayMetrics().density);
        content.setPadding(pad,pad,pad,pad);
        status=label(content, "",18);description=label(content,"",16);
        primary=button(content,R.string.guide_install,()->act());
        UiStyle.style(primary,true);
        button(content,R.string.guide_pair_help,()->new AlertDialog.Builder(activity)
            .setTitle(R.string.guide_pair_help).setMessage(R.string.guide_pair_steps)
            .setPositiveButton(R.string.guide_settings,(d,w)->openSettings())
            .setNeutralButton(R.string.guide_official,(d,w)->openUrl("https://shizuku.rikka.app/guide/setup/"))
            .setNegativeButton(R.string.close,null).show());
        if(xiaomi) {
            label(content,activity.getString(R.string.guide_xiaomi_title),18);
            label(content,activity.getString(R.string.guide_xiaomi_body),15);
            button(content,R.string.guide_settings,()->openSettings());
        }
        label(content,activity.getString(R.string.guide_finish_hint),14);
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        dialog=new AlertDialog.Builder(activity).setTitle(app==null?activity.getString(R.string.guide_title)
            :activity.getString(R.string.guide_for,app)).setView(scroll).setNegativeButton(R.string.close,null).create();
    }
    void show(){dialog.show();refresh();}
    void refresh(){
        Stage s=stage(probe);boolean blocked=s==Stage.PERMISSION&&probe.blocked();
        if(displayed==s&&displayedBlocked==blocked)return;
        displayed=s;displayedBlocked=blocked;
        int title,body,action;
        switch(s){
            case INSTALL:title=R.string.guide_install_title;body=R.string.guide_install_body;action=R.string.guide_install;break;
            case START:title=R.string.guide_start_title;body=R.string.guide_start_body;action=R.string.guide_open;break;
            case PERMISSION:title=R.string.guide_permission_title;body=blocked?R.string.guide_permission_blocked:R.string.guide_permission_body;action=blocked?R.string.guide_open:R.string.guide_allow;break;
            default:title=R.string.guide_ready_title;body=R.string.guide_ready_body;action=repair==null?R.string.close:R.string.guide_repair;
        }
        status.setText(title);description.setText(body);primary.setText(action);
    }
    private void act(){
        // Recheck on every tap: permission/service may disappear while the dialog is visible.
        Stage current=stage(probe);
        if(current!=displayed){refresh();return;}
        switch(current){
            case INSTALL:openUrl("https://shizuku.rikka.app/download/");break;
            case START:openManager();break;
            case PERMISSION:if(probe.blocked())openManager();else probe.request();break;
            case READY:dialog.dismiss();if(repair!=null)repair.run();break;
        }
    }
    private void openManager(){
        Intent i=activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if(i==null){Toast.makeText(activity,R.string.guide_open_manually,Toast.LENGTH_LONG).show();return;}
        open(i);
    }
    private void openSettings(){open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
    private void openUrl(String url){open(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    private void open(Intent i){try{activity.startActivity(i);}catch(RuntimeException unavailable){
        Toast.makeText(activity,R.string.guide_open_manually,Toast.LENGTH_LONG).show();}}
    private TextView label(LinearLayout parent,String text,int size){
        TextView v=new TextView(activity);v.setText(text);v.setTextSize(size);v.setPadding(0,8,0,12);
        boolean dark=(activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        v.setTextColor(dark?0xffeef3fc:0xff14233b);
        parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private Button button(LinearLayout parent,int text,Runnable action){
        Button b=UiStyle.button(activity,activity.getString(text),false);
        b.setOnClickListener(v->action.run());parent.addView(b,UiStyle.actionParams(activity));return b;
    }
}
