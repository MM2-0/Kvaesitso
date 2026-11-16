// Differential test for the embedded-WebView IME relay: every scenario runs twice in real Chromium —
//
//   native   the field is edited the way Android Chrome edits it from a soft keyboard: committed text via
//            CDP Input.insertText (beforeinput/input, no character keydowns), Backspace/Enter as real keys,
//            composition via Input.imeSetComposition, paste via a real clipboard paste.
//   proxied  the REAL shim with the embedded-surface flags, driven by a model of RemoteImeView: the host
//            holds the text + selection, edits them, and ships `ime.set` / `ime.action` exactly as the app.
//
// and the outcomes are compared: the field's text and caret, and what the page did (sent, searched, submitted,
// refused). A mismatch is an interaction that behaves differently in the embed than in the browser.
//
//   cd tools/ime-test && npm i playwright-core && node shim-parity.mjs [scenario-filter]
//
// Exits 0 when every scenario matches. CHROMIUM_PATH overrides the browser; SHIM the shim under test.

import { chromium } from 'playwright-core'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const SHIM = process.env.SHIM ?? resolve(HERE, '../../commonsUI/src/commonMain/composeResources/files/napplet/shim.js')
const CHROMIUM = process.env.CHROMIUM_PATH ?? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const FILTER = process.argv[2] ?? ''

// ---------------------------------------------------------------------------------------------------------
// Fixtures: the field patterns real pages use. Each defines #f and pushes what the page did to window.__out.
// ---------------------------------------------------------------------------------------------------------

// The value tracker React (and Preact) install on a controlled field: an own `value` property that wraps the
// prototype setter captured at mount, so the framework knows the last value IT wrote.
const CONTROLLED = `
  function controlled(f, transform){
    var proto = Object.getPrototypeOf(f), d = Object.getOwnPropertyDescriptor(proto, 'value'), tracked = f.value
    Object.defineProperty(f, 'value', { configurable: true, get: function(){ return d.get.call(this) }, set: function(v){ tracked = '' + v; d.set.call(this, v) } })
    var state = f.value
    function render(){ if (f.value !== state) f.value = state }
    f.addEventListener('input', function(){ if (f.value === tracked) return; state = transform ? transform(f.value) : f.value; Promise.resolve().then(render) })
    return { set: function(v){ state = v; render() }, get: function(){ return state } }
  }`

