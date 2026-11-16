// Run: PLAYWRIGHT_MODULE=/path/to/playwright-core/index.mjs node --test tools/napplet-test/protocol.test.mjs
// Uses an installed Chrome; no account, relay, or publication required.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const root = new URL('../../', import.meta.url);
const shim = await readFile(new URL('commonsUI/src/commonMain/composeResources/files/napplet/shim.js', root), 'utf8');
const shell = await readFile(new URL('commonsUI/src/commonMain/composeResources/files/napplet/shell.html', root), 'utf8');
const contract = await readFile(new URL('commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/napplet/NappletWebContract.kt', root), 'utf8');
const csp = contract.match(/const val APP_CSP: String =([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('');
const shellCsp = contract.match(/fun shellCsp[^=]*=([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('').replace('$frameSource', "'self'");

async function host(run, domains = ['theme', 'resource']) {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(5000);
    const authored = `<script>window.bootDomains=Object.keys(window.napplet);window.themeChanges=[];window.handle=window.napplet.theme?.onChanged(t=>window.themeChanges.push(t));
window.security=(async()=>{
  let storageDenied=false,evalDenied=false,networkDenied=false;
  try {localStorage.getItem('x')} catch {storageDenied=true}
  try {new Function('return 1')()} catch {evalDenied=true}
  try {await fetch('https://example.com')} catch {networkDenied=true}
  await WebAssembly.compile(new Uint8Array([0,97,115,109,1,0,0,0]));
  return {domains:window.bootDomains,signer:typeof window.nostr,storageDenied,evalDenied,networkDenied};
})();<\/script>`;
    const html = `<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="${csp}"><script>window.__nappletDomains=${JSON.stringify(domains)};${shim}<\/script>${authored}`;
    // The native bridge is a test double; the production shell and injected shim run unchanged.
    const bridge = `<script>window.requests=[];window.__nappletBridge={postMessage(raw){window.requests.push(JSON.parse(raw));}};<\/script>`;
    const served = shell.replace('<head>', '<head>' + bridge).replace('__APP_SANDBOX__', 'allow-scripts').replace('__APP_BOOTSTRAP__', `iframe.srcdoc=new TextDecoder().decode(Uint8Array.from(atob('${Buffer.from(html).toString('base64')}'),c=>c.charCodeAt(0)));`);
    await page.route('https://napplet.local/**', route => route.fulfill({ contentType: 'text/html', headers: {'Content-Security-Policy': shellCsp}, body: served }));
    await page.goto('https://napplet.local/__shell__');
    await page.waitForFunction(() => document.querySelector('iframe')?.contentWindow != null);
    const frame = page.frames().find(f => f !== page.mainFrame());
    await frame.waitForFunction(() => window.bootDomains);
    const reply = msg => page.evaluate(msg => window.__nappletBridge.onmessage({data: JSON.stringify(msg)}), msg);
    await run({page, frame, reply});
  } finally { await browser.close(); }
}

test('srcdoc injection precedes code; opaque storage, no signer/network/eval, WebAssembly works', async () => {
  await host(async ({frame}) => {
    // Read the result of authored code: CDP evaluation itself bypasses unsafe-eval CSP checks.
    const state = await frame.evaluate(() => window.security);
    assert.deepEqual(state, {domains:['theme','resource'],signer:'undefined',storageDenied:true,evalDenied:true,networkDenied:true});
  });
});

test('theme updates need no handshake/get; closing a listener stops local delivery', async () => {
  await host(async ({page, frame, reply}) => {
    assert.equal(await page.evaluate(() => window.requests.length), 0);
    const theme = {colors:{background:'#000000',text:'#ffffff',primary:'#123456'}};
    await reply({type:'theme.changed', theme});
    await frame.waitForFunction(() => window.themeChanges.length === 1);
    assert.deepEqual(await frame.evaluate(() => window.themeChanges[0]), theme);
    await frame.evaluate(() => window.handle.close());
    await reply({type:'theme.changed', theme});
    // A same-frame synthetic event must not pass the parent source check.
    await frame.evaluate(theme => window.dispatchEvent(new MessageEvent('message', {data:{type:'theme.changed',theme},source:window})), theme);
    assert.equal(await frame.evaluate(() => window.themeChanges.length), 1);
  });
});

test('resource binding decodes canonical base64 into Blob and rejects nonzero pad bits', async () => {
  await host(async ({page, frame, reply}) => {
    await frame.evaluate(() => { window.bytesResult = window.napplet.resource.bytes('data:application/octet-stream;base64,AAEC/w==').then(async blob => ({mime:blob.type,bytes:Array.from(new Uint8Array(await blob.arrayBuffer()))})); });
    await page.waitForFunction(() => window.requests.length === 1);
    const request = await page.evaluate(() => window.requests[0]);
    await reply({type:'resource.bytes.result',id:request.id,blob:'AAEC/w==',mime:'application/octet-stream'});
    assert.deepEqual(await frame.evaluate(() => window.bytesResult), {mime:'application/octet-stream',bytes:[0,1,2,255]});
    await frame.evaluate(() => { window.invalid = window.napplet.resource.bytes('bad').then(()=>false,()=>true); });
    await page.waitForFunction(() => window.requests.length === 2);
    const bad = await page.evaluate(() => window.requests[1]);
    await reply({type:'resource.bytes.result',id:bad.id,blob:'AB==',mime:'application/octet-stream'});
    assert.equal(await frame.evaluate(() => window.invalid), true);
  });
});

test('optional unavailable domains remain absent', async () => {
  await host(async ({frame}) => {
    assert.deepEqual(await frame.evaluate(() => Object.keys(window.napplet)), ['resource']);
  }, ['resource']);
});

test('outbox results resolve as NAP-OUTBOX objects, failures included; subscriptions stream and close', async () => {
  await host(async ({page, frame, reply}) => {
    // A publish the relays refused still resolves: the napplet reads `ok` and `error` itself.
    await frame.evaluate(() => { window.published = window.napplet.outbox.publish({kind:30078,content:'save',tags:[['d','slot']]}, {toInboxes:['ab']}); });
    await page.waitForFunction(() => window.requests.length === 1);
    const publish = await page.evaluate(() => window.requests[0]);
    assert.equal(publish.type, 'outbox.publish');
    assert.deepEqual(publish.options, {toInboxes:['ab']});
    await reply({type:'outbox.publish.result',id:publish.id,ok:false,relays:{'wss://a/':false},error:'publish failed'});
    assert.deepEqual(await frame.evaluate(() => window.published), {ok:false,relays:{'wss://a/':false},error:'publish failed'});

    // A query resolves to its events, even when refused.
    await frame.evaluate(() => { window.queried = window.napplet.outbox.query({kinds:[1]}, {timeoutMs:3000}); });
    await page.waitForFunction(() => window.requests.length === 2);
    const query = await page.evaluate(() => window.requests[1]);
    await reply({type:'outbox.query.result',id:query.id,events:[],error:'policy denied'});
    assert.deepEqual(await frame.evaluate(() => window.queried), {events:[],error:'policy denied'});

    // A subscription hands each event to on('event') and its end to on('closed').
    await frame.evaluate(() => { window.got = []; window.sub = window.napplet.outbox.subscribe([{kinds:[1]}]).on('event', r => window.got.push(r.event.id)).on('closed', reason => window.got.push('closed:' + reason)); });
    await page.waitForFunction(() => window.requests.length === 3);
    const subscribe = await page.evaluate(() => window.requests[2]);
    assert.equal(subscribe.type, 'outbox.subscribe');
    await reply({type:'outbox.event',subId:subscribe.subId,result:{event:{id:'e1'}}});
    await reply({type:'outbox.closed',subId:subscribe.subId,reason:'done'});
    await reply({type:'outbox.event',subId:subscribe.subId,result:{event:{id:'late'}}});
    await frame.waitForFunction(() => window.got.length === 2);
    assert.deepEqual(await frame.evaluate(() => window.got), ['e1','closed:done']);
  }, ['outbox']);
});

test('fs moves base64 both ways and rejects with the NAP-FS error code', async () => {
  await host(async ({page, frame, reply}) => {
    // PhotoHop's open: info for the limits, a pick, then ranged reads.
    await frame.evaluate(() => { window.picked = window.napplet.fs.pickFile({permissions:['read'],accept:[{extension:'.png'}]}); });
    await page.waitForFunction(() => window.requests.length === 1);
    const pick = await page.evaluate(() => window.requests[0]);
    assert.equal(pick.type, 'fs.pickFile');
    assert.deepEqual(pick.options, {permissions:['read'],accept:[{extension:'.png'}]});
    const entries = [{path:'/picked/a.png',kind:'file',name:'a.png',permissions:['read'],size:5}];
    await reply({type:'fs.pickFile.result',id:pick.id,result:{entries}});
    assert.deepEqual(await frame.evaluate(() => window.picked), {entries});

    await frame.evaluate(() => { window.read = window.napplet.fs.read('/picked/a.png', {offset:0,length:65536}); });
    await page.waitForFunction(() => window.requests.length === 2);
    const read = await page.evaluate(() => window.requests[1]);
    assert.deepEqual([read.type, read.path, read.options], ['fs.read', '/picked/a.png', {offset:0,length:65536}]);
    await reply({type:'fs.read.result',id:read.id,result:{data:'SGVsbG8=',offset:0,bytesRead:5,eof:true,size:5}});
    assert.equal(await frame.evaluate(async () => atob((await window.read).data)), 'Hello');

    // Bytes given to write are encoded; a string is sent as the base64 it already is.
    await frame.evaluate(() => { window.wrote = window.napplet.fs.write('/picked/out.png', new Uint8Array([72,105]), {mode:'append'}); });
    await page.waitForFunction(() => window.requests.length === 3);
    const write = await page.evaluate(() => window.requests[2]);
    assert.deepEqual([write.type, write.data, write.options], ['fs.write', 'SGk=', {mode:'append'}]);
    await reply({type:'fs.write.result',id:write.id,result:{bytesWritten:2,size:2}});
    assert.deepEqual(await frame.evaluate(() => window.wrote), {bytesWritten:2,size:2});

    // A dismissed picker rejects with 'cancelled', which is how PhotoHop tells a cancel from a failure.
    await frame.evaluate(() => { window.cancelled = window.napplet.fs.pickSaveFile({suggestedName:'x.png'}).then(() => 'resolved', e => e.message); });
    await page.waitForFunction(() => window.requests.length === 4);
    const save = await page.evaluate(() => window.requests[3]);
    await reply({type:'fs.pickSaveFile.result',id:save.id,error:'cancelled'});
    assert.equal(await frame.evaluate(() => window.cancelled), 'cancelled');
  }, ['fs']);
});
