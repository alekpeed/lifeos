import { el, fmtDate, todayStr, isPast, RECUR_FREQS, computeNextDueDate, contactLinkField } from '../dom.js';

const SUB_FREQS = [
  { value: 'monthly', label: 'Monthly' },
  { value: 'yearly', label: 'Yearly' },
  { value: 'weekly', label: 'Weekly' },
];

let state = {
  tab: 'bills', // bills | subscriptions | spend | crypto
  billCategoryFilter: 'all',
  showPaid: false,
  selectedBillId: null,
  subCategoryFilter: 'all',
  showCancelled: false,
  selectedSubId: null,
};

function money(n) {
  return `$${Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function monthlyEquivalent(amount, freq) {
  const a = Number(amount) || 0;
  if (freq === 'yearly') return a / 12;
  if (freq === 'weekly') return (a * 52) / 12;
  return a;
}

// --- Bills ---

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

function billMeta(bill, contactsById) {
  const meta = el('div', { class: 'mer-task-meta' });
  if (bill.category) meta.append(el('span', { class: 'mer-chip', text: bill.category }));
  if (typeof bill.amount === 'number') meta.append(el('span', { class: 'mer-chip', text: money(bill.amount) }));
  if (bill.dueDate) {
    meta.append(el('span', {
      class: isPast(bill.dueDate) && !bill.paid ? 'mer-chip is-overdue' : 'mer-chip',
      text: fmtDate(bill.dueDate),
    }));
  }
  if (bill.autopay) meta.append(el('span', { class: 'mer-chip', text: 'Autopay' }));
  if (bill.recurring?.freq) meta.append(el('span', { class: 'mer-chip', text: RECUR_FREQS.find((f) => f.value === bill.recurring.freq)?.label || 'Repeats' }));
  if (bill.contactId && contactsById.has(bill.contactId)) {
    meta.append(el('span', { class: 'mer-chip', text: `👤 ${contactsById.get(bill.contactId).name}` }));
  }
  return meta;
}

function billRow(bill, ctx, onSelect, contactsById) {
  const row = el('div', { class: 'mer-task-row' }, [
    el('input', {
      type: 'checkbox', checked: !!bill.paid,
      onclick: (e) => { e.stopPropagation(); markPaid(ctx, bill, e.target.checked); },
    }),
    el('span', { class: bill.paid ? 'mer-task-title is-done' : 'mer-task-title', text: bill.name || '(untitled bill)' }),
    billMeta(bill, contactsById),
  ]);
  row.addEventListener('click', () => onSelect(bill.id));
  return row;
}

function paymentHistory(bill, payments, ctx, rerender) {
  const list = el('div', { class: 'mer-people-list' });
  for (const p of payments.sort((a, b) => (a.datePaid < b.datePaid ? 1 : -1))) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: money(p.amountPaid) }),
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

function billDetailEditor(bill, ctx, rerender, allContacts) {
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
    el('div', { class: 'mer-subsection-label', text: 'Linked contact' }),
    contactLinkField(allContacts, bill.contactId, ctx,
      (contactId) => patch({ contactId }),
      () => patch({ contactId: null })),
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

async function renderBillsTab(container, ctx, rerender) {
  const [bills, allContacts] = await Promise.all([ctx.data.Bills.list(), ctx.data.Contacts.list()]);
  const contactsById = new Map(allContacts.map((c) => [c.id, c]));
  const categories = [...new Set(bills.map((b) => b.category).filter(Boolean))];

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New bill — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Bills.create({ name: e.target.value.trim(), paid: false });
      e.target.value = '';
    },
  });
  const categorySelect = el('select', { onchange: (e) => { state.billCategoryFilter = e.target.value; rerender(); } }, [
    el('option', { value: 'all', text: 'All categories', selected: state.billCategoryFilter === 'all' }),
    ...categories.map((c) => el('option', { value: c, text: c, selected: c === state.billCategoryFilter })),
  ]);
  const paidToggle = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: state.showPaid, onchange: (e) => { state.showPaid = e.target.checked; rerender(); } }),
    el('span', { text: 'Show paid' }),
  ]);
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd, categorySelect, paidToggle]));

  const filtered = bills
    .filter((b) => state.billCategoryFilter === 'all' || b.category === state.billCategoryFilter)
    .filter((b) => state.showPaid || !b.paid)
    .sort((a, b) => (a.dueDate || '9999') < (b.dueDate || '9999') ? -1 : 1);

  const area = el('div', {});
  container.append(area);
  const onSelect = (id) => { state.selectedBillId = state.selectedBillId === id ? null : id; rerender(); };

  if (!filtered.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No bills match the current filters.' }));
  } else {
    for (const bill of filtered) area.append(billRow(bill, ctx, onSelect, contactsById));
  }

  if (state.selectedBillId) {
    const bill = bills.find((b) => b.id === state.selectedBillId);
    if (bill) container.append(billDetailEditor(bill, ctx, rerender, allContacts));
  }
}

// --- Subscriptions ---

function subDetailEditor(sub, ctx, rerender) {
  const patch = (fields) => ctx.data.Subscriptions.update(sub.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const nameInput = el('input', { type: 'text', value: sub.name || '', onchange: (e) => patch({ name: e.target.value }) });
  const amountInput = el('input', { type: 'number', step: '0.01', value: sub.amount ?? '', onchange: (e) => patch({ amount: Number(e.target.value) || 0 }) });
  const freqSelect = el('select', { onchange: (e) => patch({ billingFreq: e.target.value }) },
    SUB_FREQS.map((f) => el('option', { value: f.value, text: f.label, selected: f.value === (sub.billingFreq || 'monthly') })));
  const categoryInput = el('input', { type: 'text', value: sub.category || '', placeholder: 'streaming, software…', onchange: (e) => patch({ category: e.target.value }) });
  const renewalInput = el('input', { type: 'date', value: sub.renewalDate || '', onchange: (e) => patch({ renewalDate: e.target.value || null }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(sub.notes || '')]);
  const activeCheckbox = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: sub.stillInUse !== false, onchange: (e) => patch({ stillInUse: e.target.checked }) }),
    el('span', { text: 'Still using this' }),
  ]);

  return el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: sub.name || '(untitled subscription)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedSubId = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Name', nameInput),
      field('Amount', amountInput),
      field('Billing', freqSelect),
      field('Category', categoryInput),
      field('Renewal date', renewalInput),
    ]),
    activeCheckbox,
    field('Notes', notesInput),
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete subscription',
      onclick: async () => { await ctx.data.Subscriptions.remove(sub.id); state.selectedSubId = null; rerender(); },
    }),
  ]);
}

async function renderSubscriptionsTab(container, ctx, rerender) {
  const subs = await ctx.data.Subscriptions.list();
  const categories = [...new Set(subs.map((s) => s.category).filter(Boolean))];

  const quickAdd = el('input', {
    type: 'text', class: 'mer-quick-add', placeholder: '+ New subscription — type a name and press Enter',
    onkeydown: async (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      await ctx.data.Subscriptions.create({ name: e.target.value.trim(), amount: 0, billingFreq: 'monthly', stillInUse: true });
      e.target.value = '';
    },
  });
  const categorySelect = el('select', { onchange: (e) => { state.subCategoryFilter = e.target.value; rerender(); } }, [
    el('option', { value: 'all', text: 'All categories', selected: state.subCategoryFilter === 'all' }),
    ...categories.map((c) => el('option', { value: c, text: c, selected: c === state.subCategoryFilter })),
  ]);
  const cancelledToggle = el('label', { class: 'mer-checkbox-label' }, [
    el('input', { type: 'checkbox', checked: state.showCancelled, onchange: (e) => { state.showCancelled = e.target.checked; rerender(); } }),
    el('span', { text: 'Show cancelled' }),
  ]);
  container.append(el('div', { class: 'mer-toolbar' }, [quickAdd, categorySelect, cancelledToggle]));

  const filtered = subs
    .filter((s) => state.subCategoryFilter === 'all' || s.category === state.subCategoryFilter)
    .filter((s) => state.showCancelled || s.stillInUse !== false);

  const totalMonthly = filtered.filter((s) => s.stillInUse !== false).reduce((sum, s) => sum + monthlyEquivalent(s.amount, s.billingFreq), 0);
  const area = el('div', {});
  container.append(area);
  area.append(el('p', {}, [el('strong', { text: `${money(totalMonthly)}/mo` }), el('span', { text: ' across active subscriptions' })]));

  if (!filtered.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No subscriptions match the current filters.' }));
  } else {
    for (const sub of filtered) {
      const row = el('div', { class: 'mer-task-row' }, [
        el('span', { class: sub.stillInUse === false ? 'mer-task-title is-done' : 'mer-task-title', text: sub.name || '(untitled)' }),
        el('div', { class: 'mer-task-meta' }, [
          el('span', { class: 'mer-chip', text: money(sub.amount) }),
          el('span', { class: 'mer-chip', text: SUB_FREQS.find((f) => f.value === (sub.billingFreq || 'monthly'))?.label }),
          sub.category ? el('span', { class: 'mer-chip', text: sub.category }) : null,
          sub.stillInUse === false ? el('span', { class: 'mer-chip', text: 'Cancelled' }) : null,
        ]),
      ]);
      row.addEventListener('click', () => { state.selectedSubId = state.selectedSubId === sub.id ? null : sub.id; rerender(); });
      area.append(row);
    }
  }

  if (state.selectedSubId) {
    const sub = subs.find((s) => s.id === state.selectedSubId);
    if (sub) container.append(subDetailEditor(sub, ctx, rerender));
  }
}

// --- Yearly spend: bill payments + annualized active subscriptions, combined by category ---

async function renderSpendSummary(container, ctx) {
  const [payments, bills, subs] = await Promise.all([ctx.data.BillPayments.list(), ctx.data.Bills.list(), ctx.data.Subscriptions.list()]);
  const categoryById = new Map(bills.map((b) => [b.id, b.category || 'Uncategorized']));
  const year = new Date().getFullYear().toString();

  const totals = new Map();
  for (const payment of payments) {
    if (!payment.datePaid?.startsWith(year)) continue;
    const category = categoryById.get(payment.billId) || 'Uncategorized';
    totals.set(category, (totals.get(category) || 0) + Number(payment.amountPaid || 0));
  }
  for (const sub of subs) {
    if (sub.stillInUse === false) continue;
    const category = sub.category || 'Uncategorized';
    totals.set(category, (totals.get(category) || 0) + monthlyEquivalent(sub.amount, sub.billingFreq) * 12);
  }

  if (!totals.size) {
    container.append(el('p', { class: 'mer-muted', text: `No bill payments or active subscriptions for ${year} yet.` }));
    return;
  }

  const sorted = [...totals.entries()].sort((a, b) => b[1] - a[1]);
  const grandTotal = sorted.reduce((sum, [, v]) => sum + v, 0);
  const list = el('div', { class: 'mer-people-list' });
  for (const [category, total] of sorted) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: category })]),
      el('div', { text: money(total) }),
    ]));
  }
  container.append(
    el('h3', { text: `${year} spend: ${money(grandTotal)}` }),
    el('p', { class: 'mer-muted', text: 'Combines logged bill payments with active subscriptions annualized at their current rate.' }),
    list,
  );
}

// --- Crypto tickers (CoinGecko, keyless) ---

function cryptoRow(coinId, price, ctx, watchlist, rerender) {
  const change = price?.usd_24h_change;
  const changeText = typeof change === 'number' ? `${change >= 0 ? '+' : ''}${change.toFixed(2)}%` : '—';
  return el('div', { class: 'mer-person-card' }, [
    el('div', { class: 'mer-person-info' }, [
      el('div', { class: 'mer-person-name', text: coinId }),
      el('div', { class: 'mer-person-meta', text: price ? `${money(price.usd)} · ${changeText} (24h)` : 'No price yet' }),
    ]),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×',
      onclick: async () => {
        await ctx.data.Settings.set('cryptoWatchlist', watchlist.filter((c) => c !== coinId));
        await ctx.data.Settings.set('cryptoPricesCache', null);
        rerender();
      },
    }),
  ]);
}

async function renderCryptoTab(area, ctx, rerender) {
  const watchlist = await ctx.data.Settings.get('cryptoWatchlist');
  const prices = await ctx.data.getCryptoPrices();
  const cache = await ctx.data.Settings.get('cryptoPricesCache');

  const statusText = cache?.fetchedAt
    ? `Prices (CoinGecko) as of ${new Date(cache.fetchedAt).toLocaleString()}.`
    : 'No prices fetched yet.';

  area.append(el('p', { class: 'mer-muted' }, [
    document.createTextNode(statusText + ' '),
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '🔄 Refresh',
      onclick: async () => { await ctx.data.Settings.set('cryptoPricesCache', null); rerender(); },
    }),
  ]));

  if (!watchlist.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No coins on your watchlist yet.' }));
  } else {
    area.append(el('div', { class: 'mer-people-list' },
      watchlist.map((coinId) => cryptoRow(coinId, prices[coinId], ctx, watchlist, rerender))));
  }

  const newCoin = el('input', { type: 'text', placeholder: 'CoinGecko ID (e.g. bitcoin, ethereum, solana)' });
  const addBtn = el('button', {
    type: 'button', text: '+ Add coin',
    onclick: async () => {
      const id = newCoin.value.trim().toLowerCase();
      if (!id || watchlist.includes(id)) return;
      await ctx.data.Settings.set('cryptoWatchlist', [...watchlist, id]);
      await ctx.data.Settings.set('cryptoPricesCache', null);
      rerender();
    },
  });
  area.append(el('div', { class: 'mer-person-form' }, [newCoin, addBtn]));
  area.append(el('p', { class: 'mer-muted', text: 'Coin IDs are CoinGecko\'s slugs, not ticker symbols -- e.g. "bitcoin", not "BTC".' }));
}

// --- Root ---

function tabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'bills' ? 'is-active' : '', text: 'Bills', onclick: () => { state.tab = 'bills'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'subscriptions' ? 'is-active' : '', text: 'Subscriptions', onclick: () => { state.tab = 'subscriptions'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'spend' ? 'is-active' : '', text: 'Yearly Spend', onclick: () => { state.tab = 'spend'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'crypto' ? 'is-active' : '', text: 'Crypto', onclick: () => { state.tab = 'crypto'; rerender(); } }),
  ]);
}

export async function renderFinance(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Finance' }));
  canvas.append(el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]));

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'bills') await renderBillsTab(area, ctx, rerender);
  else if (state.tab === 'subscriptions') await renderSubscriptionsTab(area, ctx, rerender);
  else if (state.tab === 'spend') await renderSpendSummary(area, ctx);
  else await renderCryptoTab(area, ctx, rerender);
}