const FIXTURES = {
  input: `<input id="f" value="">`,
  textarea: `<textarea id="f"></textarea>`,
  ce: `<div id="f" contenteditable="true" style="white-space:pre-wrap"></div>`,
  ceNormal: `<div id="f" contenteditable="true"></div>`,
  maxlength: `<input id="f" maxlength="5">`,
  number: `<input id="f" type="number">`,
  email: `<input id="f" type="email">`,
  password: `<input id="f" type="password">`,
  // An input mask: digits only, refused in beforeinput.
  mask: `<input id="f"><script>
    document.getElementById('f').addEventListener('beforeinput', function(e){
      if (e.data && /\\D/.test(e.data)) { e.preventDefault(); __out.push('refused ' + e.data) }
    })</script>`,
  // React-controlled input, and one whose state upper-cases what you type.
  react: `<input id="f"><script>${CONTROLLED} window.ctl = controlled(document.getElementById('f'))</script>`,
  reactUpper: `<input id="f"><script>${CONTROLLED} window.ctl = controlled(document.getElementById('f'), function(v){ return v.toUpperCase() })</script>`,
  // Chat composer: send on keydown Enter (not Shift), clear by assignment.
  chat: `<textarea id="f" rows="1"></textarea><script>${CONTROLLED}
    var f = document.getElementById('f'), ctl = controlled(f)
    f.addEventListener('keydown', function(e){ if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); __out.push('sent ' + ctl.get()); ctl.set('') } })</script>`,
  // A form: Enter submits.
  form: `<form id="form"><input id="f" name="q"><button>Go</button></form><script>
    document.getElementById('form').addEventListener('submit', function(e){ e.preventDefault(); __out.push('submit ' + document.getElementById('f').value) })</script>`,
  // A search box that acts on keydown Enter and clears itself.
  searchKeydown: `<input id="f" enterkeyhint="search"><script>
    var f = document.getElementById('f')
    f.addEventListener('keydown', function(e){ if (e.key === 'Enter') { __out.push('search ' + f.value); f.value = '' } })</script>`,
  // An autocomplete combobox: ArrowDown/Enter pick, the pick sets the value programmatically.
  combo: `<input id="f" role="combobox"><script>
    var f = document.getElementById('f')
    f.addEventListener('keydown', function(e){ if (e.key === 'Enter') { e.preventDefault(); f.value = 'Picked: ' + f.value; __out.push('picked') } })</script>`,
  // Brainstorm-style chip editor: "tag:x " becomes an uneditable chip whose visible text differs from its
  // token; Enter searches from beforeinput; Backspace right after a chip removes the whole chip.
  chips: `<div id="f" contenteditable="true" style="white-space:pre-wrap"></div><script>
    var f = document.getElementById('f')
    function value(){ var s = ''; f.childNodes.forEach(function(n){ s += n.nodeType === 3 ? n.data : (n.dataset.token || '') }); return s }
    function caretOffset(){ var sel = getSelection(); if (!sel.rangeCount) return value().length; var r = sel.getRangeAt(0), o = 0
      for (var n of f.childNodes) { if (n === r.startContainer || n.contains(r.startContainer)) return o + (n.nodeType === 3 ? r.startOffset : (n.dataset.token || '').length); o += n.nodeType === 3 ? n.data.length : (n.dataset.token || '').length }
      return o }
    function render(text, caret){
      f.innerHTML = ''
      var re = /tag:(\\w+)(?= )/g, last = 0, m, caretNode = null, caretAt = 0
      while ((m = re.exec(text))) {
        if (m.index > last) f.appendChild(document.createTextNode(text.slice(last, m.index)))
        var chip = document.createElement('span'); chip.contentEditable = 'false'; chip.dataset.token = m[0]; chip.textContent = '#' + m[1]
        f.appendChild(chip); last = m.index + m[0].length
      }
      if (last < text.length) f.appendChild(document.createTextNode(text.slice(last)))
      // put the caret back at the same logical offset
      var o = 0
      for (var n of f.childNodes) { var len = n.nodeType === 3 ? n.data.length : n.dataset.token.length
        if (n.nodeType === 3 && caret <= o + len) { caretNode = n; caretAt = caret - o; break } o += len }
      if (!caretNode) { caretNode = document.createTextNode(''); f.appendChild(caretNode) }
      var r = document.createRange(); r.setStart(caretNode, caretAt); r.collapse(true); var sel = getSelection(); sel.removeAllRanges(); sel.addRange(r)
    }
    f.addEventListener('input', function(e){ if (e.isComposing) return; var v = value(), c = caretOffset(); if ((v.match(/tag:\\w+(?= )/g)||[]).length !== f.querySelectorAll('[data-token]').length) render(v, c) })
    f.addEventListener('beforeinput', function(e){
      if (e.inputType === 'insertParagraph' || e.inputType === 'insertLineBreak') { e.preventDefault(); __out.push('search ' + value()); return }
      if (e.inputType === 'deleteContentBackward') {
        var sel = getSelection(), r = sel.rangeCount && sel.getRangeAt(0)
        if (r && r.collapsed && r.startContainer.nodeType === 3 && r.startContainer.parentNode === f && /^ *$/.test(r.startContainer.data.slice(0, r.startOffset))) {
          var chip = r.startContainer.previousSibling
          if (chip && chip.dataset && chip.dataset.token) { e.preventDefault(); var v = value(), c = caretOffset(), start = c - (r.startOffset) - chip.dataset.token.length; render(v.slice(0, start) + v.slice(c), start); __out.push('chip removed') }
        }
      }
    })</script>`,
  // A page that remounts its input after the second character (Brainstorm's Network search does): the
  // focused element leaves the DOM.
  remount: `<div id="wrap"><input id="f"></div><script>
    var n = 0; document.addEventListener('input', function(e){ if (e.target.id === 'f' && ++n === 2) { var v = e.target.value; document.getElementById('wrap').innerHTML = '<input id="f">'; document.getElementById('f').value = v; __out.push('remounted') } }, true)</script>`,
  // Enter moves focus to the next field (a multi-step form).
  focusNext: `<input id="f"><input id="g"><script>
    document.getElementById('f').addEventListener('keydown', function(e){ if (e.key === 'Enter') { e.preventDefault(); document.getElementById('g').focus(); __out.push('moved') } })
    document.getElementById('g').addEventListener('input', function(e){ __out.push('g=' + e.target.value) })</script>`,
  // A paste handler that cleans what is pasted (strips line breaks, as a single-line chip editor does).
  pasteClean: `<input id="f"><script>
    var f = document.getElementById('f')
    f.addEventListener('paste', function(e){ e.preventDefault(); var t = e.clipboardData.getData('text/plain').replace(/\\s+/g, '-'); f.setRangeText(t, f.selectionStart, f.selectionEnd, 'end'); f.dispatchEvent(new Event('input', { bubbles: true })); __out.push('cleaned') })</script>`,
  // A composer that keeps a character counter and a live "typing" flag from input events (no edits back).
  counter: `<textarea id="f" maxlength="10"></textarea><script>
    var f = document.getElementById('f'); f.addEventListener('input', function(){ window.__count = f.value.length })</script>`,
}

