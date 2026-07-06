import { el, fmtDate, todayStr, isPast, RECUR_FREQS, computeNextDueDate } from '../dom.js';

let state = {
  tab: 'bills', // bills | spend
  categoryFilter: 'all',
  showPaid: false,
  selectedBillId: null,
};

async function markPaid(ctx, bill, paid) {
  if (paid) {
    await ctx.data.BillPayments.create({
      billId: bill.id,
      datePaid: todayStr(),
      amountPaid: bill.amount ?? 0,
      method: bill.autopay ? 'autopay' : '',
    });
    if (bill.recurring?.freq) {
      const nextDue = computeNextDueDate(bill.dueDate, bill.recurring);
      await ctx.data.Bills.update(bill.id, { paid: false, dueDate: nextDue || bill.dueDate });
      return;
    }
  }
  await ctx.data.Bills.update(bill.id, { paid });
}

function billMeta(bill) {
  const meta = el('div', { class: 'mer-task-meta' });
  if (bill.category) meta.append(el('span', { class: 'mer-chip', text: bill.category }));
  if (typeof bill.amount === 'number') meta.append(el('span', { class: 'mer-chip', text: `$${bill.amount.toFixed(2)}` }));
  if (bill.dueDate) {
    meta.append(el('span', {
      class: isPast(bill.dueDate) && !bill.paid ? 'mer-chip is-overdue' : 'mer-chip',
      text: fmtDate(bill.dueDate),
    }));
  }
  if (bill.autopay) meta.append(el('span', { class: 'mer-chip', text: 'Autopay' }));
  if (bill.recurring?.freq) meta.append(el('span', { class: 'mer-chip', text: RECUR_FREQS.find((f) => f.value === bill.recurring.freq)?.label || 'Repeats' }));
  return meta;
}

function billRow(bill, ctx, onSelect) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox', checked: !!bill.paid,
      onclick: (e) => { e.stopPropagation(); markPaid(ctx, bill, e.target.checked); },
    }),
    el('span', { class: bill.paid ? 'mer-task-title is-done' : 'mer-task-title', text: bill.name || '(untitled bill)' }),
    billMeta(bill),
  ]);
  row.addEventListener('click', () => onSelect(bill.id));
  return row;
}

// --- Payment history ---

function paymentHistory(bill, payments, ctx, rerender) {
  const list = el('div', { class: 'mer-people-list' });
  for (const p of payments.sort((a, b) => (a.datePaid < b.datePaid ? 1 : -1))) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: `$${Number(p.amountPaid).toFixed(2)}` }),
        el('div', { class: 'mer-person-meta', text: [fmtDate(p.datePaid), p.method].filter(Boolean).join(' · ') }),
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => { await ctx.data.BillPayments.remove(p.id); rerender(); },
      }),
    ]));
  }

  const dateInput = el('input', { type: 'date', value: todayStr() });
  const amountInput = el('input', { type: 'number', step: '0.01', placeholder: 'Amount', value: bill.amount ?? '' });
  const methodInput = el('input', { type: 'text', placeholder: 'Method (check, card…)' });
  const addBtn = el('button', {
    type: 'button', text: 'Log payment',
    onclick: async () => {
      if (!amountInput.value) return;
      await ctx.data.BillPayments.create({
        billId: bill.id, datePaid: dateInput.value || todayStr(),
        amountPaid: Number(amountInput.value), method: methodInput.value || '',
      });
      rerender();
    },
  });

  return el('div', {}, [list, el('div', { class: 'mer-person-form' }, [dateInput, amountInput, methodInput, addBtn])]);
}

// --- Detail editor ---

function detailEditor(bill, ctx, rerender) {
  const patch = (fields) => ctx.data.Bills.update(bill.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: bill.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const categoryInput = el('input', { type: 'text', value: bill.category || '', placeholder: 'utilities, insurance, rent…', onchange: (e) => patch({ category: e.target.value }) });
  const amountInput = el('input', { type: 'number', step: '0.01', value: bill.amount ?? '', onchange: (e) => patch({ amount: e.target.value ? Number(e.target.value) : null }) });
  const dueInput = el('input', { type: 'date', value: bill.dueDate || '', onchange: (e) => patch({ dueDate: e.target.value || null }) });
  const recurSelect = el('select', {
    onchange: (e) => patch({ recurring: e.target.value ? { freq: e.target.value, interval: bill.recurring?.interval || 1 } : null }),
  }, RECUR_FREQS.map((f) => el('option', { value: f.value, text: f.label, selected: f.value === (bill.recurring?.freq || '') })));
  const autopayCheckbox = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: !!bill.autopay, onchange: (e) => patch({ autopay: e.target.checked }) }),
    el('span', { text: 'Autopay' }),
  ]);

  // Kept as direct references (not child-index lookups) so each async
  // section can be swapped in independently of the other's resolution order.
  const attachmentPlaceholder = el('p', { class: 'mer-muted', text: 'Loading attachment…' });
  const historyPlaceholder = el('p', { class: 'mer-muted', text: 'Loading payment history…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: bill.name || '(untitled bill)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedBillId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Category', categoryInput),
      field('Amount', amountInput),
      field('Due date', dueInput),
      field('Repeats', recurSelect),
    ]),
    autopayCheckbox,
    el('div', { class: 'mer-subsection-label', text: 'PDF attachment' }),
    attachmentPlaceholder,
    el('div', { class: 'mer-subsection-label', text: 'Payment history' }),
    historyPlaceholder,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete bill',
      onclick: async () => { await ctx.data.Bills.remove(bill.id); state.selectedBillId = null; rerender(); },
    }),
  ]);

  ctx.data.getAttachmentsFor('bills', bill.id).then((attachments) => {
    const wrap = el('div', {}, [
      ...attachments.map((a) => el('div', { class: 'mer-person-card' }, [
        el('a', { href: ctx.data.attachmentUrl(a), target: '_blank', rel: 'noopener', text: a.filename }),
        el('button', { type: 'button', class: 'mer-icon-btn', text: '×', onclick: async () => { await ctx.data.Attachments.remove(a.id); rerender(); } }),
      ])),
      el('input', {
        type: 'file', accept: 'application/pdf,image/*',
        onchange: async (e) => {
          if (e.target.files[0]) await ctx.data.createAttachment(e.target.files[0], 'bills', bill.id);
          rerender();
        },
      }),
    ]);
    attachmentPlaceholder.replaceWith(wrap);
  });

  ctx.data.BillPayments.byIndex('billId', bill.id).then((payments) => {
    historyPlaceholder.replaceWith(paymentHistory(bill, payments, ctx, rerender));
  });

  return detail;
}

