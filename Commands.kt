package app.daybook
import android.content.*
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import org.json.JSONObject
import java.text.DateFormat
import java.util.*
object Sender{
 const val DAILY_CAP=20
 fun send(c:Context,app:String,name:String,text:String):String{
  if(!Store.armed(c))return "Automatic sending is switched off in the app."
  val svc=SendAccessibilityService.instance?:return "Turn on Daybook automation in Accessibility settings."
  if(Store.sendsToday(c)>=DAILY_CAP)return "Daily limit of $DAILY_CAP automatic messages reached."
  val ct=Store.contacts(c).optJSONObject(name.lowercase())?:return "No saved contact named $name."
  val i=if(app=="wa"){
   val p=ct.optString("wa").filter{it.isDigit()};if(p.isEmpty())return "$name has no WhatsApp number."
   Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://wa.me/$p?text=${android.net.Uri.encode(text)}")).setPackage("com.whatsapp")
  }else{
   val u=ct.optString("ig").removePrefix("@");if(u.isEmpty())return "$name has no Instagram username."
   Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://ig.me/m/$u")).setPackage("com.instagram.android")}
  SendAccessibilityService.pending=SendAccessibilityService.Pending(app,text,System.currentTimeMillis()+25000)
  Store.countSend(c);Store.log(c,"auto-send ${if(app=="wa")"WhatsApp" else "Instagram"} to $name: $text")
  svc.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  return "Sending to $name…"}
}
object Commands{
 private val I=RegexOption.IGNORE_CASE
 private val TIME=Regex("""(\bat \d{1,2}(:\d{2})?\s*(am|pm)?|\b\d{1,2}(:\d{2})?\s*(am|pm)\b|\bin (\d+|an?|one) ?(minutes?|mins?|hours?|hrs?)\b|\btomorrow\b)""",I)
 fun fmt(t:Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(t))
 fun strip(s:String)=TIME.replace(s,"").replace(Regex("\\s+")," ").trim()
 fun parseTime(s:String):Long?{
  val l=s.lowercase();val c=Calendar.getInstance();val tom="tomorrow" in l
  Regex("""in (\d+|an?|one) ?(minute|min|hour|hr)""").find(l)?.let{c.add(if(it.groupValues[2][0]=='h')Calendar.HOUR_OF_DAY else Calendar.MINUTE,it.groupValues[1].toIntOrNull()?:1);return c.timeInMillis}
  val m=Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""").find(l)?:Regex("""at (\d{1,2})(?::(\d{2}))?()""").find(l)
  if(m==null){if(!tom)return null;c.add(Calendar.DAY_OF_YEAR,1);c.set(Calendar.HOUR_OF_DAY,9);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);return c.timeInMillis}
  var h=m.groupValues[1].toInt();val ap=m.groupValues[3]
  if(ap=="pm"&&h<12)h+=12;if(ap=="am"&&h==12)h=0
  c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,m.groupValues[2].toIntOrNull()?:0);c.set(Calendar.SECOND,0)
  if(tom)c.add(Calendar.DAY_OF_YEAR,1) else if(c.timeInMillis<System.currentTimeMillis())c.add(Calendar.DAY_OF_YEAR,1)
  return c.timeInMillis}
 private fun job(c:Context,kind:String,to:String,text:String,at:Long){
  val j=JSONObject().put("id",System.currentTimeMillis()).put("kind",kind).put("to",to).put("text",text).put("at",at)
  Store.addJob(c,j);Scheduler.schedule(c,j)}
 fun run(c:Context,raw:String):String{
  val s=raw.trim()
  if(s.startsWith("save contact",true)){
   val m=Regex("save contact (\\w+)(?:.*?whatsapp ([+\\d]+))?(?:.*?instagram @?([\\w.]+))?",I).find(s)!!
   Store.saveContact(c,m.groupValues[1],m.groupValues[2],m.groupValues[3]);return "Saved ${m.groupValues[1]}."}
  val a=Regex("^(?:send |message |text )?(whatsapp|instagram) (\\w+) (?:saying|say|that|:) (.+)$",I).find(s)
  val b=Regex("^(?:send |message |text )(\\w+) (?:on|via) (whatsapp|instagram) (?:saying|say|that|:) (.+)$",I).find(s)
  val t3=a?.let{Triple(it.groupValues[1],it.groupValues[2],it.groupValues[3])}?:b?.let{Triple(it.groupValues[2],it.groupValues[1],it.groupValues[3])}
  if(t3!=null){
   val k=if(t3.first.lowercase()=="whatsapp")"wa" else "ig";val at=parseTime(t3.third)
   if(at==null)return Sender.send(c,k,t3.second,t3.third)
   job(c,k,t3.second,strip(t3.third),at);return "Scheduled for ${fmt(at)}: message to ${t3.second}"}
  Regex("^remind me (?:to |about )?(.+)$",I).find(s)?.let{
   val at=parseTime(it.groupValues[1])?:return "When? Try “at 5 pm” or “in 20 minutes”."
   job(c,"remind","",strip(it.groupValues[1]),at);return "Reminder set for ${fmt(at)}."}
  Regex("^(?:add|schedule|create|book)(?: an?| the)?(?: event| meeting| appointment)?(?: called| for| with)? (.+)$",I).find(s)?.let{
   val at=parseTime(it.groupValues[1])?:return "What time? Try “tomorrow at 3 pm”."
   return addEvent(c,strip(it.groupValues[1]),at)}
  if(Regex("my day|agenda|what.s next|today",I).containsMatchIn(s))return agenda(c)
  return "Try: remind me to… / schedule… / whatsapp Sam saying… / save contact Sam whatsapp +44… instagram sam_x"}
 private fun addEvent(c:Context,title:String,at:Long):String{
  if(c.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR)!=PackageManager.PERMISSION_GRANTED)return "Grant calendar permission first."
  val id=c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,arrayOf(CalendarContract.Calendars._ID),"${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=500",null,null)?.use{if(it.moveToFirst())it.getLong(0) else null}?:return "No writable calendar found."
  val v=ContentValues().apply{put(Events.CALENDAR_ID,id);put(Events.TITLE,title);put(Events.DTSTART,at);put(Events.DTEND,at+3600000);put(Events.EVENT_TIMEZONE,TimeZone.getDefault().id)}
  c.contentResolver.insert(Events.CONTENT_URI,v);return "Added to your calendar: $title, ${fmt(at)}"}
 private fun agenda(c:Context):String{
  if(c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)return "Grant calendar permission first."
  val st=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0)}.timeInMillis
  val u=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(u,st);ContentUris.appendId(u,st+86400000)
  val out=StringBuilder()
  c.contentResolver.query(u.build(),arrayOf(CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN),null,null,"${CalendarContract.Instances.BEGIN} ASC")?.use{while(it.moveToNext())out.append("• ${it.getString(0)} – ${fmt(it.getLong(1))}\n")}
  val a=Store.jobs(c);for(i in 0 until a.length()){val j=a.getJSONObject(i);out.append("• ${if(j.getString("kind")=="remind")"Reminder: " else "Message to ${j.getString("to")}: "}${j.getString("text")} – ${fmt(j.getLong("at"))}\n")}
  return if(out.isEmpty())"Nothing scheduled today." else out.toString().trim()}
}
