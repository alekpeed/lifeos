// Three-way merge: changes relative to base win, untouched remote fields survive.
// Arrays of records merge by stable id, including deletions and independent additions.
const same = (a,b) => JSON.stringify(a) === JSON.stringify(b);
const obj = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const records = a => Array.isArray(a) && a.every(v => obj(v) && ['number','string'].includes(typeof v.id)) && new Set(a.map(v=>String(v.id))).size===a.length;
export function merge(base, local, remote) {
  if(same(local,base)) return remote;
  if(same(remote,base) || same(local,remote)) return local;
  if(obj(local) && obj(remote) && (base == null || obj(base))) {
    const out=Object.create(null); for(const k of new Set([...Object.keys(base||{}),...Object.keys(local),...Object.keys(remote)])) {
      const v=merge(base?.[k],local[k],remote[k]); if(v!==undefined) out[k]=v;
    } return out;
  }
  if(records(local) && records(remote) && (base == null || records(base))) {
    const map=a=>new Map((a||[]).map(v=>[String(v.id),v])); const b=map(base),l=map(local),r=map(remote);
    return [...new Set([...r.keys(),...l.keys(),...b.keys()])].map(k=>merge(b.get(k),l.get(k),r.get(k))).filter(v=>v!==undefined);
  }
  return local;
}
export function parse(text) { if(text===null || text===undefined)return undefined; try{return JSON.parse(text);}catch{return text;} }
export function mergeText(base,local,remote) {
  const result=merge(parse(base),parse(local),parse(remote));
  return result===undefined?null:typeof result==='string'?result:JSON.stringify(result);
}
// Each record is its own Firestore document. Root metadata describes array layout.
export function split(text) {
  if(text===null)return {root:{kind:'deleted'},records:[]};
  const data=parse(text), rows=[];
  const array=(a,path)=>a.map((v,i)=>{const id=`${path}:${String(v.id)}`;rows.push({id,value:JSON.stringify(v)});return id;});
  if(records(data))return {root:{kind:'array',order:array(data,'root')},records:rows};
  if(obj(data)) {const fields=Object.create(null),arrays=Object.create(null);for(const [k,v] of Object.entries(data)) {if(records(v))arrays[k]=array(v,k);else fields[k]=v;}return {root:{kind:'object',fields:JSON.stringify(fields),arrays},records:rows};}
  return {root:{kind:'raw',text},records:[]};
}
export function join(root,rows) {
  if(!root || root.kind==='deleted')return null;
  const lookup=new Map(rows.map(r=>[r.id,JSON.parse(r.value)]));
  const arr=ids=>ids.map(id=>lookup.get(id)).filter(v=>v!==undefined);
  if(root.kind==='raw')return root.text;
  if(root.kind==='array')return JSON.stringify(arr(root.order));
  const value=JSON.parse(root.fields);for(const [k,ids] of Object.entries(root.arrays||{}))Object.defineProperty(value,k,{value:arr(ids),enumerable:true,writable:true,configurable:true});return JSON.stringify(value);
}
