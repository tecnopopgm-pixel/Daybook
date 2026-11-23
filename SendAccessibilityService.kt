package app.daybook
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.*
import android.view.accessibility.*
import java.text.DateFormat
import java.util.Date
class SendAccessibilityService:AccessibilityService(){
 companion object{@Volatile var instance:SendAccessibilityService?=null;@Volatile var pending:Pending?=null}
 data class Pending(val app:String,val text:String,val expires:Long,var typed:Boolean=false)
 private var last=""
 override fun onServiceConnected(){instance=this}
 override fun onUnbind(i:Intent?):Boolean{instance=null;return super.onUnbind(i)}
 override fun onInterrupt(){}
 override fun onAccessibilityEvent(e:AccessibilityEvent){
  val pkg=e.packageName?.toString()?:return
  // screen activity log: which app came to the foreground
  if(e.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&pkg!=last&&pkg!=packageName&&!pkg.startsWith("com.android.systemui")){
   last=pkg;Store.log(this,"${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())} opened $pkg")}
  val p=pending?:return
  if(System.currentTimeMillis()>p.expires){pending=null;return}
  val root=rootInActiveWindow?:return
  if(root.packageName?.toString()!=(if(p.app=="wa")"com.whatsapp" else "com.instagram.android"))return
  if(p.app=="ig"&&!p.typed){
   val box=find(root){it.className?.toString()=="android.widget.EditText"}
   if(box!=null){box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,p.text)});p.typed=true}
   return}
  val btn=(if(p.app=="wa")root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send").firstOrNull() else null)
   ?:root.findAccessibilityNodeInfosByText("Send").firstOrNull{it.className?.toString()!="android.widget.EditText"}
  if(btn!=null&&click(btn)){pending=null;Handler(Looper.getMainLooper()).postDelayed({performGlobalAction(GLOBAL_ACTION_HOME)},1500)}}
 private fun find(n:AccessibilityNodeInfo,f:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{
  if(f(n))return n
  for(i in 0 until n.childCount){n.getChild(i)?.let{c->find(c,f)?.let{return it}}}
  return null}
 private fun click(n:AccessibilityNodeInfo):Boolean{var x:AccessibilityNodeInfo?=n;while(x!=null&&!x.isClickable)x=x.parent;return x?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true}
}
