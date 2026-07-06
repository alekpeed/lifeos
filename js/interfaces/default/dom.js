// Tiny DOM-building helper shared by Meridian's view modules. Not a
// framework — just enough to avoid innerHTML string-building for views
// that stay reactive to data-layer events.

export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'text') node.textContent = v;
    else if (k === 'html') node.innerHTML = v;
    else if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (k === 'checked' || k === 'disabled' || k === 'selected') node[k] = v;
    else node.setAttribute(k, v);
  }
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child);
  }
  return node;
}

export function fmtDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr + (dateStr.length <= 10 ? 'T00:00:00' : ''));
  if (Number.isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export function todayStr() {
  return new Date().toISOString().slice(0, 10);
}

export function isPast(dateStr) {
  if (!dateStr) return false;
  return dateStr < todayStr();
}

export function parseTags(input) {
  return input
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);
}

export function hostnameOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

// Shared by any module with a "repeats" field (Tasks, Bills, ...): a
// recurring record is { freq: 'daily'|'weekly'|'monthly'|'yearly', interval }.
export const RECUR_FREQS = [
  { value: '', label: 'Does not repeat' },
  { value: 'daily', label: 'Daily' },
  { value: 'weekly', label: 'Weekly' },
  { value: 'monthly', label: 'Monthly' },
  { value: 'yearly', label: 'Yearly' },
];

export function computeNextDueDate(dueDateStr, recurring) {
  if (!dueDateStr || !recurring?.freq) return null;
  const d = new Date(dueDateStr + 'T00:00:00');
  const n = recurring.interval || 1;
  switch (recurring.freq) {
    case 'daily': d.setDate(d.getDate() + n); break;
    case 'weekly': d.setDate(d.getDate() + 7 * n); break;
    case 'monthly': d.setMonth(d.getMonth() + n); break;
    case 'yearly': d.setFullYear(d.getFullYear() + n); break;
    default: return null;
  }
  return d.toISOString().slice(0, 10);
}