// ---------------------------------------------------------------------------------------------------------
// Scenarios: a fixture and a list of user ops. Ops: type(s), backspace(n), del, enter, shiftEnter,
// select(a,b), selectAll, paste(s), compose(word) then commit, caret(n), pageSet(v), wait.
// ---------------------------------------------------------------------------------------------------------
const S = (name, fixture, ops) => ({ name, fixture, ops })
const SCENARIOS = [
  S('type into an input', 'input', [['type', 'hello']]),
  S('type then backspace', 'input', [['type', 'hello'], ['backspace', 2]]),
  S('backspace on an empty field', 'input', [['backspace', 1]]),
  S('forward delete', 'input', [['type', 'hello'], ['caret', 1], ['del']]),
  S('type in the middle', 'input', [['type', 'helo'], ['caret', 3], ['type', 'l']]),
  S('replace a selection by typing', 'input', [['type', 'hello world'], ['select', 6, 11], ['type', 'there']]),
  S('select all and delete', 'input', [['type', 'hello world'], ['selectAll'], ['backspace', 1]]),
  S('emoji and backspace over it', 'input', [['type', 'hi 😀'], ['backspace', 1]]),
  S('flag emoji (two code points) backspace', 'input', [['type', 'a🇧🇷'], ['backspace', 1]]),
  S('composition commits a word', 'input', [['compose', 'hello'], ['commit'], ['type', ' x']]),
  S('composition then backspace', 'input', [['compose', 'hel'], ['commit'], ['backspace', 1]]),
  S('paste into an input', 'input', [['type', 'a'], ['paste', 'pasted'], ['type', 'b']]),
  S('paste over a selection', 'input', [['type', 'hello world'], ['select', 0, 5], ['paste', 'bye']]),
  S('enter in a plain input does nothing', 'input', [['type', 'x'], ['enter']]),
  S('enter in a textarea adds a line', 'textarea', [['type', 'one'], ['enter'], ['type', 'two']]),
  S('shift+enter in a textarea adds a line', 'textarea', [['type', 'one'], ['shiftEnter'], ['type', 'two']]),
  S('enter in the middle of a textarea', 'textarea', [['type', 'onetwo'], ['caret', 3], ['enter']]),
  S('multi-line backspace joins lines', 'textarea', [['type', 'a'], ['enter'], ['backspace', 1], ['type', 'b']]),
  S('type into a contenteditable', 'ce', [['type', 'hello'], ['backspace', 1]]),
  S('enter in a pre-wrap contenteditable', 'ce', [['type', 'a'], ['enter'], ['type', 'b']]),
  S('enter in a normal contenteditable', 'ceNormal', [['type', 'a'], ['enter'], ['type', 'b']]),
  S('maxlength stops typing', 'maxlength', [['type', 'abcdefgh']]),
  S('maxlength stops a paste', 'maxlength', [['type', 'ab'], ['paste', 'cdefgh']]),
  S('number field', 'number', [['type', '12'], ['backspace', 1], ['type', '5']]),
  S('email field', 'email', [['type', 'a@b.co']]),
  S('password field', 'password', [['type', 'secret'], ['backspace', 1]]),
  S('input mask refuses letters', 'mask', [['type', '1a2b3']]),
  S('react controlled input', 'react', [['type', 'hello'], ['backspace', 1], ['type', '!']]),
  S('react input that upper-cases', 'reactUpper', [['type', 'abc'], ['type', 'd']]),
  S('react input set by the page mid-typing', 'react', [['type', 'ab'], ['pageSet', 'XYZ'], ['type', 'c']]),
  S('chat: enter sends and clears', 'chat', [['type', 'hi there'], ['enter'], ['type', 'next']]),
  S('chat: shift+enter adds a line', 'chat', [['type', 'a'], ['shiftEnter'], ['type', 'b'], ['enter']]),
  S('chat: composing word then enter', 'chat', [['compose', 'hello'], ['enter'], ['type', 'z']]),
  S('form: enter submits', 'form', [['type', 'query'], ['enter']]),
  S('search on keydown enter and clear', 'searchKeydown', [['type', 'nostr'], ['enter'], ['type', 'x']]),
  S('combobox enter picks', 'combo', [['type', 'vit'], ['enter'], ['type', '!']]),
  S('chips: a token becomes a chip, typing continues', 'chips', [['type', 'a tag:x b']]),
  S('chips: backspace removes the chip', 'chips', [['type', 'a tag:x '], ['backspace', 1], ['type', 'c']]),
  S('chips: enter searches', 'chips', [['type', 'tag:y q'], ['enter']]),
  S('counter sees every edit', 'counter', [['type', 'abc'], ['backspace', 1], ['paste', 'xyz']]),
  S('tap moves the caret, typing follows it', 'input', [['type', 'hello world'], ['tap', 2], ['type', 'X']]),
  S('tap moves the caret in a textarea', 'textarea', [['type', 'one'], ['enter'], ['type', 'two'], ['tap', 1], ['type', 'X']]),
  S('tap moves the caret in a contenteditable', 'ce', [['type', 'hello world'], ['tap', 3], ['type', 'X']]),
  S('double-tap selects a word, typing replaces it', 'input', [['type', 'hello world'], ['dbltap', 8], ['type', 'there']]),
  S('page selects text, typing replaces it', 'input', [['type', 'hello world'], ['pageSelect', 0, 5], ['type', 'J']]),
  S('autocorrect replaces a word', 'input', [['type', 'teh cat'], ['replaceRange', 0, 3, 'the']]),
  S('enter over a textarea selection', 'textarea', [['type', 'abcdef'], ['select', 2, 4], ['enter']]),
  S('field remounted mid-typing loses focus', 'remount', [['type', 'abc']]),
  S('enter moves focus to the next field', 'focusNext', [['type', 'a'], ['enter'], ['type', 'b']]),
  S('paste handler cleans the paste', 'pasteClean', [['type', 'x'], ['paste', 'a b\nc']]),
  S('cut a selection', 'input', [['type', 'hello world'], ['select', 0, 6], ['cut'], ['type', 'J']]),
  S('cut in a contenteditable', 'ce', [['type', 'hello world'], ['tap', 0], ['select', 0, 6], ['cut']]),
  S('paste into a contenteditable', 'ce', [['type', 'ab'], ['paste', 'X Y']]),
  S('paste a line break into a normal contenteditable', 'ceNormal', [['type', 'ab'], ['paste', '1\n2']]),
  S('paste refused by an input mask', 'mask', [['type', '1'], ['paste', 'a2']]),
  S('keyboard clipboard chip inserts as typing', 'pasteClean', [['type', 'x'], ['chipPaste', 'a b']]),
  S('long text: type at the end of 20k chars', 'textarea', [['pageFill', 20000], ['tap', -1], ['type', 'end']]),
]

