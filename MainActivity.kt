package app.daybook
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.*
class MainActivity:Activity(){
 private lateinit var status:TextView;private lateinit var out:TextView;private lateinit var cmd:EditText;private lateinit var log:TextView
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,80,40,40)}
  fun btn(t:String,f:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setOnClickListener{f()}}.also{col.addView(it)}
  col.addView(TextView(this).apply{text="Daybook";textSize=30f})
  status=TextView(this).also{col.addView(it)}
  btn("1. Turn on Daybook automation"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  btn("2. Allow display over other apps"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
  btn("3. Allow exact alarms"){if(Build.VERSION.SDK_INT>=31)startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}
  btn("4. Don’t optimize battery"){startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))}
  btn("5. Grant calendar, mic, notifications"){requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR,Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS),1)}
  btn("6. Start background assistant"){startForegroundService(Intent(this,AssistantService::class.java))}
  col.addView(Switch(this).apply{text="Allow automatic sending (max ${Sender.DAILY_CAP}/day)";isChecked=Store.armed(this@MainActivity);setOnCheckedChangeListener{_,v->Store.setArmed(this@MainActivity,v)}})
  cmd=EditText(this).apply{hint="Type a command"};col.addView(cmd)
  btn("Run"){out.text=Commands.run(this,cmd.text.toString());cmd.setText("");log.text=Store.logText(this)}
  btn("🎤 Speak a command"){startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),7)}
  out=TextView(this).also{col.addView(it)}
  col.addView(TextView(this).apply{text="Activity log";textSize=18f;setPadding(0,30,0,0)})
  log=TextView(this).also{col.addView(it)}
  setContentView(ScrollView(this).apply{addView(col)})}
 override fun onResume(){super.onResume()
  status.text="Automation: ${if(SendAccessibilityService.instance!=null)"on" else "off"}   Overlay: ${if(Settings.canDrawOverlays(this))"allowed" else "not allowed"}"
  log.text=Store.logText(this)}
 @Deprecated("simple")
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d)
  if(r==7&&c==RESULT_OK){val t=d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?:return;cmd.setText(t);out.text=Commands.run(this,t);log.text=Store.logText(this)}}
}
