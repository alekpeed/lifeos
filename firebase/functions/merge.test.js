import test from 'node:test';
import assert from 'node:assert/strict';
import {merge,mergeText,split,join,parse} from './merge.js';
test('independent edits survive across devices',()=>assert.deepEqual(merge({a:1,b:1},{a:2,b:1},{a:1,b:2}),Object.assign(Object.create(null),{a:2,b:2})));
test('record additions and deletion merge by id',()=>assert.deepEqual(merge([{id:1},{id:2}],[{id:2},{id:3}],[{id:1},{id:2},{id:4}]),[{id:2},{id:4},{id:3}]));
test('unchanged stale device cannot resurrect deleted records',()=>assert.equal(mergeText('[{"id":1}]','[{"id":1}]','[]'),'[]'));
test('concurrent fields on same record survive',()=>assert.equal(mergeText('[{"id":1,"a":1,"b":1}]','[{"id":1,"a":2,"b":1}]','[{"id":1,"a":1,"b":2}]'),'[{"id":1,"a":2,"b":2}]'));
test('missing local baseline pulls remote data',()=>assert.equal(mergeText(null,null,'[{"id":1}]'),'[{"id":1}]'));
test('record storage round trips supported module shapes',()=>{for(const value of [null,'hello','[]','[{"id":1,"title":"x"}]','{"tasks":[{"id":1}],"settings":{"a":true},"empty":[]}','{"__proto__":{"polluted":true}}']){const s=split(value);assert.deepEqual(parse(join(s.root,s.records)),parse(value));}});
test('prototype-shaped keys never change object prototypes',()=>{const r=merge({},JSON.parse('{"__proto__":{"polluted":true}}'),{other:true});assert.equal({}.polluted,undefined);assert.equal(JSON.parse(JSON.stringify(r)).__proto__.polluted,true);});
test('whole module removal is preserved',()=>assert.equal(mergeText('{"a":1}',null,'{"a":1}'),null));
test('53-bit native IDs survive JSON, merge and Firestore record splitting',()=>{
 const a=9007199254740990,b=9007199254740991;
 assert.ok(Number.isSafeInteger(a)&&Number.isSafeInteger(b));
 const merged=mergeText('[{"id":1}]',JSON.stringify([{id:1},{id:a,title:'Phone'}]),JSON.stringify([{id:1},{id:b,title:'Desktop'}]));
 const s=split(merged);const rows=JSON.parse(join(s.root,s.records));
 assert.deepEqual(new Set(rows.map(r=>r.id)),new Set([1,a,b]));
});
