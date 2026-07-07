import { el, fmtDate, todayStr, parseTags } from '../dom.js';

let state = {
  tab: 'recipes', // recipes | grocery
  selectedId: null,
  detailScale: null, // servings to scale the open recipe's detail view to
  grocerySelections: new Map(), // recipeId -> { checked, servings }
};

function formatQty(n) {
  if (!Number.isFinite(n)) return '';
  const rounded = Math.round(n * 100) / 100;
  return String(rounded);
}

function scaleIngredient(ing, factor) {
  const qty = Number(ing.qty);
  if (!ing.qty || !Number.isFinite(qty)) return ing.qty || '';
  return formatQty(qty * factor);
}

// --- Recipe cards (list view) ---

function recipeCard(recipe, cover, ctx, onSelect) {
  const thumb = cover
    ? el('img', { class: 'mer-place-photo', src: ctx.data.attachmentUrl(cover), alt: recipe.title })
    : el('div', { class: 'mer-place-photo mer-place-photo-empty mer-link-icon', text: '🍳' });

  const card = el('div', { class: 'mer-place-card' }, [
    thumb,
    el('div', { class: 'mer-place-body' }, [
      el('div', { class: 'mer-place-name', text: recipe.title || '(untitled)' }),
      el('div', { class: 'mer-place-meta' }, [
        el('span', { class: 'mer-chip', text: `Serves ${recipe.baseServings || 4}` }),
        ...(recipe.tags || []).map((t) => el('span', { class: 'mer-chip mer-chip-tag', text: `#${t}` })),
      ]),
    ]),
  ]);
  card.addEventListener('click', () => onSelect(recipe.id));
  return card;
}

// --- Ingredients editor ---

function ingredientsEditor(recipe, ctx, rerender) {
  const scale = state.detailScale || recipe.baseServings || 4;
  const factor = scale / (recipe.baseServings || 4);

  const rows = el('div', {}, (recipe.ingredients || []).map((ing) => {
    const nameInput = el('input', { type: 'text', value: ing.name || '', placeholder: 'Ingredient' });
    const qtyInput = el('input', { type: 'text', value: ing.qty ?? '', placeholder: 'Qty' });
    const unitInput = el('input', { type: 'text', value: ing.unit || '', placeholder: 'Unit' });
    const commit = () => {
      const ingredients = recipe.ingredients.map((i) => i.id === ing.id
        ? { ...i, name: nameInput.value, qty: qtyInput.value, unit: unitInput.value }
        : i);
      ctx.data.Recipes.update(recipe.id, { ingredients }).then(rerender);
    };
    nameInput.onchange = commit;
    qtyInput.onchange = commit;
    unitInput.onchange = commit;

    const scaledLabel = factor !== 1
      ? el('span', { class: 'mer-chip', text: `→ ${scaleIngredient(ing, factor)} ${ing.unit || ''}`.trim() })
      : null;

    return el('div', { class: 'mer-person-form' }, [
      nameInput, qtyInput, unitInput, scaledLabel,
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: () => {
          const ingredients = recipe.ingredients.filter((i) => i.id !== ing.id);
          ctx.data.Recipes.update(recipe.id, { ingredients }).then(rerender);
        },
      }),
    ]);
  }));

  const newName = el('input', { type: 'text', placeholder: 'Ingredient' });
  const newQty = el('input', { type: 'text', placeholder: 'Qty' });
  const newUnit = el('input', { type: 'text', placeholder: 'Unit' });
  const addBtn = el('button', {
    type: 'button', text: 'Add ingredient',
    onclick: () => {
      if (!newName.value.trim()) return;
      const ingredients = [...(recipe.ingredients || []), {
        id: crypto.randomUUID(), name: newName.value.trim(), qty: newQty.value.trim(), unit: newUnit.value.trim(),
      }];
      ctx.data.Recipes.update(recipe.id, { ingredients }).then(rerender);
    },
  });

  const scaleInput = el('input', {
    type: 'number', min: '1', value: scale,
    onchange: (e) => { state.detailScale = Number(e.target.value) || (recipe.baseServings || 4); rerender(); },
  });

  return el('div', {}, [
    el('label', { class: 'mer-field' }, [el('span', { text: 'Scale to servings' }), scaleInput]),
    rows,
    el('div', { class: 'mer-person-form' }, [newName, newQty, newUnit, addBtn]),
  ]);
}

// --- Steps editor ---

function stepsEditor(recipe, ctx, rerender) {
  const list = el('div', { class: 'mer-subtasks' });
  (recipe.steps || []).forEach((step, i) => {
    list.append(el('div', { class: 'mer-subtask' }, [
      el('span', { text: `${i + 1}. ${step.text}` }),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: () => {
          const steps = recipe.steps.filter((s) => s.id !== step.id);
          ctx.data.Recipes.update(recipe.id, { steps }).then(rerender);
        },
      }),
    ]));
  });

  const newStepInput = el('input', {
    type: 'text', placeholder: 'Add a step and press Enter',
    onkeydown: (e) => {
      if (e.key !== 'Enter' || !e.target.value.trim()) return;
      const steps = [...(recipe.steps || []), { id: crypto.randomUUID(), text: e.target.value.trim() }];
      e.target.value = '';
      ctx.data.Recipes.update(recipe.id, { steps }).then(rerender);
    },
  });

  return el('div', {}, [list, newStepInput]);
}

