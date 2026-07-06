import { el } from '../dom.js';

export async function renderSettings(canvas, ctx) {
  canvas.append(el('h1', { text: 'Settings' }));
  const settings = await ctx.data.Settings.getAll();

  const select = (label, options, current, onChange) => {
    const sel = el('select', { onchange: (e) => onChange(e.target.value) });
    for (const opt of options) {
      sel.append(el('option', { value: opt.value, text: opt.label, selected: opt.value === current }));
    }
    return el('label', { class: 'mer-setting' }, [el('span', { text: label }), sel]);
  };

  canvas.append(
    select('Theme', [
      { value: 'dark', label: 'Dark' },
      { value: 'light', label: 'Light' },
    ], settings.theme, (v) => ctx.data.Settings.set('theme', v)),

    select('Accent', [
      { value: 'brass', label: 'Brass' },
      { value: 'teal', label: 'Teal' },
      { value: 'garnet', label: 'Garnet' },
    ], settings.accent, (v) => ctx.data.Settings.set('accent', v)),

    select('Density', [
      { value: 'comfortable', label: 'Comfortable' },
      { value: 'compact', label: 'Compact' },
    ], settings.density, (v) => ctx.data.Settings.set('density', v)),

    select('Interface',
      ctx.listInterfaces().map((i) => ({ value: i.id, label: i.name })),
      settings.activeInterface,
      (v) => ctx.switchInterface(v)),

    el('label', { class: 'mer-setting' }, [
      el('span', { text: 'Bill due-soon alert (days)' }),
      el('input', {
        type: 'number', min: '1', max: '90', value: settings.billDueSoonDays,
        onchange: (e) => ctx.data.Settings.set('billDueSoonDays', Number(e.target.value) || 7),
      }),
    ]),
  );
}