// --- Yearly spend by category ---

async function renderSpendSummary(container, ctx) {
  const [payments, bills] = await Promise.all([ctx.data.BillPayments.list(), ctx.data.Bills.list()]);
  const categoryById = new Map(bills.map((b) => [b.id, b.category || 'Uncategorized']));
  const year = new Date().getFullYear().toString();

  const totals = new Map();
  for (const payment of payments) {
    if (!payment.datePaid?.startsWith(year)) continue;
    const category = categoryById.get(payment.billId) || 'Uncategorized';
    totals.set(category, (totals.get(category) || 0) + Number(payment.amountPaid || 0));
  }

  if (!totals.size) {
    container.append(el('p', { class: 'mer-muted', text: `No payments logged for ${year} yet.` }));
    return;
  }

  const sorted = [...totals.entries()].sort((a, b) => b[1] - a[1]);
  const grandTotal = sorted.reduce((sum, [, v]) => sum + v, 0);
  const list = el('div', { class: 'mer-people-list' });
  for (const [category, total] of sorted) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: category })]),
      el('div', { text: `$${total.toFixed(2)}` }),
    ]));
  }
  container.append(el('h3', { text: `${year} spend: $${grandTotal.toFixed(2)}` }), list);
}

// --- Toolbar ---

function toolbar(ctx, bills, rerender) {
  const categories = [...new Set(bills.map((b) => b.category).filter(Boolean))];

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New bill — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Bills.create({ name: e.target.value.trim(), paid: false });
      e.target.value = '';
    },
  });

  const categorySelect = el('select', { onchange: (e) => { state.categoryFilter = e.target.value; rerender(); } }, [
    el('option', { value: 'all', text: 'All categories', selected: state.categoryFilter === 'all' }),
    ...categories.map((c) => el('option', { value: c, text: c, selected: c === state.categoryFilter })),
  ]);

  const paidToggle = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: state.showPaid, onchange: (e) => { state.showPaid = e.target.checked; rerender(); } }),
    el('span', { text: 'Show paid' }),
  ]);

  const tabsBar = el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'bills' ? 'is-active' : '', text: 'Bills', onclick: () => { state.tab = 'bills'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'spend' ? 'is-active' : '', text: 'Yearly Spend', onclick: () => { state.tab = 'spend'; rerender(); } }),
  ]);

  const row = el('div', { class: 'mer-toolbar' }, [tabsBar]);
  if (state.tab === 'bills') row.append(quickAdd, categorySelect, paidToggle);
  return row;
}

export async function renderBills(canvas, ctx, rerender) {
  const bills = await ctx.data.Bills.list();
  canvas.append(el('h1', { text: 'Bills' }));
  canvas.append(toolbar(ctx, bills, rerender));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  const onSelect = (id) => { state.selectedBillId = state.selectedBillId === id ? null : id; rerender(); };

  if (state.tab === 'spend') {
    await renderSpendSummary(area, ctx);
  } else {
    const filtered = bills
      .filter((b) => state.categoryFilter === 'all' || b.category === state.categoryFilter)
      .filter((b) => state.showPaid || !b.paid)
      .sort((a, b) => (a.dueDate || '9999') < (b.dueDate || '9999') ? -1 : 1);

    if (!filtered.length) {
      area.append(el('p', { class: 'mer-muted', text: 'No bills match the current filters.' }));
    } else {
      for (const bill of filtered) area.append(billRow(bill, ctx, onSelect));
    }

    if (state.selectedBillId) {
      const bill = bills.find((b) => b.id === state.selectedBillId);
      if (bill) canvas.append(detailEditor(bill, ctx, rerender));
    }
  }
}