// --- Cook log ---

function cookLogSection(recipe, logs, ctx, rerender) {
  const list = el('div', { class: 'mer-people-list' });
  for (const log of logs.sort((a, b) => (a.date < b.date ? 1 : -1))) {
    list.append(el('div', { class: 'mer-person-card' }, [
      el('div', { class: 'mer-person-info' }, [
        el('div', { class: 'mer-person-name', text: fmtDate(log.date) }),
        log.notes ? el('div', { class: 'mer-person-meta', text: log.notes }) : null,
      ]),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '×',
        onclick: async () => { await ctx.data.CookLogs.remove(log.id); rerender(); },
      }),
    ]));
  }

  const notesInput = el('input', { type: 'text', placeholder: 'Notes (optional)' });
  const madeItBtn = el('button', {
    type: 'button', text: 'Made it!',
    onclick: async () => {
      await ctx.data.CookLogs.create({ recipeId: recipe.id, date: todayStr(), notes: notesInput.value.trim() });
      notesInput.value = '';
      rerender();
    },
  });

  return el('div', {}, [
    list,
    el('p', { class: 'mer-muted', text: `Cooked ${logs.length} time${logs.length === 1 ? '' : 's'}.` }),
    el('div', { class: 'mer-person-form' }, [notesInput, madeItBtn]),
  ]);
}

// --- Detail editor ---

function detailEditor(recipe, cover, logs, ctx, rerender) {
  const patch = (fields) => ctx.data.Recipes.update(recipe.id, fields).then(rerender);
  const field = (labelText, inputEl) => el('label', { class: 'mer-field' }, [el('span', { text: labelText }), inputEl]);

  const titleInput = el('input', { type: 'text', value: recipe.title || '', onchange: (e) => patch({ title: e.target.value }) });
  const servingsInput = el('input', { type: 'number', min: '1', value: recipe.baseServings ?? 4, onchange: (e) => patch({ baseServings: Number(e.target.value) || 4 }) });
  const tagsInput = el('input', { type: 'text', value: (recipe.tags || []).join(', '), placeholder: 'comma, separated, tags', onchange: (e) => patch({ tags: parseTags(e.target.value) }) });
  const notesInput = el('textarea', { rows: '2', placeholder: 'Notes', onchange: (e) => patch({ notes: e.target.value }) }, [document.createTextNode(recipe.notes || '')]);

  const coverPlaceholder = el('p', { class: 'mer-muted', text: 'Loading photo…' });
  const cookLogPlaceholder = el('p', { class: 'mer-muted', text: 'Loading cook log…' });

  const detail = el('div', { class: 'mer-task-detail' }, [
    el('div', { class: 'mer-detail-header' }, [
      el('h3', { text: recipe.title || '(untitled)' }),
      el('button', { type: 'button', class: 'mer-icon-btn', text: '✕ Close', onclick: () => { state.selectedId = null; state.detailScale = null; rerender(); } }),
    ]),
    el('div', { class: 'mer-field-grid' }, [
      field('Title', titleInput),
      field('Base servings', servingsInput),
    ]),
    field('Tags', tagsInput),
    field('Notes', notesInput),
    el('div', { class: 'mer-subsection-label', text: 'Photo' }),
    coverPlaceholder,
    el('div', { class: 'mer-subsection-label', text: 'Ingredients' }),
    ingredientsEditor(recipe, ctx, rerender),
    el('div', { class: 'mer-subsection-label', text: 'Steps' }),
    stepsEditor(recipe, ctx, rerender),
    el('div', { class: 'mer-subsection-label', text: 'Cook log' }),
    cookLogPlaceholder,
    el('button', {
      type: 'button', class: 'mer-danger-btn', text: 'Delete recipe',
      onclick: async () => { await ctx.data.Recipes.remove(recipe.id); state.selectedId = null; rerender(); },
    }),
  ]);

  const coverWrap = el('div', {}, [
    cover
      ? el('div', { class: 'mer-photo-thumb' }, [
        el('img', { src: ctx.data.attachmentUrl(cover), alt: recipe.title }),
        el('button', {
          type: 'button', class: 'mer-photo-remove', text: '×',
          onclick: async () => { await ctx.data.Attachments.remove(cover.id); rerender(); },
        }),
      ])
      : el('label', { class: 'mer-photo-add' }, [
        el('span', { text: '+ Photo' }),
        el('input', {
          type: 'file', accept: 'image/*',
          onchange: async (e) => {
            if (e.target.files[0]) await ctx.data.createAttachment(e.target.files[0], 'recipes', recipe.id);
            rerender();
          },
        }),
      ]),
  ]);
  coverPlaceholder.replaceWith(coverWrap);
  cookLogPlaceholder.replaceWith(cookLogSection(recipe, logs, ctx, rerender));

  return detail;
}

// --- Grocery list generator ---

