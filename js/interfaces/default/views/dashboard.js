import { el, fmtDate } from '../dom.js';

// surprise: undefined = not tried yet; null = tried, nothing to suggest
// (an empty pool is a legitimate result, not a bug -- but it needs to say
// so, or a working "nothing to suggest" looks identical to a dead button).
let state = {
  surprise: undefined,
};

function surpriseSection(ctx, rerender) {
  const button = el('button', {
    type: 'button', text: '🎲 Surprise me',
    onclick: async () => { state.surprise = await ctx.data.getSurpriseMe(); rerender(); },
  });
  if (state.surprise === undefined) return el('div', {}, [button]);
  if (!state.surprise) {
    return el('div', {}, [
      button,
      el('p', { class: 'mer-muted', text: 'Nothing in the queue — add a want-to-go place, an unread book, an untried recipe, or a bucket-list goal to get a pick.' }),
    ]);
  }

  return el('div', {}, [
    button,
    el('p', {}, [
      el('span', { class: 'mer-chip', text: state.surprise.kind }),
      el('span', { text: ` ${state.surprise.title}` }),
      el('button', { type: 'button', text: 'Go there →', onclick: () => ctx.navigate(state.surprise.module) }),
    ]),
  ]);
}

function weatherSection(weather, ctx, rerender) {
  if (!weather) {
    return el('p', { class: 'mer-muted' }, [
      document.createTextNode('No weather set. '),
      el('button', {
        type: 'button', class: 'mer-icon-btn', text: '📍 Use my location',
        onclick: () => {
          if (!navigator.geolocation) { alert('Geolocation is not available in this browser.'); return; }
          navigator.geolocation.getCurrentPosition(
            async (pos) => {
              await ctx.data.Settings.set('weatherLocation', { lat: pos.coords.latitude, lng: pos.coords.longitude, label: 'Home' });
              await ctx.data.Settings.set('weatherCache', null);
              rerender();
            },
            (err) => alert(`Couldn't get location: ${err.message}`)
          );
        },
      }),
    ]);
  }
  const { icon, label } = ctx.data.describeWeatherCode(weather.code);
  return el('p', {}, [
    el('span', { text: `${icon} ${Math.round(weather.tempF)}°F, ${label}` }),
    weather.highF != null ? el('span', { class: 'mer-muted', text: ` · H:${Math.round(weather.highF)}° L:${Math.round(weather.lowF)}°` }) : null,
    el('button', {
      type: 'button', class: 'mer-icon-btn', text: '×', title: 'Remove weather',
      onclick: async () => {
        await ctx.data.Settings.set('weatherLocation', null);
        await ctx.data.Settings.set('weatherCache', null);
        rerender();
      },
    }),
  ]);
}

function onThisDaySection(items) {
  if (!items.length) return null;
  const list = el('ul', { class: 'mer-feed' });
  for (const item of items) {
    list.append(el('li', { class: 'mer-feed-item' }, [
      el('span', { class: 'mer-feed-module', text: item.kind }),
      el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
      el('span', { class: 'mer-feed-date', text: item.year }),
    ]));
  }
  return el('div', {}, [el('div', { class: 'mer-subsection-label', text: 'On this day' }), list]);
}

export async function renderDashboard(canvas, ctx, rerender) {
  const [billDueSoonDays, documentExpiryDays, onThisDay, weather] = await Promise.all([
    ctx.data.Settings.get('billDueSoonDays'),
    ctx.data.Settings.get('documentExpiryDays'),
    ctx.data.getOnThisDay(),
    ctx.data.getWeather(),
  ]);
  const feed = await ctx.data.getDueSoonFeed(7, billDueSoonDays, documentExpiryDays);
  canvas.append(el('h1', { text: 'Today' }));
  canvas.append(weatherSection(weather, ctx, rerender));

  if (!feed.length) {
    canvas.append(el('p', { class: 'mer-muted', text: 'Nothing due in the next 7 days.' }));
  } else {
    const list = el('ul', { class: 'mer-feed' });
    for (const item of feed) {
      list.append(
        el('li', { class: item.overdue ? 'mer-feed-item is-overdue' : 'mer-feed-item' }, [
          el('span', { class: 'mer-feed-module', text: item.module }),
          el('span', { class: 'mer-feed-title', text: item.title || '(untitled)' }),
          el('span', { class: 'mer-feed-date', text: fmtDate(item.dueDate) }),
        ])
      );
    }
    canvas.append(list);
  }

  const onThisDayEl = onThisDaySection(onThisDay);
  if (onThisDayEl) canvas.append(onThisDayEl);

  canvas.append(el('div', { class: 'mer-subsection-label', text: 'Not sure what to do?' }));
  canvas.append(surpriseSection(ctx, rerender));
}
