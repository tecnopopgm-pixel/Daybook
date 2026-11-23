package app.daybook
import android.content.Context
import org.json.*
object Store{
 private fun sp(c:Context)=c.getSharedPreferences("db",Context.MODE_PRIVATE)
 fun contacts(c:Context)=JSONObject(sp(c).getString("contacts","{}")!!)
 fun saveContact(c:Context,n:String,wa:String,ig:String){val o=contacts(c);o.put(n.lowercase(),JSONObject().put("wa",wa).put("ig",ig));sp(c).edit().putString("contacts",o.toString()).apply()}
 fun jobs(c:Context)=JSONArray(sp(c).getString("jobs","[]")!!)
 private fun putJobs(c:Context,a:JSONArray)=sp(c).edit().putString("jobs",a.toString()).apply()
 fun addJob(c:Context,j:JSONObject)=putJobs(c,jobs(c).put(j))
 fun takeJob(c:Context,id:Long):JSONObject?{val a=jobs(c);val out=JSONArray();var f:JSONObject?=null
  for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.getLong("id")==id)f=o else out.put(o)};putJobs(c,out);return f}
 fun armed(c:Context)=sp(c).getBoolean("armed",false)
 fun setArmed(c:Context,b:Boolean)=sp(c).edit().putBoolean("armed",b).apply()
 fun log(c:Context,line:String){val l=(sp(c).getString("log","")!!.lines().filter{it.isNotBlank()}+line).takeLast(150);sp(c).edit().putString("log",l.joinToString("\n")).apply()}
 fun logText(c:Context)=sp(c).getString("log","")!!.lines().takeLast(25).reversed().joinToString("\n")
 fun sendsToday(c:Context):Int=if(sp(c).getString("sd","")==java.time.LocalDate.now().toString())sp(c).getInt("sn",0) else 0
 fun countSend(c:Context){sp(c).edit().putString("sd",java.time.LocalDate.now().toString()).putInt("sn",sendsToday(c)+1).apply()}
}
