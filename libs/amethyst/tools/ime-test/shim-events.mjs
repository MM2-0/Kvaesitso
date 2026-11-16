// Regression test for the embedded-WebView IME relay's page→host protocol.
//
// Loads the REAL shim (commonsUI/src/commonMain/composeResources/files/napplet/shim.js) into real Chromium
// with the embedded-surface flags set, drives genuine focus/tap/blur gestures, and asserts the `ime.*`
// envelopes it emits. This is the only honest automated coverage for this code: the half worth protecting
// is the page↔host contract (real browser focus/gesture behavior), which no JVM unit test of the host-side
// parser (`parseImeEvent`, kotlinx.serialization) can exercise.
//
//   cd tools/ime-test && npm i playwright-core && node shim-events.mjs
//
// Exits 0 if every expectation holds, 1 otherwise. Override the browser with CHROMIUM_PATH, and the shim
// under test with argv[2] (useful for diffing a candidate against the committed one).

import { chromium } from 'playwright-core'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const SHIM = process.argv[2] ?? resolve(HERE, '../../commonsUI/src/commonMain/composeResources/files/napplet/shim.js')
const CHROMIUM = process.env.CHROMIUM_PATH ?? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'

const HTML = `<!doctype html><meta charset=utf-8><title>ime</title>
<body style="margin:0;font:16px sans-serif">
<p id="para">plain page text, not editable</p>
<input id="inp" value="hello world" style="width:90%;height:40px">
<input id="ro" value="read only field" readonly style="width:90%;height:40px">
<div id="ce" contenteditable="true" style="border:1px solid #000;padding:8px">
  <span id="cespan">editable span text</span>
</div>
<textarea id="chat" rows="1"></textarea>
<textarea id="notes" rows="3"></textarea>
<form id="pf"><input id="n1"><input id="n2"><textarea id="n3"></textarea><input id="n4"></form>
<div id="search" contenteditable="true" role="combobox" enterkeyhint="search" style="border:1px solid #000;padding:8px"></div>
<script>
  // A rich search box (Brainstorm's home): a contenteditable that takes Enter from beforeinput, not keydown.
  window.searches = []
  document.getElementById('search').addEventListener('beforeinput', function (e) {
    if (e.inputType === 'insertParagraph' || e.inputType === 'insertLineBreak') { e.preventDefault(); window.searches.push(this.textContent) }
  })
  // A chat composer the way chat apps write one: send on a keydown Enter, cancel the key, then clear the
  // field by assigning its value (what a framework's state reset does), which fires no input event.
  window.sentMsgs = []
  document.getElementById('chat').addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); window.sentMsgs.push(this.value); this.value = '' }
  })
</script>
</body>`

const browser = await chromium.launch({ executablePath: CHROMIUM, args: ['--no-sandbox'] })
const context = await browser.newContext()

// Stand in for the native bridge the `:napplet` process installs: collect what the page sends, and keep the
// reply channel so host→page ops (`ime.resync`) can be delivered exactly as the host delivers them.
await context.addInitScript(() => {
  window.__sent = []
  window.__nappletDirectBridge = true
  window.__nappletImeProxy = true
  window.__nappletBridge = {
    postMessage(s) { window.__sent.push(s) },
    set onmessage(fn) { window.__imeIn = fn },
    get onmessage() { return window.__imeIn },
  }
})
await context.addInitScript({ content: readFileSync(SHIM, 'utf8') })
await context.route('https://ime.test/**', (route) => route.fulfill({ contentType: 'text/html', body: HTML }))
const page = await context.newPage()
await page.goto('https://ime.test/')

const drain = async () => {
  const raw = await page.evaluate(() => { const s = window.__sent.slice(); window.__sent.length = 0; return s })
  return raw.map((s) => JSON.parse(s)).filter((m) => (m.type || '').startsWith('ime.'))
}

const failures = []
const results = []

// `expect` is a predicate over the messages one gesture produced, described in words for the report.
const step = async (name, gesture, expectation) => {
  await gesture()
  await page.waitForTimeout(150)
  const msgs = await drain()
  const problem = expectation(msgs)
  const types = msgs.map((m) => m.type).join(', ') || '(nothing)'
  results.push([name, types, problem])
  if (problem) failures.push(`${name}: ${problem}\n      got: ${types}`)
}

