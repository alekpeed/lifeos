package com.alekpeed.lifeos.interfaces.nexus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekpeed.lifeos.HomeScreen
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.lifeOsModules
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.loadBase64ImageAsset
import com.alekpeed.lifeos.platform.loadImageAsset
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseSync
import com.alekpeed.lifeos.sync.SyncEngine
import com.alekpeed.lifeos.sync.SyncMeta
import com.alekpeed.lifeos.system.scanCode
import com.alekpeed.lifeos.system.scanWithCamera
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.priorityRank
import com.alekpeed.lifeos.tasks.statusLabel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

const val NEXUS = "nexus"
private const val ART = "nexus-home.png"
private const val CW = 941f
private const val CH = 1672f
private val ART_PARTS = listOf(
    "nexus-command-room.b64",
    "nexus-command-room-1.b64",
    "nexus-command-room-2.b64",
    "nexus-command-room-3.b64",
)
private val MONTHS = listOf("January","February","March","April","May","June","July","August","September","October","November","December")

private data class R(val id:String,val l:Float,val t:Float,val r:Float,val b:Float)
private val HITS = listOf(
    R("domain:Operations",.060f,.128f,.292f,.222f), R("domain:Archive",.060f,.228f,.292f,.318f),
    R("domain:Logistics",.060f,.327f,.292f,.418f), R("domain:Discovery",.060f,.425f,.292f,.515f),
    R("domain:Management",.705f,.128f,.936f,.222f), R("domain:Intelligence",.705f,.228f,.936f,.318f),
    R("domain:People",.705f,.327f,.936f,.418f), R("domain:System",.705f,.425f,.936f,.515f),
    R("bell",.755f,.024f,.815f,.078f), R("core",.205f,.515f,.795f,.645f),
    R("voice",.035f,.865f,.220f,.965f), R("quick_note",.220f,.865f,.405f,.965f),
    R("camera",.405f,.840f,.595f,.985f), R("barcode",.595f,.865f,.780f,.965f), R("ai_assist",.780f,.865f,.965f,.965f),
)

private data class Live(
    val score:String,val scoreCaption:String,val syncMain:String,val sync1:String,val sync2:String,
    val nextWhen:String,val nextTitle:String,val nextDetail:String,val notifications:Int,
)

@Composable
fun NexusHome() {
    val art = remember { loadBase64ImageAsset(ART_PARTS) ?: loadImageAsset(ART) }
    if (art == null) { HomeScreen(remember { lifeOsModules() }) { Nav.open(it.id) }; return }
    val modules = remember { lifeOsModules() }
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf("") }
    DisposableEffect(Unit) { Native.setImmersive(true); onDispose { Native.setImmersive(false) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF03070B))) {
        val vw=constraints.maxWidth.toFloat(); val vh=constraints.maxHeight.toFloat()
        val top=Native.cutoutTopPx().toFloat(); val bottom=Native.navBottomPx().toFloat(); val safe=(vh-top-bottom).coerceAtLeast(1f)
        val s=minOf(vw/CW,safe/CH); val w=CW*s; val h=CH*s; val ox=(vw-w)/2f; val oy=top+(safe-h)/2f
        val d=LocalDensity.current
        Image(art,null,Modifier.offset(with(d){ox.toDp()},with(d){oy.toDp()}).size(with(d){w.toDp()},with(d){h.toDp()}),contentScale=ContentScale.FillBounds)
        Ambient(ox,oy,w,h)
        LiveOverlay(ox,oy,w,h)
        Box(Modifier.fillMaxSize().pointerInput(art,ox,oy,w,h){ detectTapGestures { p ->
            when(val hit=hit(p,ox,oy,w,h)) {
                null,"core" -> Unit
                "bell" -> Nav.open("notifications")
                "voice" -> Nav.open("command")
                "quick_note" -> Nav.open("ideas")
                "camera" -> scanWithCamera(scope)
                "barcode" -> scanCode(scope)
                "ai_assist" -> Nav.open("ai-assistant")
                else -> if(hit.startsWith("domain:")) domain=hit.removePrefix("domain:")
            }
        } })
        if(domain.isNotBlank()) DomainSheet(domain,modules.filter{it.group==domain},{Nav.open(it);domain=""},{domain=""})
    }
}

