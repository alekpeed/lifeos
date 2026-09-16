import {initializeApp} from 'firebase-admin/app';
import {getAuth} from 'firebase-admin/auth';
import {getStorage} from 'firebase-admin/storage';
import {onRequest} from 'firebase-functions/v2/https';
import {onSchedule} from 'firebase-functions/v2/scheduler';
import {getMessaging} from 'firebase-admin/messaging';
import {randomUUID,randomBytes,timingSafeEqual} from 'node:crypto';
import {db,hash,readModule,writeModule} from './store.js';
import {itemsFrom,KEYS,buildMessage,subjectOf} from './digest.js';
initializeApp();
const options={region:'us-east1',maxInstances:5,memory:'512MiB',timeoutSeconds:120,invoker:'public'};
const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
const str=(v,max=4000)=>{if(typeof v!=='string'||!v.length||v.length>max)fail(400,'Invalid field');return v;};
const safeId=v=>{str(v,128);if(!/^[A-Za-z0-9_-]+$/.test(v))fail(400,'Invalid id');return v;};
const own=(uid,row)=>{if(row.user_id && row.user_id!==uid)fail(403,'Wrong account');};
const member=async(uid,id)=>{safeId(id);if(!(await db().doc(`spaces/${id}/members/${uid}`).get()).exists)fail(403,'Not a space member');};
const eq=(req,k)=>String(req.query[k]||'').replace(/^eq\./,'');
const bodyRow=req=>{const row=Array.isArray(req.body)?req.body[0]:req.body;if(!row||typeof row!=='object')fail(400,'Invalid body');return row;};
async function authenticated(req){const token=(req.headers.authorization||'').replace(/^Bearer /,'');if(!token)fail(401,'Sign in first');try{return await getAuth().verifyIdToken(token);}catch{fail(401,'Session expired');}}
export const api=onRequest(options,async(req,res)=>{
 try {
  const user=await authenticated(req),uid=user.uid;const path=req.path;
  const methods={'/sync/read':['GET'],'/events':['GET'],'/rest/v1/rpc/create_space':['POST'],'/rest/v1/sharebox_members':['GET','POST'],'/rest/v1/fcm_tokens':['POST'],'/devices/remove':['POST'],'/telegram/configure':['POST'],'/rest/v1/telegram_links':['GET','DELETE'],'/rest/v1/telegram_link_tokens':['POST']};
  if(methods[path]&&!methods[path].includes(req.method))fail(405,'Method not allowed');
  if(req.method==='POST'&&!path.startsWith('/storage/'))bodyRow(req);
  if(path==='/sync' && req.method==='POST') {
   const key=str(req.body.key,200);if(key.startsWith('__')||/[\/\\\x00-\x1f]/.test(key))fail(400,'Reserved or invalid key');
   const {base,text}=req.body;if(![base,text].every(v=>v===null||typeof v==='string'))fail(400,'Invalid sync payload');
   const value=await writeModule(uid,key,base,text);await db().doc(`users/${uid}`).set({active:true},{merge:true});return res.json({key,text:value});
  }
  if(path==='/sync' && req.method==='GET') {const snap=await db().collection(`users/${uid}/modules`).get();return res.json(snap.docs.map(d=>d.data().key));}
  if(path==='/sync/read')return res.json({key:str(req.query.key,200),text:await readModule(uid,str(req.query.key,200))});
  if(path.startsWith('/storage/v1/object/')) {
   const parts=decodeURIComponent(path.slice('/storage/v1/object/'.length)).split('/');const bucket=parts.shift();
   if(bucket==='attachments'){if(parts.shift()!==uid)fail(403,'Wrong account');if(parts.length!==1)fail(400,'Invalid path');safeId(parts[0]);}
   else if(bucket==='sharebox-files'){await member(uid,parts[0]);if(parts.length!==2||parts.some(p=>!p||p==='..'))fail(400,'Invalid path');}
   else fail(404,'Unknown bucket');
   const objectPath=bucket==='attachments'?`users/${uid}/attachments/${parts[0]}`:`spaces/${parts.join('/')}`;
   const file=getStorage().bucket().file(objectPath);
   if(req.method==='GET'){const [exists]=await file.exists();if(!exists)fail(404,'File missing');res.set('Content-Type','application/octet-stream');return file.createReadStream().on('error',()=>res.destroy()).pipe(res);}
   if(req.method==='POST'){if(req.rawBody.length>20*1024*1024)fail(413,'File too large (20 MB maximum)');await file.save(req.rawBody,{resumable:false,metadata:{contentType:'application/octet-stream',cacheControl:'private, no-store'}});return res.json({ok:true});}
   if(req.method==='DELETE'){await file.delete({ignoreNotFound:true});return res.json({ok:true});}
  }
  if(path==='/events') {
   const space=safeId(req.query.space);await member(uid,space);res.set({'Content-Type':'text/event-stream','Cache-Control':'no-cache'});res.flushHeaders();
   const stop=db().doc(`spaces/${space}`).onSnapshot(()=>res.write('data: changed\n\n'),()=>res.end());
   const timer=setTimeout(()=>res.end(),50000);res.on('close',()=>{clearTimeout(timer);stop();});return;
  }
  if(path==='/rest/v1/rpc/create_space') {
   const id=randomUUID(),row={id,name:str(req.body.p_name,100),created_by:uid,created_at:new Date().toISOString()};const batch=db().batch();batch.set(db().doc(`spaces/${id}`),row);batch.set(db().doc(`spaces/${id}/members/${uid}`),{user_id:uid,display_name:str(req.body.p_display_name,100),joined_at:row.created_at});batch.set(db().doc(`users/${uid}/spaces/${id}`),row);await batch.commit();return res.json(row);
  }
  if(path==='/rest/v1/sharebox_spaces' && req.method==='GET'){const rows=await db().collection(`users/${uid}/spaces`).get();return res.json(rows.docs.map(d=>d.data()));}
  if(path==='/rest/v1/sharebox_members') {
   if(req.method==='GET'){const id=safeId(eq(req,'space_id'));await member(uid,id);const s=await db().collection(`spaces/${id}/members`).get();return res.json(s.docs.map(d=>d.data()));}
   const row=bodyRow(req);own(uid,row);const id=safeId(row.space_id);const space=await db().doc(`spaces/${id}`).get();if(!space.exists)fail(404,'Space not found');
   // A random 128-bit space id is the invitation, matching LifeOS's share-code flow.
   const batch=db().batch();batch.set(db().doc(`spaces/${id}/members/${uid}`),{user_id:uid,display_name:str(row.display_name,100),joined_at:new Date().toISOString()});batch.set(db().doc(`users/${uid}/spaces/${id}`),space.data());await batch.commit();return res.json({ok:true});
  }
  if(path==='/rest/v1/sharebox_items') {
   if(req.method==='GET'){const id=safeId(eq(req,'space_id'));await member(uid,id);const rows=await db().collection(`spaces/${id}/items`).orderBy('created_at','desc').get();return res.json(rows.docs.map(d=>d.data()));}
   if(req.method==='POST'){const row=bodyRow(req),id=safeId(row.space_id);await member(uid,id);if(!['note','link','file'].includes(row.kind)||!['normal','soon','urgent'].includes(row.urgency))fail(400,'Invalid item');
    for(const k of ['title','url','body','storage_path'])if(row[k]!=null && (typeof row[k]!=='string'||row[k].length>100000))fail(400,'Invalid item field');
    if(row.storage_path && !row.storage_path.startsWith(id+'/'))fail(403,'Wrong file space');const item={id:randomUUID(),space_id:id,posted_by:uid,kind:row.kind,urgency:row.urgency,title:row.title||null,url:row.url||null,body:row.body||null,storage_path:row.storage_path||null,created_at:new Date().toISOString()};const b=db().batch();b.set(db().doc(`spaces/${id}/items/${item.id}`),item);b.set(db().doc(`itemSpaces/${item.id}`),{space:id});b.update(db().doc(`spaces/${id}`),{revision:randomUUID()});await b.commit();return res.json(item);}
   if(req.method==='DELETE'){const id=safeId(eq(req,'id')),lookup=await db().doc(`itemSpaces/${id}`).get();if(!lookup.exists)return res.json({ok:true});const space=lookup.data().space;await member(uid,space);const b=db().batch();b.delete(db().doc(`spaces/${space}/items/${id}`));b.delete(lookup.ref);b.update(db().doc(`spaces/${space}`),{revision:randomUUID()});await b.commit();return res.json({ok:true});}
  }
  if(path==='/rest/v1/fcm_tokens') {const row=bodyRow(req);own(uid,row);const token=str(row.token,4096);await db().doc(`devices/${hash(token)}`).set({uid,token,updatedAt:Date.now()});return res.json({ok:true});}
  if(path==='/devices/remove'){const token=str(req.body.token,4096),ref=db().doc(`devices/${hash(token)}`);await db().runTransaction(async tx=>{const d=await tx.get(ref);if(d.data()?.uid===uid)tx.delete(ref);});return res.json({ok:true});}
  if(path==='/telegram/configure') {
   const token=str(req.body.token,256);if(!/^[0-9]+:[A-Za-z0-9_-]+$/.test(token))fail(400,'Invalid bot token');
   const me=await fetch(`https://api.telegram.org/bot${token}/getMe`).then(r=>r.json());if(!me.ok)fail(400,'Invalid bot token');
   const secret=randomBytes(32).toString('hex');await db().doc(`telegramBots/${uid}`).set({token,secret});
   const hook=await fetch(`https://api.telegram.org/bot${token}/setWebhook`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({url:`https://us-east1-lifeos-501716.cloudfunctions.net/telegram/${uid}`,secret_token:secret,allowed_updates:['message']})}).then(r=>r.json());
   if(!hook.ok)fail(502,'Webhook setup failed');return res.json({username:me.result.username});
  }
  if(path==='/rest/v1/telegram_links'){const ref=db().doc(`telegramLinks/${uid}`);if(req.method==='DELETE'){await ref.delete();return res.json({ok:true});}const d=await ref.get();return res.json(d.exists?[{user_id:uid,telegram_chat_id:d.data().chatId}]:[]);}
  if(path==='/rest/v1/telegram_link_tokens'){const row=bodyRow(req);own(uid,row);const token=str(row.token,128);if(!/^[a-f0-9]{32}$/.test(token))fail(400,'Invalid token');await db().doc(`telegramTokens/${hash(token)}`).create({uid,expires:Date.now()+600000});return res.json({ok:true});}
  fail(404,'Unknown endpoint');
 }catch(e){if(!e.status)console.error('LifeOS API failure',e.code||e.name);if(!res.headersSent)res.status(e.status||500).json({message:e.status?e.message:'Server error'});else res.end();}
});
async function telegramSend(config,chatId,text){const r=await fetch(`https://api.telegram.org/bot${config.token}/sendMessage`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({chat_id:chatId,text})});if(!r.ok)throw new Error('Telegram send failed');}
async function due(uid){const result=[];for(const key of KEYS){const raw=await readModule(uid,key);if(raw){try{result.push(...itemsFrom(key,JSON.parse(raw)));}catch{}}}return result;}
export const telegram=onRequest(options,async(req,res)=>{
 try{
  const uid=req.path.split('/').filter(Boolean)[0];if(!uid)return res.sendStatus(404);
  const config=(await db().doc(`telegramBots/${uid}`).get()).data();
  const secret=Buffer.from(String(req.headers['x-telegram-bot-api-secret-token']||''));
  if(req.method!=='POST'||!config?.secret||secret.length!==Buffer.byteLength(config.secret)||!timingSafeEqual(secret,Buffer.from(config.secret)))return res.sendStatus(403);
  const update=req.body,msg=update?.message,text=msg?.text?.trim(),chatId=msg?.chat?.id;if(!text||!chatId)return res.send('ok');
  if(text.startsWith('/start ')){const ref=db().doc(`telegramTokens/${hash(text.slice(7).trim())}`);await db().runTransaction(async tx=>{const d=await tx.get(ref);if(d.data()?.uid!==uid||d.data().expires<Date.now())fail(403,'Expired link');tx.set(db().doc(`telegramLinks/${uid}`),{chatId});tx.delete(ref);});await telegramSend(config,chatId,'Connected to LifeOS. Use /task, /due, or send an idea.');return res.send('ok');}
  const link=(await db().doc(`telegramLinks/${uid}`).get()).data();if(link?.chatId!==chatId)return res.sendStatus(403);
  if(text==='/help'){await telegramSend(config,chatId,'/task <text> — add a task\n/due — upcoming items\nOther text — capture an idea');return res.send('ok');}
  if(text==='/due'){const items=await due(uid);await telegramSend(config,chatId,items.length?buildMessage(items):'Nothing due soon.');return res.send('ok');}
  const task=text.startsWith('/task '),value=task?text.slice(6).trim():text;if(!value)return res.send('ok');
  // Deterministic IDs make Telegram retries idempotent in the same merge path as app edits.
  const id=Number(update.update_id)+8000000000000000,key=task?'Tasks':'Ideas';
  const row=task?{id,title:value,status:'not_started',priority:'medium',due:''}:{id,text:value,tags:[],archived:false,created:new Date().toISOString().slice(0,10)};
  await writeModule(uid,key,null,JSON.stringify(task?[row]:{ideas:[row]}));
  await telegramSend(config,chatId,task?'Task captured.':'Idea captured.');res.send('ok');
 }catch(e){res.status(e.status||500).send('Unable to process message');}
});
async function claim(id){try{await db().doc(`sent/${hash(id)}`).create({at:Date.now()});return true;}catch(e){if(e.code===6)return false;throw e;}}
export const scheduledNotifications=onSchedule({schedule:'every 60 minutes',timeZone:'Etc/UTC',region:'us-east1',maxInstances:1},async()=>{
 const day=new Date().toISOString().slice(0,10);let cursor;
 do{let query=db().collection('users').orderBy('__name__').limit(100);if(cursor)query=query.startAfter(cursor);const users=await query.get();if(users.empty)break;
 for(const user of users.docs){const uid=user.id,items=await due(uid);const devices=await db().collection('devices').where('uid','==',uid).get();
 for(const d of devices.docs)for(const item of items.filter(x=>x.when<=0)){const id=`push:${uid}:${d.id}:${subjectOf(item)}:${day}`;if(!await claim(id))continue;try{await getMessaging().send({token:d.data().token,data:{uid,title:item.title,body:`${item.kind} — ${item.when<0?'overdue':'due today'}`,subject:subjectOf(item)},android:{priority:'high'}});}catch(e){if(['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(e.code))await d.ref.delete();else await db().doc(`sent/${hash(id)}`).delete();}}
 if(items.length){const config=(await db().doc(`telegramBots/${uid}`).get()).data(),link=(await db().doc(`telegramLinks/${uid}`).get()).data();const id=`digest:${uid}:${day}`;if(config&&link&&await claim(id)){try{await telegramSend(config,link.chatId,buildMessage(items));}catch{await db().doc(`sent/${hash(id)}`).delete();}}}
 }
 cursor=users.docs.at(-1);if(users.size<100)break;
 }while(true);
});