const has = (msgs, type) => msgs.some((m) => m.type === type)
const find = (msgs, type) => msgs.find((m) => m.type === type)

await step(
  'tap an unfocused field announces it and asks for the keyboard',
  () => page.click('#inp'),
  (m) => {
    const focus = find(m, 'ime.focus')
    if (!focus) return 'no ime.focus'
    if (focus.text !== 'hello world') return `ime.focus carried text "${focus.text}"`
    if (focus.readOnly !== false) return 'ime.focus said readOnly on an editable field'
    return has(m, 'ime.wantkb') ? null : 'no ime.wantkb doorbell'
  },
)

// The regression this suite exists for: a tap on a field that never blurred fires no focus event, so before
// `ime.wantkb` existed the host had no signal at all and the keyboard could not be brought back.
await step(
  'tap the SAME already-focused field still rings the doorbell',
  () => page.click('#inp'),
  (m) => {
    if (!has(m, 'ime.wantkb')) return 'no ime.wantkb — the keyboard could never come back'
    if (has(m, 'ime.focus')) return 'unexpected ime.focus (page focus never moved)'
    return null
  },
)

// The doorbell must stay payload-free: it fires on every tap in a field, so attaching the editing state
// would put the whole field text on the wire per tap.
await step(
  'the doorbell carries no payload',
  () => page.click('#inp'),
  (m) => {
    const kb = find(m, 'ime.wantkb')
    if (!kb) return 'no ime.wantkb'
    const extra = Object.keys(kb).filter((k) => k !== 'type' && k !== 'id')
    return extra.length ? `ime.wantkb carried ${extra.join(', ')}` : null
  },
)

await step(
  'a host resync is answered with the focused field state',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.resync' }) })),
  (m) => {
    const re = find(m, 'ime.refocus')
    if (!re) return 'no ime.refocus'
    if (re.text !== 'hello world') return `ime.refocus carried text "${re.text}"`
    if (re.geom !== undefined) return 'ime.refocus carried geometry (forces a synchronous layout per send)'
    return null
  },
)

await step(
  'tapping off the field blurs it',
  () => page.click('#para'),
  (m) => (has(m, 'ime.blur') ? null : 'no ime.blur'),
)

await step(
  'a resync with nothing focused answers nothing',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.resync' }) })),
  (m) => (m.length ? 'answered a resync with no focused field' : null),
)

await step(
  'a readonly field announces itself as readonly',
  () => page.click('#ro'),
  (m) => {
    const focus = find(m, 'ime.focus')
    if (!focus) return 'no ime.focus'
    return focus.readOnly === true ? null : 'ime.focus did not report readOnly'
  },
)

await step(
  'readonly survives the resync round trip',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.resync' }) })),
  (m) => {
    const re = find(m, 'ime.refocus')
    if (!re) return 'no ime.refocus'
    return re.readOnly === true ? null : 'ime.refocus dropped readOnly — a restore would raise a keyboard'
  },
)

// contenteditable taps land on a child node, so the doorbell has to test containment, not equality.
await step(
  'a tap inside an already-focused contenteditable rings the doorbell',
  async () => {
    await page.click('#cespan')
    await page.waitForTimeout(150)
    await drain()
    await page.click('#cespan')
  },
  (m) => (has(m, 'ime.wantkb') ? null : 'no ime.wantkb from inside a contenteditable'),
)

// Host → page: the mirror's edit, then the keyboard's Enter, exactly as RemoteImeView ships them.
const hostSet = (text) => page.evaluate((t) => window.__imeIn({ data: JSON.stringify({ type: 'ime.set', text: t, selStart: t.length, selEnd: t.length, composingStart: -1, composingEnd: -1 }) }), text)
const hostEnter = () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.action' }) }))

await step(
  'Enter in a chat composer sends it instead of adding a line break',
  async () => {
    await page.click('#chat')
    await page.waitForTimeout(150)
    await drain()
    await hostSet('hello')
    await page.waitForTimeout(50)
    await drain()
    await hostEnter()
  },
  () => null,
)
{
  const [sent, value] = await page.evaluate(() => [window.sentMsgs.slice(), document.getElementById('chat').value])
  const problem = sent.length !== 1 || sent[0] !== 'hello' ? `page received ${JSON.stringify(sent)} (no keydown Enter reached it)` : value !== '' ? `field kept ${JSON.stringify(value)}` : null
  results.push(['  ...the page got exactly "hello" and cleared itself', JSON.stringify(sent), problem])
  if (problem) failures.push(`chat Enter: ${problem}`)
}

