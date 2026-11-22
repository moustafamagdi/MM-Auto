package com.magdi.auto;
import android.app.*;import android.os.*;import android.content.*;import android.widget.*;import com.magdi.auto.install.InstallDiagnostics;import java.util.*;
/** Existing detailed installation report stays manual-only. */
public final class InstallReportActivity extends Activity {
 public void onCreate(Bundle b){super.onCreate(b);boolean ar="ar".equals(getIntent().getStringExtra("language"));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,48,24,48);ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);TextView text=new TextView(this);text.setText(ar?"جارٍ تجهيز تفاصيل التثبيت…":"Preparing installation details…");text.setTextIsSelectable(true);body.addView(text);
  SupportReporter.io.execute(()->{Map<String,String> apps=new LinkedHashMap<>();for(Releases.App a:Releases.App.values())apps.put(a.title,a.packageName);String report=InstallDiagnostics.report(this,apps);runOnUiThread(()->{if(isDestroyed()||isFinishing())return;text.setText(report);Button copy=UiStyle.button(this,ar?"نسخ":"Copy",false);body.addView(copy,UiStyle.actionParams(this));copy.setOnClickListener(v->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MM Auto diagnostics",report)));Button share=UiStyle.button(this,ar?"مشاركة":"Share",false);body.addView(share,UiStyle.actionParams(this));share.setOnClickListener(v->startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"MM Auto")));});});
 }
}
