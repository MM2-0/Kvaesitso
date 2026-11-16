// Per-keystroke cost of the embedded IME relay's page side: replays 200 host `ime.set` keystrokes into the REAL
// shim on long fields and reports the time each takes to apply (p50/p95/max, ms). A long contenteditable is the
// case to watch: every keystroke maps offsets through a walk of the field.
//
//   cd tools/ime-test && npm i playwright-core && node shim-perf.mjs      (SHIM=<path> to compare another shim)
import { chromium } from 'playwright-core'
import { readFileSync } from 'node:fs'
const SHIM = process.env.SHIM ?? '../../commonsUI/src/commonMain/composeResources/files/napplet/shim.js'
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH })
const cases = {
  'input, short': ['<input id="f">', 0],
  'textarea 20k chars': ['<textarea id="f"></textarea>', 20000],
  'contenteditable 1000 lines': ['<div id="f" contenteditable="true"></div>', -1000],
  'contenteditable 1000 paragraphs': ['<div id="f" contenteditable="true"></div>', -1001],
  'page rewrites value on input': ['<input id="f"><script>var f=document.getElementById("f");f.addEventListener("input",function(){f.value=f.value})</script>', 0],
}
for (const [name, [html, fill]] of Object.entries(cases)) {
  const ctx = await browser.newContext()
  await ctx.addInitScript(() => { window.__sent = []; window.__nappletDirectBridge = true; window.__nappletImeProxy = true; window.__nappletBridge = { postMessage(s) { window.__sent.push(s) }, set onmessage(fn) { window.__imeIn = fn }, get onmessage() { return window.__imeIn } } })
  await ctx.addInitScript({ content: readFileSync(SHIM, 'utf8') })
  await ctx.route('https://ime.test/**', (r) => r.fulfill({ contentType: 'text/html', body: `<body>${html}</body>` }))
  const p = await ctx.newPage(); await p.goto('https://ime.test/')
  const r = await p.evaluate(async (fill) => {
    const f = document.getElementById('f')
    if (fill > 0) { f.value = 'lorem ipsum dolor sit amet '.repeat(fill / 27 + 1).slice(0, fill) }
    if (fill === -1000) f.innerHTML = Array.from({ length: 1000 }, (_, i) => 'line number ' + i).join('<br>')
    if (fill === -1001) f.innerHTML = Array.from({ length: 1000 }, (_, i) => '<div>line number ' + i + '</div>').join('')
    f.focus()
    if (!f.isContentEditable) f.setSelectionRange(f.value.length, f.value.length)
    else { const s = getSelection(); s.selectAllChildren(f); s.collapseToEnd() }
    await new Promise((r) => setTimeout(r, 50))
    const focusMsg = window.__sent.map((x) => JSON.parse(x)).filter((m) => m.type === 'ime.focus').pop()
    window.__sent.length = 0
    const handle = window.__nappletImeHandle
    // host text as the shim reported it on focus
    let text = (f.isContentEditable ? null : f.value)
    if (text === null) { const st = window.__sent; text = null }
    const times = []
    let host = f.isContentEditable ? null : f.value
    for (let i = 0; i < 200; i++) {
      if (host === null) host = focusMsg.text
      host = host + 'x'
      const msg = { type: 'ime.set', text: host, selStart: host.length, selEnd: host.length, composingStart: -1, composingEnd: -1 }
      const t0 = performance.now(); handle(msg); await Promise.resolve(); times.push(performance.now() - t0)
    }
    times.sort((a, b) => a - b)
    return { p50: times[100].toFixed(3), p95: times[190].toFixed(3), max: times[199].toFixed(3), reports: window.__sent.filter((s) => s.includes('ime.state')).length }
  }, fill)
  console.log(name.padEnd(32), JSON.stringify(r))
  await ctx.close()
}
await browser.close()