// ---------------------------------------------------------------------------------------------------------
const browser = await chromium.launch({ executablePath: CHROMIUM, args: ['--no-sandbox'] })
const shimSrc = readFileSync(SHIM, 'utf8')
const page = (body) => `<!doctype html><meta charset=utf-8><body><script>window.__out=[]</script>${body}</body>`

async function open(fixture, proxied) {
  const context = await browser.newContext()
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'https://ime.test' })
  if (proxied) {
    await context.addInitScript(() => {
      window.__sent = []
      window.__nappletDirectBridge = true
      window.__nappletImeProxy = true
      window.__nappletBridge = { postMessage(s) { window.__sent.push(s) }, set onmessage(fn) { window.__imeIn = fn }, get onmessage() { return window.__imeIn } }
    })
    await context.addInitScript({ content: shimSrc })
  }
  await context.route('https://ime.test/**', (route) => route.fulfill({ contentType: 'text/html', body: page(FIXTURES[fixture]) }))
  const p = await context.newPage()
  await p.goto('https://ime.test/')
  return { context, p }
}

const fieldState = (p) => p.evaluate(() => {
  const f = document.getElementById('f')
  const ce = f.isContentEditable
  const text = ce ? f.innerText.replace(/\n$/, '') : f.value
  let sel
  if (ce) {
    // Caret as an offset into innerText (what the user sees, line breaks included): drop a marker at the caret.
    const s = getSelection(); sel = null
    if (s.rangeCount && f.contains(s.anchorNode)) {
      const mark = document.createTextNode('\uE000'), r = s.getRangeAt(0).cloneRange(); r.collapse(true); r.insertNode(mark)
      sel = f.innerText.indexOf('\uE000'); mark.remove()
    }
  } else sel = f.selectionStart
  const a = document.activeElement
  const activeText = a && a !== document.body ? (a.isContentEditable ? a.innerText.replace(/\n$/, '') : a.value) : null
  return { text, caret: sel, focused: document.activeElement === f, activeText, out: window.__out.slice() }
})

