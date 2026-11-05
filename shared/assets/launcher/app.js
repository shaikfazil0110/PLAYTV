/* PLAYTV launcher logic.
 * Talks to the Android app through window.PlayTVNative (only injected on this page).
 * Also runs in a plain desktop browser for development: the bridge is optional. */
(() => {
  'use strict';

  const native = window.PlayTVNative || null;
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  /* ------------------------------------------------------------------ helpers */
  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    if (attrs) for (const [k, v] of Object.entries(attrs)) {
      if (v === false || v == null) continue;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const c of children.flat()) if (c != null) el.append(c.nodeType ? c : document.createTextNode(c));
    return el;
  }

  const store = {
    get(key, fallback) {
      try {
        const raw = native && native.getPref ? native.getPref(key) : localStorage.getItem('playtv.' + key);
        return raw ? JSON.parse(raw) : fallback;
      } catch (_) { return fallback; }
    },
    set(key, value) {
      try {
        const raw = JSON.stringify(value);
        if (native && native.setPref) native.setPref(key, raw);
        else localStorage.setItem('playtv.' + key, raw);
      } catch (_) { /* storage can be unavailable; the launcher still works */ }
    }
  };

  function hue(str) {
    let x = 0;
    for (let i = 0; i < str.length; i++) x = (x * 31 + str.charCodeAt(i)) >>> 0;
    return x % 360;
  }
  const gradient = (name) => {
    const a = hue(name);
    return `linear-gradient(135deg, hsl(${a} 55% 30%), hsl(${(a + 55) % 360} 60% 13%))`;
  };

  let toastTimer = 0;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('show'), 2600);
  }

  /* ------------------------------------------------------------------ state */
  const state = {
    games: [],
    byId: new Map(),
    fav: new Set(store.get('fav', [])),
    recent: store.get('recent', []),
    tab: 'home',
    heroGame: null,
    onlyReady: false,
    caps: { probed: false, webgl1: false, webgl2: false, webgpu: false, webgpuDetail: '' }
  };

  /* ------------------------------------------------------------------ catalog */
  async function loadCatalog() {
    let data = null;
    try {
      const r = await fetch('../games.json');
      data = await r.json();
    } catch (_) {
      // Fallback for runtimes where fetch() of packaged assets is blocked.
      try { if (native && native.getCatalog) data = JSON.parse(native.getCatalog()); } catch (_) {}
    }
    if (!Array.isArray(data) || !data.length) {
      $('#hero-title').textContent = 'Catalog unavailable';
      $('#hero-note').textContent = 'games.json could not be loaded.';
      return;
    }
    state.games = data;
    state.byId = new Map(data.map(g => [g.id, g]));
    renderAll();
  }

  /* Compatibility of one entry on THIS device.  'ok' | 'warn' | '' (unknown/not applicable) */
  function compat(game) {
    if (!state.caps.probed || game.kind === 'portal') return { cls: '', label: '' };
    if (game.requires === 'webgpu' && !state.caps.webgpu) return { cls: 'warn', label: 'Needs WebGPU' };
    if (game.requires === 'webgl' && !(state.caps.webgl1 || state.caps.webgl2)) return { cls: 'warn', label: 'Needs WebGL' };
    return { cls: 'ok', label: 'Ready' };
  }

  /* ------------------------------------------------------------------ rendering */
  function makeCard(game) {
    const c = compat(game);
    const cover = h('div', { class: 'cover', text: game.icon || '🎮' });
    if (game.art) {
      const img = h('img', { src: game.art, alt: '', loading: 'lazy' });
      img.onerror = () => img.remove();
      cover.append(img);
    }
    const card = h('div', { class: 'card' + (c.cls === 'warn' ? ' dim' : ''), tabindex: 0, 'data-id': game.id, role: 'button' },
      cover,
      h('div', { class: 'shade' }),
      state.fav.has(game.id) ? h('div', { class: 'fav', text: '★' }) : null,
      c.label ? h('div', { class: 'tag ' + c.cls, text: c.label }) : (game.kind === 'portal' ? h('div', { class: 'tag', text: 'Site' }) : null),
      h('div', { class: 'meta' },
        h('div', { class: 't', text: game.name }),
        h('div', { class: 's', text: game.genre })));
    cover.style.background = gradient(game.name);
    card.addEventListener('focus', () => onCardFocus(game));
    card.addEventListener('click', () => requestLaunch(game));
    return card;
  }

  function onCardFocus(game) {
    if (state.tab === 'home') setHero(game);
  }

  function setHero(game) {
    if (!game) return;
    state.heroGame = game;
    const c = compat(game);
    $('#hero').style.background = gradient(game.name);
    const glyph = $('#hero-glyph');
    glyph.textContent = game.icon || '🎮';
    $('#hero-kicker').textContent = game.platform;
    $('#hero-title').textContent = game.name;
    $('#hero-note').textContent = game.note || '';
    const chips = $('#hero-chips');
    chips.textContent = '';
    chips.append(h('span', { class: 'chip', text: game.genre }));
    if (c.label) chips.append(h('span', { class: 'chip ' + c.cls, text: c.label }));
    chips.append(h('span', { class: 'chip', text: game.input === 'keys' ? 'Keyboard / pad' : game.input === 'both' ? 'Mouse + keys' : 'Cursor mode' }));
    $('#hero-fav').textContent = state.fav.has(game.id) ? '★ Favorited' : '☆ Favorite';
  }

  function addRow(parent, title, games) {
    if (!games.length) return;
    parent.append(h('section', { class: 'row' }, h('h2', { text: title }), h('div', { class: 'track' }, games.map(makeCard))));
  }

  function renderHome() {
    if (!state.games.length) return;
    const rows = $('#rows');
    rows.textContent = '';
    const games = state.games.filter(g => g.kind === 'game');
    const recent = state.recent.map(id => state.byId.get(id)).filter(Boolean);
    addRow(rows, 'Continue playing', recent);
    addRow(rows, 'Favorites', state.games.filter(g => state.fav.has(g.id)));
    if (state.caps.probed) addRow(rows, 'Ready on this device', games.filter(g => compat(g).cls === 'ok'));
    const genres = [...new Set(games.map(g => g.genre))];
    for (const genre of genres) addRow(rows, genre, games.filter(g => g.genre === genre));
    addRow(rows, 'Game sites', state.games.filter(g => g.kind === 'portal').slice(0, 12));
    if (!state.heroGame || !state.byId.has(state.heroGame.id)) setHero(recent[0] || games[0] || state.games[0]);
    else setHero(state.heroGame);
  }

  function renderGrid(id, games) {
    const grid = $(id);
    grid.textContent = '';
    games.forEach(g => grid.append(makeCard(g)));
  }

  function renderLibrary() {
    let games = state.games.filter(g => g.kind === 'game');
    if (state.onlyReady) games = games.filter(g => compat(g).cls !== 'warn');
    renderGrid('#grid-library', games);
    $('#filter-state').textContent = state.onlyReady ? 'ON' : 'OFF';
  }
  const renderBrowse = () => renderGrid('#grid-browse', state.games.filter(g => g.kind === 'portal'));
  function renderFavorites() {
    const favs = state.games.filter(g => state.fav.has(g.id));
    renderGrid('#grid-favorites', favs);
    $('#empty-favorites').hidden = favs.length > 0;
  }

  function renderAll() {
    renderHome(); renderLibrary(); renderBrowse(); renderFavorites();
  }

  /* Re-render everything but keep the D-pad focus on the same card / button. */
  function rerenderKeepFocus() {
    const a = document.activeElement;
    const id = a && a.dataset ? a.dataset.id : null;
    const elId = a && a.id ? a.id : null;
    const pane = state.tab;
    renderAll();
    let el = null;
    if (id) el = $(`#pane-${pane} .card[data-id="${id}"]`);
    else if (elId) el = document.getElementById(elId);
    if (el) el.focus({ preventScroll: true });
    else if (a && a.classList && a.classList.contains('tab')) a.focus({ preventScroll: true });
  }

  function renderTab(tab) {
    if (tab === 'home') renderHome();
    else if (tab === 'library') renderLibrary();
    else if (tab === 'browse') renderBrowse();
    else if (tab === 'favorites') renderFavorites();
  }

  /* ------------------------------------------------------------------ tabs */
  function showTab(tab, focusContent) {
    state.tab = tab;
    $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === tab));
    $$('.pane').forEach(p => p.classList.toggle('active', p.id === 'pane-' + tab));
    renderTab(tab);
    $('#pane-' + tab).scrollTop = 0;
    if (focusContent) focusFirstIn($('#pane-' + tab));
  }

  /* ------------------------------------------------------------------ launching */
  function requestLaunch(game) {
    const c = compat(game);
    if (c.cls === 'warn') {
      const what = game.requires === 'webgpu' ? 'WebGPU' : 'WebGL';
      confirmModal(
        `${game.name} needs ${what}`,
        `This device's browser engine does not provide ${what}, so the game will most likely show an error or a black screen. You can still try it.`,
        'Launch anyway',
        () => launch(game));
    } else launch(game);
  }

  function launch(game) {
    state.recent = [game.id, ...state.recent.filter(id => id !== game.id)].slice(0, 12);
    store.set('recent', state.recent);
    if (native && native.launchGame) {
      native.launchGame(JSON.stringify({ id: game.id, name: game.name, url: game.url, input: game.input, requires: game.requires }));
    } else {
      toast('Launch: ' + game.url); // desktop preview
    }
  }

  function toggleFav(game) {
    if (!game) return;
    if (state.fav.has(game.id)) state.fav.delete(game.id); else state.fav.add(game.id);
    store.set('fav', [...state.fav]);
    toast(state.fav.has(game.id) ? 'Added to favorites' : 'Removed from favorites');
    rerenderKeepFocus();
  }

  /* ------------------------------------------------------------------ modal */
  let modalCb = null;
  function confirmModal(title, text, okLabel, cb) {
    $('#modal-title').textContent = title;
    $('#modal-text').textContent = text;
    $('#modal-ok').textContent = okLabel;
    modalCb = cb;
    $('#modal').hidden = false;
    $('#modal-ok').focus();
  }
  function closeModal() {
    const wasOpen = !$('#modal').hidden;
    $('#modal').hidden = true; modalCb = null;
    return wasOpen;
  }

  /* ------------------------------------------------------------------ diagnostics */
  function dlRow(dl, k, v, cls) {
    dl.append(h('dt', { text: k }), h('dd', { text: v, class: cls || false }));
  }

  function renderDevice(info) {
    const dl = $('#dl-device');
    dl.textContent = '';
    if (!info) {
      dlRow(dl, 'Runtime', navigator.userAgent.includes('AFT') ? 'Amazon Fire TV' : 'Browser (no native bridge)');
      return;
    }
    dlRow(dl, 'Model', `${info.manufacturer || ''} ${info.deviceModel || ''}`.trim());
    dlRow(dl, 'Android', `${info.androidVersion} (API ${info.apiLevel})`);
    dlRow(dl, 'Memory', `${info.availMemMb} MB free / ${info.totalMemMb} MB`);
    dlRow(dl, 'CPU', info.cpuAbi || '-');
    dlRow(dl, 'Browser engine', info.engine || '-');
    dlRow(dl, 'WebView', info.webViewVersion || '-');
    dlRow(dl, 'App version', info.appVersion || '-');
    if (info.lowRam) dlRow(dl, 'Warning', 'Low memory device: heavy 3D games may crash', 'bad');
  }

  async function probeGraphics() {
    const caps = { probed: false, webgl1: false, webgl2: false, webgpu: false, webgpuDetail: '', renderer: '' };
    let gl1 = null, gl2 = null;
    try { gl1 = document.createElement('canvas').getContext('webgl'); } catch (_) {}
    try { gl2 = document.createElement('canvas').getContext('webgl2'); } catch (_) {}
    caps.webgl1 = !!gl1; caps.webgl2 = !!gl2;
    const gl = gl2 || gl1;
    if (gl) {
      try {
        const dbg = gl.getExtension('WEBGL_debug_renderer_info');
        caps.renderer = String(dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER));
      } catch (_) { caps.renderer = 'hidden by browser'; }
    }

    // WebGPU is probed in three separate stages so the report says exactly which one fails.
    if (!navigator.gpu || typeof navigator.gpu.requestAdapter !== 'function') {
      caps.webgpuDetail = 'navigator.gpu is not exposed by this browser engine';
    } else {
      try {
        const adapter = await Promise.race([navigator.gpu.requestAdapter(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 4000))]);
        if (!adapter) caps.webgpuDetail = 'API present, but no GPU adapter was returned';
        else {
          try {
            const device = await adapter.requestDevice();
            caps.webgpu = !!device;
            caps.webgpuDetail = device ? 'adapter and device created' : 'adapter found, device creation returned nothing';
            if (device && device.destroy) device.destroy();
          } catch (e) { caps.webgpuDetail = 'adapter found, device creation failed: ' + (e && e.message || e); }
        }
      } catch (e) { caps.webgpuDetail = 'requestAdapter failed: ' + (e && e.message || e); }
    }
    caps.probed = true;
    return caps;
  }

  function renderGpu() {
    const c = state.caps, dl = $('#dl-gpu');
    dl.textContent = '';
    if (!c.probed) { dlRow(dl, 'Status', 'Testing…'); return; }
    dlRow(dl, 'WebGL 1', c.webgl1 ? 'Available' : 'Unavailable', c.webgl1 ? 'ok' : 'bad');
    dlRow(dl, 'WebGL 2', c.webgl2 ? 'Available' : 'Unavailable', c.webgl2 ? 'ok' : 'bad');
    dlRow(dl, 'WebGPU', c.webgpu ? 'Available' : 'Unavailable', c.webgpu ? 'ok' : 'bad');
    dlRow(dl, 'WebGPU detail', c.webgpuDetail);
    dlRow(dl, 'Renderer', c.renderer || '-');
    $('#gpu-hint').textContent = c.webgpu ? ''
      : 'Games that require WebGPU will not run on this engine. They are marked "Needs WebGPU"; everything else is unaffected.';
  }

  async function runDiagnostics() {
    let info = null;
    try { if (native && native.getDeviceInfo) info = JSON.parse(native.getDeviceInfo()); } catch (_) {}
    renderDevice(info);
    state.caps = { ...state.caps, probed: false };
    renderGpu();
    state.caps = await probeGraphics();
    renderGpu();
    rerenderKeepFocus(); // badges depend on capabilities
  }

  /* ------------------------------------------------------------------ D-pad navigation */
  const FOCUSABLE = 'button:not([disabled]), .card, [tabindex="0"]';

  function visibleFocusables() {
    const modal = $('#modal');
    const scope = !modal.hidden ? modal : document;
    return $$(FOCUSABLE, scope).filter(el => {
      const pane = el.closest('.pane');
      if (pane && !pane.classList.contains('active')) return false;
      const r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0;
    });
  }

  function focusFirstIn(root) {
    const el = $$(FOCUSABLE, root).find(e => e.getBoundingClientRect().width > 0);
    if (el) el.focus({ preventScroll: true });
  }

  function focusKeep() {
    if (document.activeElement && document.activeElement !== document.body) return;
    const tab = $('.tab.active');
    if (tab) tab.focus({ preventScroll: true });
  }

  const center = r => ({ x: r.left + r.width / 2, y: r.top + r.height / 2 });

  function moveFocus(dir) {
    const items = visibleFocusables();
    if (!items.length) return;
    let cur = document.activeElement;
    if (!items.includes(cur)) { (items.find(e => e.classList.contains('tab')) || items[0]).focus(); return; }
    const o = center(cur.getBoundingClientRect());
    let best = null, bestScore = Infinity;
    for (const it of items) {
      if (it === cur) continue;
      const p = center(it.getBoundingClientRect());
      const dx = p.x - o.x, dy = p.y - o.y;
      const primary = dir === 'left' ? -dx : dir === 'right' ? dx : dir === 'up' ? -dy : dy;
      if (primary < 4) continue;
      const secondary = (dir === 'left' || dir === 'right') ? Math.abs(dy) : Math.abs(dx);
      // Strongly prefer the same row / column; allow jumping to the neighbouring one.
      const score = primary + secondary * 3 + (secondary > primary * 1.2 ? 800 : 0);
      if (score < bestScore) { bestScore = score; best = it; }
    }
    if (!best) return;
    best.focus({ preventScroll: true });
    const pane = best.closest('.pane');
    if (pane) {
      const r = best.getBoundingClientRect(), pr = pane.getBoundingClientRect();
      if (r.top < pr.top + 60) pane.scrollTop -= (pr.top + 60 - r.top) + 20;
      else if (r.bottom > pr.bottom - 40) pane.scrollTop += (r.bottom - pr.bottom + 40) + 20;
    }
    const track = best.closest('.track');
    if (track) {
      const r = best.getBoundingClientRect(), tr = track.getBoundingClientRect();
      if (r.left < tr.left) track.scrollLeft -= (tr.left - r.left) + 40;
      else if (r.right > tr.right) track.scrollLeft += (r.right - tr.right) + 40;
    }
  }

  function setupKeys() {
    const dirs = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' };
    document.addEventListener('keydown', e => {
      if (dirs[e.key]) { e.preventDefault(); moveFocus(dirs[e.key]); return; }
      if (e.key === 'Enter' || e.key === ' ') {
        const el = document.activeElement;
        if (el && (el.classList.contains('card') || el.tagName === 'BUTTON')) { e.preventDefault(); el.click(); }
      }
    }, true);
  }

  /* Called by the native layer when BACK is pressed while the launcher is visible.
   * Return true if the launcher handled it, false to let the app exit. */
  window.PlayTVLauncher = {
    handleBack() {
      if (closeModal()) return true;
      if (state.tab !== 'home') { showTab('home'); $('.tab.active').focus(); return true; }
      if (document.activeElement && !document.activeElement.classList.contains('tab')) { $('.tab.active').focus(); return true; }
      return false;
    }
  };

  /* ------------------------------------------------------------------ boot */
  function tick() {
    const d = new Date();
    $('#clock').textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  document.addEventListener('DOMContentLoaded', () => {
    tick(); setInterval(tick, 15000);
    setupKeys();

    $$('.tab').forEach(t => t.addEventListener('click', () => showTab(t.dataset.tab, t.dataset.tab !== 'system')));
    $('#hero-play').addEventListener('click', () => state.heroGame && requestLaunch(state.heroGame));
    $('#hero-fav').addEventListener('click', () => toggleFav(state.heroGame));
    $('#filter-ready').addEventListener('click', () => { state.onlyReady = !state.onlyReady; renderLibrary(); $('#filter-ready').focus(); });
    $('#modal-ok').addEventListener('click', () => { const cb = modalCb; closeModal(); if (cb) cb(); });
    $('#modal-cancel').addEventListener('click', closeModal);

    $('#btn-rerun').addEventListener('click', () => { runDiagnostics(); toast('Re-running graphics tests…'); });
    $('#btn-clear-web').addEventListener('click', () => {
      if (native && native.clearWebData) { native.clearWebData(); toast('Browser cache and cookies cleared'); }
      else toast('Only available inside the app');
    });
    $('#btn-reset-user').addEventListener('click', () => {
      state.fav.clear(); state.recent = [];
      store.set('fav', []); store.set('recent', []);
      renderAll(); toast('Favorites and history cleared');
    });

    loadCatalog().then(() => { focusKeep(); });
    runDiagnostics();
    setTimeout(focusKeep, 300);
  });
})();
