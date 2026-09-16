// One-time conversion of archived pre-native task/project rows. Requires initialized Admin app.
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {db,hash,readModule,writeModule} from '../functions/store.js';
const source=JSON.parse(readFileSync(process.argv[2],'utf8'));
const marker=db().doc('migrations/supabase-native-legacy-20260916');
if((await marker.get()).exists)throw new Error('Legacy conversion already complete');
let converted=0;
for(const row of source.sync||[]){
 if(row.deleted_at||!['tasks','projects'].includes(row.store))continue;
 const old=row.data,id=parseInt(hash(row.store+':'+row.record_id).slice(0,13),16);
 const task=row.store==='tasks',key=task?'Tasks':'Projects';
 const item=task?{id,title:old.title,status:old.status||'not_started',priority:old.priority||'medium',due:old.dueDate||''}:{id,name:old.name,status:old.archived?'ARCHIVED':'ACTIVE'};
 const base=await readModule(row.user_id,key),data=base?JSON.parse(base):(task?[]:{projects:[]});
 const records=task?data:data.projects;
 assert.ok(Array.isArray(records));assert.ok(!records.some(r=>r.id===id),'Converted ID already exists');
 records.push(item);
 await writeModule(row.user_id,key,base,JSON.stringify(data));
 const actual=JSON.parse(await readModule(row.user_id,key));assert.deepEqual((task?actual:actual.projects).find(r=>r.id===id),item);converted++;
}
await marker.set({converted,at:new Date().toISOString()});console.log('Converted and verified '+converted+' legacy records for the native app');
