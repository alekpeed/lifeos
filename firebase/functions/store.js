import {getFirestore} from 'firebase-admin/firestore';
import {createHash} from 'node:crypto';
import {mergeText,split,join} from './merge.js';
export const hash=s=>createHash('sha256').update(s).digest('hex');
export const db=()=>getFirestore('lifeos');
export const moduleRef=(uid,key)=>db().collection('users').doc(uid).collection('modules').doc(hash(key));
export async function readModule(uid,key) {
 const ref=moduleRef(uid,key); return db().runTransaction(async tx=>{
 const [root,rows]=await Promise.all([tx.get(ref),tx.get(ref.collection('records'))]);
 return join(root.data(),rows.docs.map(d=>d.data()));
 },{readOnly:true});
}
export async function writeModule(uid,key,base,text) {
 const ref=moduleRef(uid,key);
 return db().runTransaction(async tx=>{
  const [root,rows]=await Promise.all([tx.get(ref),tx.get(ref.collection('records'))]);
  const old=join(root.data(),rows.docs.map(d=>d.data()));const merged=mergeText(base,text,old); const parts=split(merged);
  if(Buffer.byteLength(JSON.stringify(parts.root))>800000 || parts.records.some(r=>Buffer.byteLength(r.value)>800000))throw Object.assign(new Error('A record exceeds the sync size limit'),{status:413});
  const next=new Map(parts.records.map(r=>[hash(r.id),r]));
  for(const d of rows.docs)if(!next.has(d.id))tx.delete(d.ref);
  for(const [id,row]of next) {const before=rows.docs.find(d=>d.id===id);if(!before || before.data().value!==row.value)tx.set(ref.collection('records').doc(id),row);}
  tx.set(ref,{...parts.root,key,updatedAt:Date.now()});
  return merged;
 });
}