// ---- native driver -------------------------------------------------------------------------------------
async function runNative(sc) {
  const { context, p } = await open(sc.fixture, false)
  const cdp = await context.newCDPSession(p)
  await p.click('#f')
  for (const [op, a, b, c3] of sc.ops) {
    if (op === 'type') { for (const c of [...a]) await cdp.send('Input.insertText', { text: c }) }
    else if (op === 'backspace') { for (let i = 0; i < a; i++) await p.keyboard.press('Backspace') }
    else if (op === 'del') await p.keyboard.press('Delete')
    else if (op === 'enter') await p.keyboard.press('Enter')
    else if (op === 'shiftEnter') await p.keyboard.press('Shift+Enter')
    else if (op === 'caret') await setSelNative(p, a, a)
    else if (op === 'select') await setSelNative(p, a, b)
    else if (op === 'selectAll') await p.keyboard.press('ControlOrMeta+A')
    else if (op === 'paste') { await p.evaluate((t) => navigator.clipboard.writeText(t), a); await p.keyboard.press('ControlOrMeta+V') }
    else if (op === 'cut') await p.keyboard.press('ControlOrMeta+X')
    else if (op === 'chipPaste') await cdp.send('Input.insertText', { text: a })
    else if (op === 'compose') { for (let i = 1; i <= a.length; i++) await cdp.send('Input.imeSetComposition', { text: a.slice(0, i), selectionStart: i, selectionEnd: i }) ; p.__composing = a }
    else if (op === 'commit') { await cdp.send('Input.insertText', { text: p.__composing }); p.__composing = null }
    else if (op === 'pageSet') await p.evaluate((v) => window.ctl.set(v), a)
    else if (op === 'tap') await tapAt(p, a, false)
    else if (op === 'dbltap') await tapAt(p, a, true)
    else if (op === 'pageSelect') await p.evaluate(([a, b]) => document.activeElement.setSelectionRange(a, b), [a, b])
    else if (op === 'replaceRange') { await setSelNative(p, a, b); await cdp.send('Input.insertText', { text: c3 }) }
    else if (op === 'pageFill') await pageFill(p, a)
    if (op === 'enter' && p.__composing) p.__composing = null
    await p.waitForTimeout(30)
  }
  await p.waitForTimeout(150)
  const st = await fieldState(p)
  await context.close()
  return st
}
async function setSelNative(p, a, b) {
  await p.evaluate(([s, e]) => {
    const f = document.getElementById('f')
    if (!f.isContentEditable) { f.setSelectionRange(s, e); return }
    const w = document.createTreeWalker(f, NodeFilter.SHOW_TEXT); let n, acc = 0, x = null, y = null
    while ((n = w.nextNode())) { const len = n.data.length; if (!x && s <= acc + len) x = [n, s - acc]; if (!y && e <= acc + len) y = [n, e - acc]; acc += len }
    const r = document.createRange(); r.setStart(...x); r.setEnd(...y); const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r)
  }, [a, b])
}