@Composable
private fun Ambient(ox:Float,oy:Float,w:Float,h:Float){
    var ms by remember{mutableStateOf(0L)}
    LaunchedEffect(Unit){while(true) withFrameNanos{ms=it/1_000_000L}}
    val d=LocalDensity.current; val p=((ms%4800L).toFloat()/4800f)*2f*PI.toFloat(); val breathe=.5f+.5f*sin(p)
    Canvas(Modifier.offset(with(d){ox.toDp()},with(d){oy.toDp()}).size(with(d){w.toDp()},with(d){h.toDp()})){
        val core=Offset(size.width*.5f,size.height*.562f); val r=size.width*(.018f+.005f*breathe)
        drawCircle(Color(0x222DDCFF),r*3.2f,core); drawCircle(Color(0x554AE8FF),r*1.7f,core); drawCircle(Color(0xAA9DF8FF),r*.55f,core)
        drawCircle(Color(0x8848BFFF),r*2f,core.copy(y=core.y+size.height*.002f*sin(p+1.1f)),style=Stroke(size.width*.0015f))
        val lights=listOf(.326f to .250f,.366f to .327f,.421f to .287f,.468f to .390f,.520f to .302f,.566f to .348f,.614f to .268f,.654f to .402f)
        lights.forEachIndexed{i,(x,y)-> val a=.12f+.22f*(.5f+.5f*sin(p+i*.8f)); drawCircle(Color.White.copy(alpha=a),size.width*.0017f,Offset(size.width*x,size.height*y)) }
        drawCircle(Color(0x5545D5FF),size.width*(.028f+.002f*breathe),Offset(size.width*.871f,size.height*.047f),style=Stroke(size.width*.002f))
    }
}