async function renderGroceryList(container, ctx, recipes) {
  const pickerRows = el('div', {}, recipes.map((recipe) => {
    const sel = state.grocerySelections.get(recipe.id) || { checked: false, servings: recipe.baseServings || 4 };
    state.grocerySelections.set(recipe.id, sel);

    const checkbox = el('input', {
      type: 'checkbox', checked: sel.checked,
      onchange: (e) => { sel.checked = e.target.checked; renderGroceryList(container, ctx, recipes); },
    });
    const servingsInput = el('input', {
      type: 'number', min: '1', value: sel.servings,
      onchange: (e) => { sel.servings = Number(e.target.value) || (recipe.baseServings || 4); renderGroceryList(container, ctx, recipes); },
    });
    return el('label', { class: 'mer-checkbox-label' }, [checkbox, el('span', { text: recipe.title || '(untitled)' }), servingsInput]);
  }));

  const totals = new Map(); // "name|unit" -> { name, unit, qty, exact }
  for (const recipe of recipes) {
    const sel = state.grocerySelections.get(recipe.id);
    if (!sel?.checked) continue;
    const factor = sel.servings / (recipe.baseServings || 4);
    for (const ing of recipe.ingredients || []) {
      const key = `${(ing.name || '').toLowerCase()}|${(ing.unit || '').toLowerCase()}`;
      const qty = Number(ing.qty);
      const existing = totals.get(key);
      if (Number.isFinite(qty)) {
        const scaled = qty * factor;
        if (existing && existing.exact) existing.qty += scaled;
        else totals.set(key, { name: ing.name, unit: ing.unit, qty: scaled, exact: true });
      } else if (!existing) {
        totals.set(key, { name: ing.name, unit: ing.unit, qty: ing.qty, exact: false });
      }
    }
  }

  const resultList = el('div', { class: 'mer-people-list' });
  if (totals.size) {
    for (const { name, unit, qty, exact } of totals.values()) {
      resultList.append(el('div', { class: 'mer-person-card' }, [
        el('div', { class: 'mer-person-info' }, [el('div', { class: 'mer-person-name', text: name })]),
        el('div', { text: exact ? `${formatQty(qty)} ${unit || ''}`.trim() : String(qty) }),
      ]));
    }
  }

  container.innerHTML = '';
  container.append(
    el('p', { class: 'mer-muted', text: 'Check the recipes to shop for and set servings, and the list below combines every ingredient.' }),
    pickerRows,
    el('div', { class: 'mer-subsection-label', text: 'Combined grocery list' }),
    totals.size ? resultList : el('p', { class: 'mer-muted', text: 'Select at least one recipe above.' }),
  );
}

// --- Toolbar ---

function tabsBar(rerender) {
  return el('div', { class: 'mer-toggle-group' }, [
    el('button', { type: 'button', class: state.tab === 'recipes' ? 'is-active' : '', text: 'Recipes', onclick: () => { state.tab = 'recipes'; rerender(); } }),
    el('button', { type: 'button', class: state.tab === 'grocery' ? 'is-active' : '', text: 'Grocery List', onclick: () => { state.tab = 'grocery'; rerender(); } }),
  ]);
}

export async function renderRecipes(canvas, ctx, rerender) {
  canvas.append(el('h1', { text: 'Recipes' }));

  const recipes = await ctx.data.Recipes.list();
  const row = el('div', { class: 'mer-toolbar' }, [tabsBar(rerender)]);

  if (state.tab === 'recipes') {
    const quickAdd = el('input', {
      type: 'text', class: 'mer-quick-add', placeholder: '+ New recipe — type a title and press Enter',
      onkeydown: async (e) => {
        if (e.key !== 'Enter' || !e.target.value.trim()) return;
        await ctx.data.Recipes.create({ title: e.target.value.trim(), baseServings: 4, ingredients: [], steps: [], tags: [] });
        e.target.value = '';
      },
    });
    row.append(quickAdd);
  }
  canvas.append(row);

  const area = el('div', { class: 'mer-task-list-area' });
  canvas.append(area);

  if (state.tab === 'grocery') {
    await renderGroceryList(area, ctx, recipes);
    return;
  }

  const onSelect = (id) => { state.selectedId = state.selectedId === id ? null : id; state.detailScale = null; rerender(); };

  if (!recipes.length) {
    area.append(el('p', { class: 'mer-muted', text: 'No recipes yet.' }));
  } else {
    const grid = el('div', { class: 'mer-place-grid' });
    area.append(grid);
    const covers = await Promise.all(recipes.map((r) => ctx.data.getAttachmentsFor('recipes', r.id)));
    recipes.forEach((recipe, i) => grid.append(recipeCard(recipe, covers[i][0], ctx, onSelect)));
  }

  if (state.selectedId) {
    const recipe = recipes.find((r) => r.id === state.selectedId);
    if (recipe) {
      const [cover, logs] = await Promise.all([
        ctx.data.getAttachmentsFor('recipes', recipe.id).then((a) => a[0]),
        ctx.data.CookLogs.byIndex('recipeId', recipe.id),
      ]);
      canvas.append(detailEditor(recipe, cover, logs, ctx, rerender));
    }
  }
}
