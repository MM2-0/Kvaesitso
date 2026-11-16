// Run: tools/napplet-doom/build.sh && node --test tools/napplet-doom/doom.test.mjs
// Plays the built Freedoom napplet in headless Chromium inside Amethyst's production shell.html,
// shim.js and NIP-5D CSP, injected exactly as NappletWebContract.injectPrelude does on the device.
// The native bridge is a test double that answers storage requests from an in-memory map.
// PLAYWRIGHT_MODULE and CHROMIUM override the Playwright module and browser binary.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = process.env.PLAYWRIGHT_MODULE ? await import(process.env.PLAYWRIGHT_MODULE) : require('playwright');
const root = new URL('../../', import.meta.url);
const shim = await readFile(new URL('commonsUI/src/commonMain/composeResources/files/napplet/shim.js', root), 'utf8');
const shell = await readFile(new URL('commonsUI/src/commonMain/composeResources/files/napplet/shell.html', root), 'utf8');
const contract = await readFile(new URL('commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/napplet/NappletWebContract.kt', root), 'utf8');
const appCsp = contract.match(/const val APP_CSP: String =([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('');
const webrtcGuard = contract.match(/const val WEBRTC_GUARD_JS: String =([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('');
const shellCsp = contract.match(/fun shellCsp[^=]*=([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('').replace('$frameSource', "'self'");
const artifact = await readFile(process.env.DOOM_HTML || new URL('build/index.html', import.meta.url));
const screens = new URL('build/screens/', import.meta.url);
await mkdir(screens, { recursive: true });

// NappletWebContract.injectPrelude for the locked NIP-5D posture.
function injectPrelude(html, domains) {
  const prelude = `<meta http-equiv="Content-Security-Policy" content="${appCsp}"><script>${webrtcGuard}</script>` +
    '<style>html,body{overscroll-behavior:none !important}</style>' +
    '<script>window.__nappletNip07=false;</script>' +
    `<script>window.__nappletDomains=${JSON.stringify(domains)};${shim}</script>`;
  return Buffer.concat([Buffer.from(`<!doctype html><html><head>${prelude}`), html]);
}

async function play(run, { domains = ['storage'], storage = {}, deny = false, resources = {} } = {}) {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM, args: ['--autoplay-policy=no-user-gesture-required'] });
  try {
    const page = await browser.newPage({ viewport: { width: 960, height: 640 } });
    page.setDefaultTimeout(30000);
    const consoleErrors = [];
    page.on('console', msg => { if (msg.type() === 'error' || /Content Security Policy/.test(msg.text())) consoleErrors.push(msg.text()); });
    page.on('pageerror', err => consoleErrors.push(String(err)));
    const bridge = `<script>
      window.store = new Map(${JSON.stringify(Object.entries(storage))});
      window.resources = ${JSON.stringify(resources)};
      window.requests = [];
      window.__nappletBridge = { postMessage(raw) {
        const m = JSON.parse(raw);
        window.requests.push({ type: m.type, key: m.key, url: m.url });
        let reply;
        if (${deny}) reply = { type: m.type + '.result', id: m.id, ok: false, error: 'denied', reason: 'Denied by the user' };
        else if (m.type === 'storage.keys') reply = { type: 'storage.keys.result', id: m.id, keys: [...window.store.keys()] };
        else if (m.type === 'storage.get') reply = { type: 'storage.get.result', id: m.id, value: window.store.get(m.key) ?? null };
        else if (m.type === 'storage.set') { window.store.set(m.key, m.value); reply = { type: 'storage.set.result', id: m.id }; }
        else if (m.type === 'storage.remove') { window.store.delete(m.key); reply = { type: 'storage.remove.result', id: m.id }; }
        else if (m.type === 'resource.bytes' && window.resources[m.url]) reply = { type: 'resource.bytes.result', id: m.id, ...window.resources[m.url] };
        else if (m.type === 'resource.bytes') reply = { type: 'resource.bytes.result', id: m.id, ok: false, error: 'not-found', reason: 'No such resource' };
        else reply = { type: m.type + '.result', id: m.id, ok: false, error: 'unsupported' };
        setTimeout(() => this.onmessage({ data: JSON.stringify(reply) }), 5);
      } };
    <\/script>`;
    // The Android host's bootstrap: base64 in the shell, decoded into the opaque srcdoc child.
    const encoded = injectPrelude(artifact, domains).toString('base64');
    const served = shell.replace('<head>', '<head>' + bridge).replace('__APP_SANDBOX__', 'allow-scripts').replace('__APP_BOOTSTRAP__',
      `var b=atob('${encoded}'),u=new Uint8Array(b.length);for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);iframe.srcdoc=new TextDecoder('utf-8').decode(u);`);
    await page.route('https://napplet.local/**', route => route.fulfill({ contentType: 'text/html', headers: { 'Content-Security-Policy': shellCsp }, body: served }));
    await page.goto('https://napplet.local/__shell__');
    await page.waitForFunction(() => document.querySelector('iframe')?.contentWindow != null);
    const frame = page.frames().find(f => f !== page.mainFrame());
    await frame.waitForFunction(() => window.doomStatus && window.doomStatus.phase !== 'loading');
    assert.equal(await frame.evaluate(() => window.doomStatus.phase), 'ready');
    const state = () => frame.evaluate(() => ({ ...window.doomStatus, files: window.doomStatus.files(), mods: window.doomStatus.mods() }));
    const key = async (name, wait = 250) => { await page.keyboard.press(name); await page.waitForTimeout(wait); };
    const pixels = () => frame.evaluate(() => {
      const c = document.getElementById('canvas');
      return Array.from(c.getContext('2d').getImageData(0, 0, c.width, c.height).data.filter((_, i) => i % 4 !== 3 && i % 64 === 0));
    });
    const start = async () => {
      await frame.click('#play');
      await frame.waitForFunction(() => window.doomStatus.frames > 20);
    };
    const newGame = async () => {
      // Title loop -> main menu -> New Game -> episode 1 -> skill 3 -> the melt into E1M1.
      await key('Escape', 400);
      await key('Enter', 400);
      await key('Enter', 400);
      await key('Enter', 2500);
    };
    await run({ page, frame, state, key, pixels, start, newGame, consoleErrors });
  } finally { await browser.close(); }
}

const changed = (a, b) => a.filter((v, i) => Math.abs(v - b[i]) > 8).length;

test('boots under the napplet CSP, renders the title loop, plays sound effects and music', async () => {
  await play(async ({ page, frame, state, pixels, start, consoleErrors }) => {
    await start();
    await frame.waitForFunction(() => window.doomStatus.songs > 0 && window.doomStatus.notes > 20);
    const s = await state();
    assert.equal(s.phase, 'running');
    assert.ok(s.tics > 0);
    assert.ok(new Set(await pixels()).size > 16, 'the title screen is not a blank frame');
    await page.screenshot({ path: new URL('title.png', screens).pathname });
    assert.deepEqual(consoleErrors, []);
  });
});

test('the keyboard starts a game, and the view moves when the player turns', async () => {
  await play(async ({ page, frame, state, key, pixels, start, newGame, consoleErrors }) => {
    await start();
    await newGame();
    await page.screenshot({ path: new URL('e1m1.png', screens).pathname });
    const before = await pixels();
    await page.keyboard.down('ArrowLeft');
    await page.waitForTimeout(600);
    await page.keyboard.up('ArrowLeft');
    await page.waitForTimeout(200);
    assert.ok(changed(before, await pixels()) > 200, 'turning redraws the 3D view');
    await page.keyboard.down('Control');
    await page.waitForTimeout(300);
    await page.keyboard.up('Control');
    await frame.waitForFunction(() => window.doomStatus.sounds > 0);
    await page.screenshot({ path: new URL('turned-and-fired.png', screens).pathname });
    assert.equal((await state()).phase, 'running');
    assert.deepEqual(consoleErrors, []);
  });
});

test('one finger slides around the pad: directions change without lifting, corners are diagonals, the middle is empty', async () => {
  await play(async ({ page, frame, start, newGame, pixels, consoleErrors }) => {
    await start();
    await newGame();
    await frame.evaluate(() => document.body.classList.add('touch'));
    await page.waitForTimeout(300);
    const box = await frame.locator('.pad').boundingBox();
    const cell = (col, row) => [box.x + box.width * (col + 0.5) / 3, box.y + box.height * (row + 0.5) / 3];
    const held = () => frame.evaluate(() => [...document.querySelectorAll('.pad button.down')].map(b => b.textContent).sort().join(''));
    const slideTo = async (col, row) => { await page.mouse.move(...cell(col, row), { steps: 4 }); await page.waitForTimeout(150); };

    await page.mouse.move(...cell(1, 0));
    await page.mouse.down();
    await page.waitForTimeout(150);
    assert.equal(await held(), '\u25B2', 'forward');
    const before = await pixels();
    await slideTo(2, 0);
    assert.equal(await held(), '\u25B2\u25B6', 'the corner moves forward and turns');
    await slideTo(2, 1);
    assert.equal(await held(), '\u25B6', 'turning only, the same finger');
    await page.waitForTimeout(400);
    assert.ok(changed(before, await pixels()) > 200, 'the view turned while the finger slid');
    await slideTo(1, 1);
    assert.equal(await held(), '', 'sliding through the middle stops, and never presses USE');
    // Off the pad, down past its bottom-left corner, still on the screen.
    await page.mouse.move(box.x - 4, box.y + box.height + 4, { steps: 4 });
    await page.waitForTimeout(150);
    assert.equal(await held(), '\u25BC\u25C0', 'past the corner keeps the nearest directions');
    await page.mouse.up();
    await page.waitForTimeout(150);
    assert.equal(await held(), '', 'lifting the finger lets go');

    // The pad's middle presses nothing, even for a finger that lands there: USE is beside FIRE.
    await page.mouse.move(...cell(1, 1));
    await page.mouse.down();
    await page.waitForTimeout(150);
    assert.equal(await held(), '');
    await page.mouse.up();
    const use = await frame.locator('.actions button', { hasText: 'USE' }).boundingBox();
    await page.mouse.move(use.x + use.width / 2, use.y + use.height / 2);
    await page.mouse.down();
    await page.waitForTimeout(150);
    assert.equal(await frame.evaluate(() => [...document.querySelectorAll('.actions button.down')].map(b => b.textContent).join('')), 'USE');
    await page.mouse.up();

    // A touch on the controls has its default cancelled, so Android's WebView never sees a long
    // press (it buzzes for one); the pointer events that drive the controls still fire.
    const cancelled = await frame.evaluate(() => {
      const target = document.querySelector('.pad button');
      const touch = new Touch({ identifier: 1, target, clientX: 1, clientY: 1 });
      const e = new TouchEvent('touchstart', { touches: [touch], targetTouches: [touch], changedTouches: [touch], bubbles: true, cancelable: true });
      target.dispatchEvent(e);
      return e.defaultPrevented;
    });
    assert.equal(cancelled, true, 'a touch on the controls starts no long press');
    assert.equal((await frame.evaluate(() => window.doomStatus.phase)), 'running');
    assert.deepEqual(consoleErrors, []);
  });
});

test('a save game lands in napplet storage and loads back on the next launch', async () => {
  let saved;
  await play(async ({ page, frame, key, start, newGame }) => {
    await start();
    await newGame();
    await key('F2', 500);        // save menu
    await key('Enter', 300);     // slot 1 -> edit its description
    await key('a', 150);
    await key('Enter', 1500);    // save; the page persists it after the engine closes the file
    await page.waitForFunction(() => [...window.store.keys()].some(k => k.endsWith('.dsg')));
    saved = Object.fromEntries(await page.evaluate(() => [...window.store.entries()]));
  });
  const keys = Object.keys(saved);
  assert.ok(keys.some(k => /^fs:\/.*\/doomsav0\.dsg$/.test(k)), `saved keys: ${keys}`);
  const bytes = Object.values(saved).reduce((n, v) => n + v.length, 0);
  assert.ok(bytes < 512 * 1024, `fits the 512 KiB napplet storage quota (${bytes} bytes)`);

  await play(async ({ page, frame, state, key, start }) => {
    await start();
    assert.ok((await state()).files.some(f => f.endsWith('doomsav0.dsg')), 'the save is restored into the file system');
    await key('F3', 500);        // load menu lists the restored slot
    await key('Enter', 2500);    // load it
    await page.screenshot({ path: new URL('loaded.png', screens).pathname });
    assert.equal((await state()).phase, 'running');
    const types = await page.evaluate(() => window.requests.map(r => r.type));
    assert.ok(types.includes('storage.keys') && types.includes('storage.get'));
  }, { storage: saved });
});

test('denied storage leaves the game playable', async () => {
  await play(async ({ frame, state, start, newGame, pixels }) => {
    await start();
    await newGame();
    const s = await state();
    assert.equal(s.phase, 'running');
    assert.ok(new Set(await pixels()).size > 16);
    assert.match(await frame.evaluate(() => document.getElementById('toast').textContent), /Saves last until you close the game/);
  }, { deny: true });
});

test('without the storage domain the game still runs (no window.napplet.storage)', async () => {
  await play(async ({ frame, start }) => {
    assert.equal(await frame.evaluate(() => typeof window.napplet?.storage), 'undefined');
    await start();
    assert.equal(await frame.evaluate(() => window.doomStatus.phase), 'running');
  }, { domains: [] });
});

// ---- Mods ----------------------------------------------------------------------------------

// A PWAD of [lumps] ({ name, data }), in the WAD layout: header, lump data, directory.
function pwad(lumps) {
  const header = Buffer.alloc(12);
  const directory = Buffer.alloc(16 * lumps.length);
  let offset = 12;
  lumps.forEach(({ name, data }, i) => {
    directory.writeInt32LE(offset, i * 16);
    directory.writeInt32LE(data.length, i * 16 + 4);
    directory.write(name, i * 16 + 8, 'ascii');
    offset += data.length;
  });
  header.write('PWAD', 0, 'ascii');
  header.writeInt32LE(lumps.length, 4);
  header.writeInt32LE(offset, 8);
  return Buffer.concat([header, ...lumps.map(l => l.data), directory]);
}

// A 320x200 Doom picture of one palette colour: each column is a single 200-pixel post.
function solidPicture(index) {
  const width = 320, height = 200;
  const column = Buffer.concat([Buffer.from([0, height, 0]), Buffer.alloc(height, index), Buffer.from([0, 0xff])]);
  const header = Buffer.alloc(8 + 4 * width);
  header.writeUInt16LE(width, 0);
  header.writeUInt16LE(height, 2);
  for (let x = 0; x < width; x++) header.writeUInt32LE(header.length + x * column.length, 8 + 4 * x);
  return Buffer.concat([header, ...Array(width).fill(column)]);
}

// The game's palette entry closest to pure blue, which the Freedoom title does not use.
async function bluestPaletteEntry() {
  const wad = await readFile(process.env.DOOM_WAD || new URL('build/freedoom-0.13.0/freedoom1.wad', import.meta.url));
  const count = wad.readInt32LE(4), dir = wad.readInt32LE(8);
  for (let i = 0; i < count; i++) {
    if (wad.toString('ascii', dir + i * 16 + 8, dir + i * 16 + 16).replace(/\0.*$/, '') !== 'PLAYPAL') continue;
    const at = wad.readInt32LE(dir + i * 16);
    let best = 0, bestScore = -1e9;
    for (let c = 0; c < 256; c++) {
      const [r, g, b] = [wad[at + c * 3], wad[at + c * 3 + 1], wad[at + c * 3 + 2]];
      if (b - r - g > bestScore) { bestScore = b - r - g; best = c; }
    }
    return { index: best, rgb: [wad[at + best * 3], wad[at + best * 3 + 1], wad[at + best * 3 + 2]] };
  }
  throw new Error('no PLAYPAL');
}

const asResource = (bytes, mime = 'application/octet-stream') => ({ blob: Buffer.from(bytes).toString('base64'), mime });

test('a mod linked from a note loads over the game, persists, and keeps its own saves', async () => {
  const blue = await bluestPaletteEntry();
  const mod = pwad([{ name: 'TITLEPIC', data: solidPicture(blue.index) }]);
  const hash = createHash('sha256').update(mod).digest('hex');
  const note = { kind: 1, content: `My blue title screen blossom:${hash}.wad`, tags: [] };
  const lying = { kind: 1063, content: 'Not what it says', tags: [['url', `blossom:${hash}.wad`], ['x', 'f'.repeat(64)]] };
  const resources = {
    'nostr:nevent1bluetitle': asResource(JSON.stringify(note), 'application/json'),
    'nostr:nevent1lying': asResource(JSON.stringify(lying), 'application/json'),
    [`blossom:${hash}.wad`]: asResource(mod),
  };
  // How much of the screen is the mod's blue.
  const blueShare = frame => frame.evaluate(rgb => {
    const c = document.getElementById('canvas');
    const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
    let hits = 0;
    for (let i = 0; i < d.length; i += 4) if (Math.abs(d[i] - rgb[0]) + Math.abs(d[i + 1] - rgb[1]) + Math.abs(d[i + 2] - rgb[2]) < 24) hits++;
    return hits / (d.length / 4);
  }, blue.rgb);

  let saved;
  await play(async ({ page, frame, state, key, newGame }) => {
    await frame.click('#mods summary');
    await frame.fill('#mod-ref', 'nevent1lying');
    await frame.click('#mod-add');
    await frame.waitForFunction(() => window.doomStatus.mods()[0]?.state === 'failed');
    assert.match((await state()).mods[0].error, /Hash mismatch/);
    await frame.click('#mod-list button');

    await frame.fill('#mod-ref', 'nostr:nevent1bluetitle');
    await frame.click('#mod-add');
    await frame.waitForFunction(() => window.doomStatus.mods()[0]?.state === 'ready');
    assert.equal((await state()).mods[0].name, 'My blue title screen');
    await frame.click('#play');
    await frame.waitForFunction(() => window.doomStatus.frames > 40);
    await page.waitForTimeout(1500);
    assert.ok(await blueShare(frame) > 0.9, 'the title is the mod’s picture');
    await page.screenshot({ path: new URL('mod-title.png', screens).pathname });

    await newGame();
    await key('F2', 500);
    await key('Enter', 300);
    await key('m', 150);
    await key('Enter', 1500);
    await page.waitForFunction(() => [...window.store.keys()].some(k => k.endsWith('.dsg')));
    saved = Object.fromEntries(await page.evaluate(() => [...window.store.entries()]));
  }, { domains: ['storage', 'resource'], resources });

  assert.deepEqual(JSON.parse(saved.mods), [{ ref: 'nostr:nevent1bluetitle', name: 'My blue title screen' }]);
  const save = Object.keys(saved).find(k => k.endsWith('doomsav0.dsg'));
  assert.match(save, /^fs:[0-9a-f]{8}:\//, 'a modded save lives in its own namespace');

  // The next launch fetches the mod again by its link, and its save shows up only with it.
  await play(async ({ page, frame, state, key }) => {
    await frame.waitForFunction(() => window.doomStatus.mods()[0]?.state === 'ready');
    await frame.click('#play');
    await frame.waitForFunction(() => window.doomStatus.frames > 40);
    await page.waitForTimeout(1500);
    assert.ok(await blueShare(frame) > 0.9);
    assert.ok((await state()).files.some(f => f.endsWith('doomsav0.dsg')));
  }, { domains: ['storage', 'resource'], resources, storage: saved });

  await play(async ({ frame, state }) => {
    await frame.click('#mod-list button');   // remove the mod: back to the plain game
    await frame.click('#play');
    await frame.waitForFunction(() => window.doomStatus.frames > 20);
    assert.ok(!(await state()).files.some(f => f.endsWith('doomsav0.dsg')), 'the modded save stays with the mod');
  }, { domains: ['storage', 'resource'], resources, storage: saved });
});