// ---- proxied driver: the host is a model of RemoteImeView ---------------------------------------------
async function runProxied(sc) {
  const { context, p } = await open(sc.fixture, true)
  const host = { text: '', s: 0, e: 0, multiline: false, readOnly: false, composing: null, focused: false }
  const drain = async () => {
    const raw = await p.evaluate(() => { const s = window.__sent.slice(); window.__sent.length = 0; return s })
    for (const m of raw.map((x) => JSON.parse(x))) {
      if (m.type === 'ime.focus' || m.type === 'ime.refocus') { Object.assign(host, { text: m.text, s: m.selStart, e: m.selEnd, multiline: m.multiline, readOnly: m.readOnly, focused: true, composing: null }) }
      else if (m.type === 'ime.state') { if (host.text !== m.text || host.s !== m.selStart || host.e !== m.selEnd) { Object.assign(host, { text: m.text, s: m.selStart, e: m.selEnd, composing: null }) } }
      else if (m.type === 'ime.blur') host.focused = false
    }
  }
  const ship = async () => {
    const msg = { type: 'ime.set', text: host.text, selStart: host.s, selEnd: host.e, composingStart: host.composing ? host.composing[0] : -1, composingEnd: host.composing ? host.composing[1] : -1 }
    await p.evaluate((m) => window.__imeIn({ data: JSON.stringify(m) }), msg)
    await p.waitForTimeout(5)
    await drain()
  }
  const replace = (ins) => { host.text = host.text.slice(0, host.s) + ins + host.text.slice(host.e); host.s = host.e = host.s + ins.length }
  const action = async () => { await p.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.action' }) })); await p.waitForTimeout(5); await drain() }
  await p.click('#f'); await p.waitForTimeout(30); await drain()
  for (const [op, a, b, c3] of sc.ops) {
    if (!host.focused) break // the host only edits a field the page has focused
    if (op === 'type') { for (const c of [...a]) { replace(c); await ship() } }
    else if (op === 'backspace') { for (let i = 0; i < a; i++) { if (host.s !== host.e) replace(''); else if (host.s > 0) { const cut = graphemeBefore(host.text, host.s); host.text = host.text.slice(0, host.s - cut) + host.text.slice(host.s); host.s = host.e = host.s - cut } else continue; await ship() } }
    else if (op === 'del') { if (host.s !== host.e) replace(''); else if (host.s < host.text.length) { host.text = host.text.slice(0, host.s) + host.text.slice(host.s + 1) } await ship() }
    else if (op === 'enter') { if (host.composing) { host.composing = null; await ship() } await action() }
    else if (op === 'shiftEnter') { if (host.multiline) { replace('\n'); await ship() } else await action() }
    else if (op === 'caret') { host.s = host.e = a; await ship() }
    else if (op === 'select') { host.s = a; host.e = b; await ship() }
    else if (op === 'selectAll') { host.s = 0; host.e = host.text.length; await ship() }
    else if (op === 'paste') { await p.evaluate((t) => window.__imeIn({ data: JSON.stringify({ type: 'ime.paste', text: t }) }), a); await p.waitForTimeout(5); await drain() }
    else if (op === 'cut') { await p.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.cut' }) })); await p.waitForTimeout(5); await drain() }
    else if (op === 'chipPaste') { replace(a); await ship() }
    else if (op === 'compose') { const start = host.s; for (let i = 1; i <= a.length; i++) { host.text = host.text.slice(0, start) + a.slice(0, i) + host.text.slice(host.e); host.s = host.e = start + i; host.composing = [start, start + i]; await ship() } }
    else if (op === 'commit') { host.composing = null; await ship() }
    else if (op === 'pageSet') { await p.evaluate((v) => window.ctl.set(v), a); await p.waitForTimeout(20); await drain() }
    else if (op === 'tap') { await tapAt(p, a, false); await p.waitForTimeout(20); await drain() }
    else if (op === 'dbltap') { await tapAt(p, a, true); await p.waitForTimeout(20); await drain() }
    else if (op === 'pageSelect') { await p.evaluate(([a, b]) => document.activeElement.setSelectionRange(a, b), [a, b]); await p.waitForTimeout(20); await drain() }
    else if (op === 'replaceRange') { host.s = a; host.e = b; replace(c3); await ship() }
    else if (op === 'pageFill') { await pageFill(p, a); await p.waitForTimeout(20); await drain() }
    await p.waitForTimeout(30); await drain()
  }
  await p.waitForTimeout(150); await drain()
  const st = await fieldState(p)
  st.hostText = host.text
  st.hostFocused = host.focused
  await context.close()
  return st
}
// A tap in the field: Chrome puts the caret at the tapped character (a double-tap selects its word). Done the
// same way in both modes, page-side; what differs is whether the host's mirror hears about it.
async function tapAt(p, at, word) {
  await p.evaluate(([at, word]) => {
    const f = document.getElementById('f')
    const text = f.isContentEditable ? f.textContent : f.value
    if (at < 0) at = text.length
    let s = at, e = at
    if (word) { while (s > 0 && /\w/.test(text[s - 1])) s--; while (e < text.length && /\w/.test(text[e])) e++ }
    f.focus()
    if (!f.isContentEditable) { f.setSelectionRange(s, e); return }
    const w = document.createTreeWalker(f, NodeFilter.SHOW_TEXT); let n, acc = 0, a = null, b = null
    while ((n = w.nextNode())) { const len = n.data.length; if (!a && s <= acc + len) a = [n, s - acc]; if (!b && e <= acc + len) b = [n, e - acc]; acc += len }
    const r = document.createRange(); r.setStart(...a); r.setEnd(...b); const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r)
  }, [at, word])
}
// The page loads a long draft into the field (as a framework restoring state would).
async function pageFill(p, n) {
  await p.evaluate((n) => {
    const f = document.getElementById('f'), v = 'lorem ipsum dolor sit amet '.repeat(Math.ceil(n / 27)).slice(0, n)
    Object.getOwnPropertyDescriptor(Object.getPrototypeOf(f), 'value').set.call(f, v)
    f.dispatchEvent(new Event('input', { bubbles: true }))
  }, n)
}
// The UTF-16 length of the last user-perceived character before `at` (what a keyboard's backspace removes).
function graphemeBefore(text, at) {
  const seg = new Intl.Segmenter(undefined, { granularity: 'grapheme' })
  let last = 1
  for (const { index, segment } of seg.segment(text.slice(0, at))) last = segment.length
  return last
}

// ---------------------------------------------------------------------------------------------------------
const failures = []
for (const sc of SCENARIOS) {
  if (FILTER && !sc.name.includes(FILTER)) continue
  const n = await runNative(sc)
  const x = await runProxied(sc)
  const diffs = []
  if (n.text !== x.text) diffs.push(`text native=${JSON.stringify(n.text)} embed=${JSON.stringify(x.text)}`)
  if (n.caret !== x.caret) diffs.push(`caret native=${n.caret} embed=${x.caret}`)
  if (JSON.stringify(n.out) !== JSON.stringify(x.out)) diffs.push(`page native=${JSON.stringify(n.out)} embed=${JSON.stringify(x.out)}`)
  if (n.focused !== x.focused) diffs.push(`focus native=${n.focused} embed=${x.focused}`)
  // The host mirror must end up holding what the field shows, or the next keystroke corrupts it.
  // (Only while a field is focused: with none, the host's buffer is stale by design and never shipped.)
  if (x.hostFocused && x.activeText !== null && x.hostText.replace(/\n$/, '') !== x.activeText) diffs.push(`host mirror=${JSON.stringify(x.hostText)} focused field=${JSON.stringify(x.activeText)}`)
  if (x.hostFocused && x.activeText === null) diffs.push('host still mirrors a field although nothing is focused (keyboard left up)')
  console.log(`${diffs.length ? 'FAIL' : 'ok  '}  ${sc.name}${diffs.length ? '\n        ' + diffs.join('\n        ') : ''}`)
  if (diffs.length) failures.push(sc.name)
}
console.log(`\n${failures.length} of ${SCENARIOS.filter((s) => !FILTER || s.name.includes(FILTER)).length} scenarios differ from native`)
await browser.close()
process.exit(failures.length ? 1 : 0)