@Composable
private fun LiveOverlay(ox:Float,oy:Float,w:Float,h:Float){
    var time by remember{mutableStateOf("")}; var date by remember{mutableStateOf("")}; var syncing by remember{mutableStateOf(false)}; var live by remember{mutableStateOf(readLive(false))}
    LaunchedEffect(Unit){while(true){val n=Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault());val hr=if(n.hour==0)12 else if(n.hour>12)n.hour-12 else n.hour;time="$hr:${n.minute.toString().padStart(2,'0')} ${if(n.hour<12)"AM" else "PM"}";date="${MONTHS[n.monthNumber-1]} ${n.dayOfMonth}, ${n.year}";delay(1000)}}
    LaunchedEffect(Unit){while(true){live=readLive(syncing);delay(15_000)}}
    LaunchedEffect(Unit){while(true){if(SupabaseAuth.isSignedIn()){syncing=true;live=readLive(true);SupabaseSync.syncNow();syncing=false;live=readLive(false)};delay(300_000)}}
    val d=LocalDensity.current; val panel=Color(0xFF050A10); val card=Color(0xFF071018); val cyan=Color(0xFF8DEBFF); val white=Color(0xFFF4F8FC); val muted=Color(0xFFA8BBC7); val green=Color(0xFF55D6A0)
    fun Modifier.rect(x:Float,y:Float,rw:Float,rh:Float)=offset(with(d){(ox+w*x).toDp()},with(d){(oy+h*y).toDp()}).size(with(d){(w*rw).toDp()},with(d){(h*rh).toDp()})
    Column(Modifier.rect(.395f,.026f,.215f,.050f).background(panel),horizontalAlignment=Alignment.CenterHorizontally){Text(time,color=white,fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=1);Text(date.uppercase(),color=muted,fontSize=8.sp,maxLines=1)}
    Column(Modifier.rect(.075f,.710f,.215f,.110f).background(card),horizontalAlignment=Alignment.CenterHorizontally){Text(live.score,color=white,fontSize=31.sp,fontWeight=FontWeight.Light);Text(live.scoreCaption,color=cyan,fontSize=7.sp,maxLines=1)}
    Column(Modifier.rect(.365f,.704f,.245f,.120f).background(card).padding(6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(live.syncMain,color=cyan,fontSize=10.sp,fontWeight=FontWeight.Medium);Text(live.sync1,color=white,fontSize=8.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(live.sync2,color=if(SupabaseAuth.isSignedIn()&&SyncEngine.pendingCount()==0)green else muted,fontSize=8.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
    Column(Modifier.rect(.690f,.704f,.245f,.122f).background(card).padding(6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(live.nextWhen,color=cyan,fontSize=10.sp,fontWeight=FontWeight.Medium);Text(live.nextTitle,color=white,fontSize=8.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(live.nextDetail,color=muted,fontSize=8.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
    if(live.notifications>0) Box(Modifier.rect(.786f,.026f,.028f,.022f).background(Color(0xFFD94D74),RoundedCornerShape(50)),contentAlignment=Alignment.Center){Text(live.notifications.coerceAtMost(99).toString(),color=Color.White,fontSize=7.sp,fontWeight=FontWeight.Bold)}
}

private fun readLive(syncing:Boolean):Live{
    val now=Clock.System.now().toEpochMilliseconds(); val day=today(); val tasks=loadTasks(); val habits=loadHabits(); val due=tasks.filter{it.dueDate()==day}; val total=due.size+habits.size; val done=due.count{it.done}+habits.count{it.checkedInToday}
    val score=if(total==0)"—" else ((done*100f)/total).roundToInt().toString(); val caption=if(total==0)"NO ITEMS TODAY" else "$done / $total COMPLETE"
    val signed=SupabaseAuth.isSignedIn(); val pending=if(signed)SyncEngine.pendingCount() else 0; val last=SyncMeta.lastSyncAt
    val sm=when{syncing->"SYNCING";!signed->"LOCAL ONLY";pending==0->"SYNCED";else->"$pending PENDING"}; val s1=when{!signed->"Cloud sync is off";last<=0L->"Not synced yet";else->relative(now-last)}; val s2=when{!signed->"Sign in from Settings";pending==0->"All systems up to date";else->"Changes waiting to sync"}
    val n=nextTask(tasks,day); val nw=when(val x=n?.dueDate()){null->if(n==null)"CLEAR" else "ANYTIME";day->"TODAY";else->x.toString()}; return Live(score,caption,sm,s1,s2,nw,n?.title?:"Nothing queued",n?.project?.ifBlank{statusLabel(n.status)}?:"Tasks are clear",due.count{!it.done}+pending)
}
private fun nextTask(tasks:List<Task>,day:kotlinx.datetime.LocalDate)=tasks.asSequence().filter{!it.done}.filter{it.snoozeDate()?.let{s->s<=day}?:true}.sortedWith(compareBy<Task>({it.dueDate()==null},{it.dueDate()?.toString().orEmpty()},{priorityRank(it.priority)})).firstOrNull()
private fun relative(a:Long)=when{a<60_000->"Last synced just now";a<3_600_000->"Last synced ${a/60_000}m ago";a<86_400_000->"Last synced ${a/3_600_000}h ago";else->"Last synced ${a/86_400_000}d ago"}
private fun hit(p:Offset,ox:Float,oy:Float,w:Float,h:Float):String?{val x=(p.x-ox)/w;val y=(p.y-oy)/h;if(x !in 0f..1f||y !in 0f..1f)return null;return HITS.firstOrNull{x in it.l..it.r&&y in it.t..it.b}?.id}

@Composable
private fun DomainSheet(domain:String,modules:List<com.alekpeed.lifeos.Module>,onPick:(String)->Unit,onDismiss:()->Unit){
    Box(Modifier.fillMaxSize().background(Color(0xE6050A10)).clickable(onClick=onDismiss),contentAlignment=Alignment.Center){Column(Modifier.fillMaxWidth().padding(horizontal=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(domain.uppercase(),color=Color(0xFF8DEBFF),fontSize=13.sp,fontWeight=FontWeight.SemiBold,letterSpacing=3.sp);modules.forEach{m->Box(Modifier.fillMaxWidth().background(Color(0x14FFFFFF),RoundedCornerShape(12.dp)).clickable{onPick(m.id)}.padding(horizontal=16.dp,vertical=15.dp)){Text("${m.icon}   ${m.label}",color=Color(0xFFEDEFF2),fontSize=16.sp)}}}}
}
fun registerNexus(){com.alekpeed.lifeos.interfaces.Interfaces.registerHome(NEXUS){NexusHome()}}