// The page cleared the field by assignment: the host must hear the empty text, or its mirror keeps "hello"
// and the next keystroke writes it all back.
await step(
  'a field the page clears by assigning value is reported to the host',
  () => page.evaluate(() => { const t = document.getElementById('chat'); t.value = 'draft'; return new Promise((r) => setTimeout(r, 20)) }).then(() => drain()).then(() => page.evaluate(() => { document.getElementById('chat').value = '' })),
  (m) => {
    const st = m.filter((x) => x.type === 'ime.state').pop()
    if (!st) return 'no ime.state — the host never learns the page cleared the field'
    return st.text === '' ? null : `ime.state carried "${st.text}"`
  },
)

await step(
  'our own edits are not echoed back as page writes',
  () => hostSet('typed'),
  (m) => (has(m, 'ime.state') ? 'ime.state echoed the host its own edit' : null),
)

await step(
  'Enter in a plain textarea inserts a line break and reports it',
  async () => {
    await page.click('#notes')
    await page.waitForTimeout(150)
    await drain()
    await hostSet('one')
    await page.waitForTimeout(50)
    await drain()
    await hostEnter()
  },
  (m) => {
    const st = m.filter((x) => x.type === 'ime.state').pop()
    if (!st) return 'no ime.state — the host mirror never learns about the line break'
    return st.text === 'one\n' ? null : `ime.state carried ${JSON.stringify(st.text)}`
  },
)

await step(
  'Enter in a contenteditable search box searches instead of adding a line break',
  async () => {
    await page.click('#search')
    await page.waitForTimeout(150)
    await drain()
    await hostSet('Vitor')
    await page.waitForTimeout(50)
    await drain()
    await hostEnter()
  },
  () => null,
)
{
  const [searches, text] = await page.evaluate(() => [window.searches.slice(), document.getElementById('search').textContent])
  const problem = searches.length !== 1 || searches[0] !== 'Vitor' ? `page searched ${JSON.stringify(searches)} (no beforeinput insertParagraph reached it)` : text !== 'Vitor' ? `field became ${JSON.stringify(text)}` : null
  results.push(['  ...the page searched "Vitor" and the field kept no line break', JSON.stringify(searches), problem])
  if (problem) failures.push(`search Enter: ${problem}`)
}

await step(
  'a form field with another after it offers Next',
  () => page.click('#n1'),
  (m) => { const f = find(m, 'ime.focus'); return !f ? 'no ime.focus' : f.hasNext === true ? null : `hasNext=${f.hasNext}` },
)
await step(
  'Next moves focus to the following field',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.next' }) })),
  (m) => { const f = find(m, 'ime.focus'); return !f ? 'focus did not move' : null },
)
{
  const id = await page.evaluate(() => document.activeElement.id)
  results.push(['  ...to the second field', id, id === 'n2' ? null : `focused ${id}`])
  if (id !== 'n2') failures.push(`Next: focused ${id}`)
}
await step(
  'a textarea never offers Next (Enter is a line break)',
  () => page.click('#n3'),
  (m) => { const f = find(m, 'ime.focus'); return !f ? 'no ime.focus' : f.hasNext ? 'hasNext on a textarea' : null },
)
await step(
  'the last field in the form offers Go, not Next',
  () => page.click('#n4'),
  (m) => { const f = find(m, 'ime.focus'); return !f ? 'no ime.focus' : f.hasNext ? 'hasNext on the last field' : null },
)
await step(
  'Previous moves focus back',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.prev' }) })),
  () => null,
)
{
  const id = await page.evaluate(() => document.activeElement.id)
  results.push(['  ...to the textarea before it', id, id === 'n3' ? null : `focused ${id}`])
  if (id !== 'n3') failures.push(`Previous: focused ${id}`)
}

console.log(`\nshim: ${SHIM}\n`)
for (const [name, types, problem] of results) {
  console.log(`  ${problem ? 'FAIL' : 'ok  '}  ${name}\n          ${types}`)
}
if (failures.length) {
  console.log(`\n${failures.length} failure(s):\n`)
  failures.forEach((f) => console.log(`  - ${f}`))
}
await browser.close()
process.exit(failures.length ? 1 : 0)
