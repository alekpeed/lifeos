// Run once with GOOGLE_APPLICATION_CREDENTIALS and an export JSON path.
// Never commit exports: they contain personal records and password hashes.
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../functions/package.json',import.meta.url));
const {initializeApp,getApps}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
import {db,hash,writeModule,readModule} from '../functions/store.js';
if(!getApps().length)initializeApp({projectId:'lifeos-501716'});
const data=JSON.parse(readFileSync(process.argv[2],'utf8'));
const marker=db().doc('migrations/supabase-20260916');
if((await marker.get()).data()?.complete)throw new Error('Migration already complete; refusing to overwrite migrated data');
const users=(data.users||[]).map(u=>({uid:u.uid,...(u.email?{email:u.email}:{}),emailVerified:!!u.emailVerified,disabled:!!u.disabled,...(u.passwordHash?{passwordHash:Buffer.from(u.passwordHash)}:{})}));
// Do not overwrite an unrelated Firebase account that happens to share an email.
for(const user of users){if(!user.email)continue;try{const existing=await getAuth().getUserByEmail(user.email);assert.equal(existing.uid,user.uid,'Email already belongs to another Firebase UID');}catch(e){if(e.code!=='auth/user-not-found')throw e;}}
const result=await getAuth().importUsers(users,{hash:{algorithm:'BCRYPT'}});
assert.equal(result.failureCount,0,JSON.stringify(result.errors.map(e=>({index:e.index,code:e.error.code}))));
for(const u of users)await db().doc(`users/${u.uid}`).set({active:true},{merge:true});
let modules=0,legacy=0;
for(const row of data.sync||[]){
 if(row.store==='kv'){
  const text=row.deleted_at?null:row.data.text;
  if(typeof text!=='string'&&text!==null)throw new Error('Unexpected module shape');
  await writeModule(row.user_id,row.record_id,null,text);
  const actual=await readModule(row.user_id,row.record_id);
  const parse=s=>{try{return JSON.parse(s);}catch{return s;}};
  assert.deepEqual(parse(actual),parse(text));modules++;
 }else{
  // Retain pre-native web records losslessly; their UUID schema is not the native Long-ID schema.
  await db().doc(`users/${row.user_id}/legacyRecords/${hash(row.store+':'+row.record_id)}`).set(row);legacy++;
 }
}
for(const row of data.profiles||[])await db().doc(`users/${row.id}`).set({profile:row},{merge:true});
for(const row of data.spaces||[])await db().doc(`spaces/${row.id}`).set(row);
for(const row of data.members||[]){await db().doc(`spaces/${row.space_id}/members/${row.user_id}`).set(row);const space=data.spaces.find(s=>s.id===row.space_id);assert.ok(space);await db().doc(`users/${row.user_id}/spaces/${row.space_id}`).set(space);}
for(const row of data.items||[]){await db().doc(`spaces/${row.space_id}/items/${row.id}`).set(row);await db().doc(`itemSpaces/${row.id}`).set({space:row.space_id});}
for(const row of data.telegram||[])await db().doc(`telegramLinks/${row.user_id}`).set({chatId:row.telegram_chat_id});
// Old project FCM tokens are intentionally not reused: updated Android registers fresh tokens.
const report={complete:true,at:new Date().toISOString(),users:users.length,modules,legacyRecords:legacy,spaces:(data.spaces||[]).length,members:(data.members||[]).length,items:(data.items||[]).length};
for(const u of users)assert.equal((await getAuth().getUser(u.uid)).uid,u.uid);
await marker.set(report);console.log(JSON.stringify(report));
