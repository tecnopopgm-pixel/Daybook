package app.daybook
import android.app.*
import android.content.*
import android.os.Build
import org.json.JSONObject
object Scheduler{
 fun schedule(c:Context,j:JSONObject){
  val am=c.getSystemService(AlarmManager::class.java);val id=j.getLong("id")
  val pi=PendingIntent.getBroadcast(c,id.toInt(),Intent(c,AlarmReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,j.getLong("at"),pi)
  else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,j.getLong("at"),pi)}
}
object Notify{
 fun show(c:Context,t:String,x:String){
  val nm=c.getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("main","Daybook",NotificationManager.IMPORTANCE_HIGH))
  nm.notify(System.currentTimeMillis().toInt(),Notification.Builder(c,"main").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(t).setContentText(x).build())}
}
class AlarmReceiver:BroadcastReceiver(){
 override fun onReceive(c:Context,i:Intent){
  val j=Store.takeJob(c,i.getLongExtra("id",0))?:return
  if(j.getString("kind")=="remind")Notify.show(c,"Reminder",j.getString("text"))
  else{val r=Sender.send(c,j.getString("kind"),j.getString("to"),j.getString("text"));Store.log(c,"scheduled send: $r")}}
}
class BootReceiver:BroadcastReceiver(){
 override fun onReceive(c:Context,i:Intent){
  val a=Store.jobs(c);for(k in 0 until a.length())Scheduler.schedule(c,a.getJSONObject(k))
  c.startForegroundService(Intent(c,AssistantService::class.java))}
}
class AssistantService:android.app.Service(){
 override fun onBind(i:Intent?)=null
 override fun onStartCommand(i:Intent?,f:Int,s:Int):Int{
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("svc","Assistant running",NotificationManager.IMPORTANCE_LOW))
  val n=Notification.Builder(this,"svc").setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle("Daybook is running").setContentText("Reminders and scheduled messages are active").build()
  if(Build.VERSION.SDK_INT>=34)startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1,n)
  return START_STICKY}
}
