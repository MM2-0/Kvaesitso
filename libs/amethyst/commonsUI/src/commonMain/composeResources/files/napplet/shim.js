// Injected window.napplet.* client shim. Namespaced to match the @napplet/shim SDK over the
// {type:"domain.action", id} envelope, so an applet built against that SDK runs unchanged here.
// This is loaded as an asset and injected into the applet document by NappletHostActivity.
(function(){
  if (window.__nappletShimInstalled) return; window.__nappletShimInstalled = true;

  // Website-only Web Storage polyfill. The applet runs in an `allow-scripts` (no `allow-same-origin`) iframe, so
  // its origin is opaque and reading `window.localStorage`/`sessionStorage` throws a SecurityError —
  // which aborts the bootstrap of essentially every bundler-built SPA (they read storage at init) and
  // leaves a blank page. We can't grant `allow-same-origin` (that would hand the applet the
  // napplet.local origin the native bridge trusts — a sandbox escape), so we shadow the throwing
  // native accessor with a synchronous in-memory Storage. It is per-launch (not persisted); durable
  // storage is available separately and asynchronously via `window.napplet.storage.*`. This inline
  // shim runs before the applet's deferred module script, so the polyfill is in place first.
  (function(){
    if (!window.__nappletNip07) return;
    function makeStorage(){
      var data = Object.create(null);
      var methods = {
        getItem: function(k){ k = String(k); return Object.prototype.hasOwnProperty.call(data, k) ? data[k] : null; },
        setItem: function(k, v){ data[String(k)] = String(v); },
        removeItem: function(k){ delete data[String(k)]; },
        clear: function(){ data = Object.create(null); },
        key: function(i){ var ks = Object.keys(data); i = i >>> 0; return i < ks.length ? ks[i] : null; }
      };
      return new Proxy(methods, {
        get: function(t, p){
          if (p === 'length') return Object.keys(data).length;
          if (typeof p !== 'string' || p in t) return t[p];
          return Object.prototype.hasOwnProperty.call(data, p) ? data[p] : undefined;
        },
        set: function(t, p, v){ if (p in t) { t[p] = v; } else { data[String(p)] = String(v); } return true; },
        has: function(t, p){ return (p in t) || (p === 'length') || Object.prototype.hasOwnProperty.call(data, p); },
        deleteProperty: function(t, p){ delete data[p]; return true; },
        ownKeys: function(){ return Object.keys(data); },
        getOwnPropertyDescriptor: function(t, p){
          if (Object.prototype.hasOwnProperty.call(data, p)) return { value: data[p], writable: true, enumerable: true, configurable: true };
          return undefined;
        }
      });
    }
    function install(name){
      try { if (window[name]) return; } catch (_) { /* native getter threw — install the polyfill */ }
      try { Object.defineProperty(window, name, { value: makeStorage(), configurable: true, enumerable: true, writable: false }); } catch (_) {}
    }
    install('localStorage');
    install('sessionStorage');
  })();

  var seq = 0, pending = {}, subs = {}, outboxSubs = {}, actions = {}, identityHandlers = [], themeHandlers = [], fsHandlers = [], uploadHandlers = [];
  // Transport. A napplet/nSite runs inside the trusted shell's iframe and talks to the shell via
  // postMessage (the shell relays to the native bridge). A top-level page opened in the in-app browser
  // has no shell parent, so it talks to the origin-scoped native bridge object (__nappletBridge,
  // injected for the page) directly. __nappletDirectBridge selects that path.
  var DIRECT = false; try { DIRECT = !!window.__nappletDirectBridge; } catch (_) {}
  var recvWired = false;
  // In the shell's frame, NAP rides a MessagePort made for this document (NIP-5D Transport). The
  // shim runs before any page script, so it makes the channel, keeps one end in this closure and
  // hands the other to the shell in the one window-level message it sends, `napplet.hello`. The
  // shell binds the napplet's identity to that port, so a document that later takes this frame's
  // place has no channel. The send is bound now, while MessagePort's prototype is still the
  // browser's own.
  var portSend = null;
  if (!DIRECT) {
    var channel = new MessageChannel();
    portSend = MessagePort.prototype.postMessage.bind(channel.port1);
    channel.port1.onmessage = function(e){ onIncoming(e.data); };
    parent.postMessage({ type: 'napplet.hello' }, '*', [channel.port2]);
  }
  function rawSend(s){
    if (DIRECT) {
      var b = window.__nappletBridge; if (!b) return;
      // Wire the reply channel before the first send, so no reply can arrive before we listen.
      if (!recvWired) { recvWired = true; b.onmessage = function(e){ onIncoming(e.data); }; }
      b.postMessage(s);
    } else {
      portSend(JSON.parse(s));
    }
  }
  function send(env){ env.id = env.id || ('r' + (seq++)); rawSend(JSON.stringify(env)); return env.id; }
  function call(type, fields){
    return new Promise(function(resolve, reject){
      var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k];
      var id = send(env);
      pending[id] = { resolve: resolve, reject: reject };
    });
  }
  // NAP-OUTBOX results carry their own outcome (`ok`, `error`, `incomplete`) for the napplet to read,
  // so they resolve with the whole reply instead of rejecting on a failure.
  function callResult(type, fields){
    return new Promise(function(resolve){
      var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k];
      var id = send(env);
      pending[id] = { resolve: resolve, reject: function(err){ resolve(err.napplet || { error: String(err && err.message || err) }); } };
    });
  }
  // The named fields of a reply that are present, so an absent one is absent, not undefined.
  function pick(m, keys){ var out = {}; keys.forEach(function(k){ if (m[k] !== undefined) out[k] = m[k]; }); return out; }
  // Fire-and-forget (no .result awaited), used for subscribe/unsubscribe.
  function post(type, fields){ var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k]; send(env); }
  function onIncoming(raw){
    var msg; if (typeof raw === 'string') { try { msg = JSON.parse(raw); } catch (_) { return; } } else { msg = raw; }
    if (!msg) return;
    // Subscription pushes are keyed by subId, not a request id.
    if (msg.type === 'relay.event' || msg.type === 'relay.eose' || msg.type === 'relay.closed') {
      var sub = subs[msg.subId]; if (!sub) return;
      if (msg.type === 'relay.event') { if (sub.onEvent) sub.onEvent(msg.result); }
      else if (msg.type === 'relay.eose') { if (sub.onEose) sub.onEose(); }
      else { delete subs[msg.subId]; if (sub.onClosed) sub.onClosed(msg.reason); }
      return;
    }
    if (msg.type === 'outbox.event' || msg.type === 'outbox.closed') {
      var osub = outboxSubs[msg.subId]; if (!osub) return;
      if (msg.type === 'outbox.event') osub.event.slice().forEach(function(cb){ try { cb(msg.result); } catch (_) {} });
      else { delete outboxSubs[msg.subId]; osub.closed.slice().forEach(function(cb){ try { cb(msg.reason); } catch (_) {} }); }
      return;
    }
    // keys.action push: the shell triggers a registered keyboard/command action.
    if (msg.type === 'keys.action') { var cb = actions[msg.actionId]; if (cb) cb(); return; }
    // IME ops (host keyboard -> focused page field). Only the direct-bridge browser installs the agent.
    if (msg.type && msg.type.indexOf('ime.') === 0) { if (window.__nappletImeHandle) window.__nappletImeHandle(msg); return; }
    // identity.changed push: the active user's key changed (account switch / connect / disconnect).
    if (msg.type === 'identity.changed') { identityHandlers.slice().forEach(function(h){ try { h(msg.pubkey); } catch (_) {} }); return; }
    if (msg.type === 'theme.changed') { themeHandlers.slice().forEach(function(h){ try { h(msg.theme); } catch (_) {} }); return; }
    if (msg.type && msg.type.indexOf('inc.') === 0 && incIncoming(msg)) return;
    if (msg.type === 'intent.deliver') {
      if (intentDeliveryHandlers.length) intentDeliveryHandlers.slice().forEach(function(h){ incSafe(h, msg.delivery); });
      else if (intentDeliveries.length < 64) intentDeliveries.push(msg.delivery);
      return;
    }
    if (msg.type === 'intent.changed') { intentChangedHandlers.slice().forEach(function(h){ incSafe(h, msg.availability); }); return; }
    if (msg.type === 'upload.status.changed') { uploadHandlers.slice().forEach(function(h){ try { h(msg.status); } catch (_) {} }); return; }
    if (msg.type === 'fs.changed') { fsHandlers.slice().forEach(function(h){ try { h(msg.change); } catch (_) {} }); return; }
    if (!msg.id) return;
    var p = pending[msg.id]; if (!p) return; delete pending[msg.id]; if (p.cleanup) p.cleanup();
    if (msg.ok !== false && !msg.error) p.resolve(msg);
    else { var err = new Error(msg.reason || msg.operation || msg.error || 'napplet error'); err.napplet = msg; p.reject(err); }
  }
  // The shell-relayed path receives on its port (wired above); the direct-bridge path wires its
  // receive channel in rawSend the first time it posts.
  function field(promise, name){ return promise.then(function(m){ return m[name]; }); }
  function resourceCall(type, fields, opts){
    var signal = opts && opts.signal;
    if (signal && signal.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'));
    return new Promise(function(resolve, reject){
      var env = { type: type }; for (var k in fields) env[k] = fields[k];
      var id = send(env), onAbort;
      var cleanup = function(){ if (signal && onAbort) signal.removeEventListener('abort', onAbort); };
      pending[id] = { resolve: resolve, reject: reject, cleanup: cleanup };
      if (signal) {
        onAbort = function(){
          if (!pending[id]) return;
          delete pending[id]; cleanup();
          post('resource.cancel', { id: id });
          reject(new DOMException('Aborted', 'AbortError'));
        };
        signal.addEventListener('abort', onAbort, { once: true });
      }
    });
  }
  function resourceBlob(encoded, mime){
    if (typeof encoded !== 'string' || typeof mime !== 'string') throw new Error('Invalid resource bytes');
    var bin = atob(encoded);
    if (btoa(bin) !== encoded) throw new Error('Non-canonical resource base64');
    var bytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new Blob([bytes], { type: mime });
  }
  function normFilters(filters){ return { filters: Array.isArray(filters) ? filters : [filters || {}] }; }
  function bytesToB64(bytes){
    var u = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes), parts = [];
    // In slices: one fromCharCode call per byte made multi-megabyte uploads crawl.
    for (var i = 0; i < u.length; i += 0x8000) parts.push(String.fromCharCode.apply(null, u.subarray(i, i + 0x8000)));
    return btoa(parts.join(''));
  }
  // A NAP-COMMON reply without the envelope's own fields.
  function commonResult(m){ var out = {}; for (var k in m) if (k !== 'type' && k !== 'id') out[k] = m[k]; if (out.ok === undefined) out.ok = !out.error; return out; }
  // follow('npub1…', 'npub1…') or follow(['npub1…', …]).
  function commonKeys(args){ return (args.length === 1 && Array.isArray(args[0])) ? args[0].slice() : Array.prototype.slice.call(args); }
  // The bytes of an upload's `data`, whatever form the napplet handed over.
  function uploadBytes(data){
    if (typeof Blob !== 'undefined' && data instanceof Blob) return data.arrayBuffer().then(function(b){ return new Uint8Array(b); });
    if (data instanceof ArrayBuffer) return Promise.resolve(new Uint8Array(data));
    if (ArrayBuffer.isView(data)) return Promise.resolve(new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
    return Promise.reject(new TypeError('upload data must be a Blob, ArrayBuffer or typed array'));
  }
  // NAP-INC: topics and channels between napplets, routed by the shell under runtime-assigned endpoint
  // identifiers. Several on(topic) handlers share one shell subscription per topic: the wire's
  // inc.unsubscribe drops the whole topic, so it goes out only when the last handler closes.
  var incTopics = {}, incChannels = {}, incUnclaimed = [], incOpenedHandlers = [];
  // How many unclaimed inbound handles, or undelivered messages on one channel, the binding keeps.
  // Past it the channel is closed (NAP-INC: never drop them silently).
  var INC_BUFFER = 256;
  function incLater(fn){ Promise.resolve().then(fn); }
  function incSafe(cb, arg){ try { cb(arg); } catch (_) {} }
  function incRemove(list, item){ var i = list.indexOf(item); if (i >= 0) list.splice(i, 1); }
  // Convention URI napplet:<archetype>/<intent>?a=b -> stable topic + text payload { a: 'b' }.
  // Values stay text (a '+' is a plus); a fragment, bad percent-encoding, a repeated name, or a query
  // together with an explicit payload is refused before anything is sent.
  function incTranspose(topic, payload, hasPayload){
    if (typeof topic !== 'string' || topic === '') throw new TypeError('inc topic must be a non-empty string');
    if (topic.indexOf('napplet:') !== 0) return { topic: topic, payload: payload, hasPayload: hasPayload };
    if (topic.indexOf('#') >= 0) throw new TypeError('inc convention URI must not have a fragment');
    var q = topic.indexOf('?');
    if (q < 0) return { topic: topic, payload: payload, hasPayload: hasPayload };
    var stable = topic.substring(0, q), query = topic.substring(q + 1);
    if (query === '') return { topic: stable, payload: payload, hasPayload: hasPayload };
    if (hasPayload) throw new TypeError('inc convention URI query and an explicit payload cannot be combined');
    var params = {};
    query.split('&').forEach(function(pair){
      if (pair === '') return;
      var eq = pair.indexOf('=');
      var name, value;
      try {
        name = decodeURIComponent(eq < 0 ? pair : pair.substring(0, eq));
        value = eq < 0 ? '' : decodeURIComponent(pair.substring(eq + 1));
      } catch (_) { throw new TypeError('inc convention URI has malformed percent-encoding'); }
      if (Object.prototype.hasOwnProperty.call(params, name)) throw new TypeError('inc convention URI repeats ' + name);
      params[name] = value;
    });
    return { topic: stable, payload: params, hasPayload: true };
  }
  function incChannel(channelId, peer){
    var ch = incChannels[channelId];
    if (ch) { if (peer && !ch.handle.peer) ch.handle.peer = peer; return ch; }
    ch = { handlers: [], closedHandlers: [], buffer: [], closed: null, closing: false };
    incChannels[channelId] = ch;
    function closeChannel(){ if (ch.closed || ch.closing) return; ch.closing = true; post('inc.channel.close', { channelId: channelId }); }
    ch.close = closeChannel;
    ch.handle = {
      id: channelId,
      peer: peer || '',
      emit: function(payload){
        if (ch.closed || ch.closing) return;
        var env = { channelId: channelId }; if (payload !== undefined) env.payload = payload;
        post('inc.channel.emit', env);
      },
      on: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        ch.handlers.push(handler);
        // Messages that came before anyone listened go to the first handler, in order.
        var queued = ch.buffer; ch.buffer = [];
        queued.forEach(function(event){ incSafe(handler, event); });
        return { close: function(){ incRemove(ch.handlers, handler); } };
      },
      onClosed: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        if (ch.closed) { var record = ch.closed; incLater(function(){ incSafe(handler, record); }); return { close: function(){} }; }
        ch.closedHandlers.push(handler);
        return { close: function(){ incRemove(ch.closedHandlers, handler); } };
      },
      close: closeChannel
    };
    return ch;
  }
  function incIncoming(msg){
    if (msg.type === 'inc.event') {
      var topic = incTopics[msg.topic]; if (!topic) return true;
      var event = { topic: msg.topic, sender: msg.sender }; if (msg.payload !== undefined) event.payload = msg.payload;
      topic.handlers.slice().forEach(function(h){ incSafe(h, event); });
      return true;
    }
    if (msg.type === 'inc.channel.opened') {
      if (typeof msg.channelId !== 'string') return true;
      var opened = incChannel(msg.channelId, msg.peer);
      if (incOpenedHandlers.length) incOpenedHandlers.slice().forEach(function(h){ incSafe(h, opened.handle); });
      else if (incUnclaimed.length >= INC_BUFFER) opened.close();
      else incUnclaimed.push(opened);
      return true;
    }
    if (msg.type === 'inc.channel.event') {
      if (typeof msg.channelId !== 'string') return true;
      var ch = incChannel(msg.channelId, null); if (ch.closed || ch.closing) return true;
      var cev = { channelId: msg.channelId, sender: msg.sender }; if (msg.payload !== undefined) cev.payload = msg.payload;
      if (ch.handlers.length) ch.handlers.slice().forEach(function(h){ incSafe(h, cev); });
      else if (ch.buffer.length >= INC_BUFFER) ch.close();
      else ch.buffer.push(cev);
      return true;
    }
    if (msg.type === 'inc.channel.closed') {
      if (typeof msg.channelId !== 'string') return true;
      var gone = incChannel(msg.channelId, null); if (gone.closed) return true;
      var record = { channelId: msg.channelId }; if (typeof msg.reason === 'string') record.reason = msg.reason;
      gone.closed = record;
      delete incChannels[msg.channelId];
      gone.closedHandlers.splice(0).forEach(function(h){ incSafe(h, record); });
      return true;
    }
    return false;
  }
  var incDomain = {
    emit: function(topic, payload){
      var routed = incTranspose(topic, payload, arguments.length > 1 && payload !== undefined);
      var env = { topic: routed.topic }; if (routed.hasPayload) env.payload = routed.payload;
      post('inc.emit', env);
    },
    on: function(topicName, handler){
      if (typeof topicName !== 'string' || topicName === '') throw new TypeError('inc topic must be a non-empty string');
      if (typeof handler !== 'function') throw new TypeError('inc handler must be a function');
      // An emit routes a convention URI on its queryless topic, so a listener with a query would never match.
      if (topicName.indexOf('napplet:') === 0 && /[?#]/.test(topicName)) throw new TypeError('inc.on takes the stable topic, without a query or fragment');
      var topic = incTopics[topicName];
      if (!topic) {
        topic = incTopics[topicName] = { handlers: [] };
        topic.ready = call('inc.subscribe', { topic: topicName }).then(function(){}, function(err){
          // Not registered: forget it, so a later on() asks again.
          if (incTopics[topicName] === topic) delete incTopics[topicName];
          throw err;
        });
        topic.ready.catch(function(){});
      }
      topic.handlers.push(handler);
      var closed = false;
      var subscription = { close: function(){
        if (closed) return; closed = true;
        incRemove(topic.handlers, handler);
        if (!topic.handlers.length && incTopics[topicName] === topic) { delete incTopics[topicName]; post('inc.unsubscribe', { topic: topicName }); }
      } };
      // Settles once the shell registered the topic, or rejects with its reason.
      Object.defineProperty(subscription, 'ready', { value: topic.ready, enumerable: false });
      return subscription;
    },
    channel: {
      open: function(target){
        if (typeof target !== 'string' || target === '') return Promise.reject(new TypeError('inc channel target must be an endpoint identifier'));
        return call('inc.channel.open', { target: target }).then(function(m){ return incChannel(m.channelId, m.peer || target).handle; });
      },
      onOpened: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        incOpenedHandlers.push(handler);
        var waiting = incUnclaimed; incUnclaimed = [];
        waiting.forEach(function(ch){ incSafe(handler, ch.handle); });
        return { close: function(){ incRemove(incOpenedHandlers, handler); } };
      },
      list: function(){ return field(call('inc.channel.list'), 'channels').then(function(list){ return Array.isArray(list) ? list : []; }); },
      broadcast: function(payload){ var env = {}; if (payload !== undefined) env.payload = payload; post('inc.channel.broadcast', env); }
    }
  };
  // NAP-INTENT. The URI is normalized here, as the runtime-provided binding must: archetype, action,
  // the queryless convention, the query as a text payload (INC's rules), and a `#naddr` fragment as a
  // handlerHint. Deliveries wait here until the napplet registers onDelivery.
  var intentDeliveries = [], intentDeliveryHandlers = [], intentChangedHandlers = [];
  var B32 = 'qpzry9x8gf2tvdw0s3jn54khce6mua7l';
  function bech32Bytes(str, hrp){
    var s = str.toLowerCase();
    if (s !== str && str.toUpperCase() !== str) return null;
    var pos = s.lastIndexOf('1');
    if (pos < 1 || pos + 7 > s.length || s.substring(0, pos) !== hrp) return null;
    var data = [], i;
    for (i = pos + 1; i < s.length; i++) { var d = B32.indexOf(s.charAt(i)); if (d < 0) return null; data.push(d); }
    var values = [];
    for (i = 0; i < hrp.length; i++) values.push(hrp.charCodeAt(i) >> 5);
    values.push(0);
    for (i = 0; i < hrp.length; i++) values.push(hrp.charCodeAt(i) & 31);
    var GEN = [0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3], chk = 1;
    values.concat(data).forEach(function(v){ var b = chk >>> 25; chk = ((chk & 0x1ffffff) << 5) ^ v; for (var j = 0; j < 5; j++) if ((b >>> j) & 1) chk ^= GEN[j]; });
    if (chk !== 1) return null;
    var acc = 0, bits = 0, out = [];
    data.slice(0, data.length - 6).forEach(function(v){ acc = ((acc << 5) | v) & 0xffff; bits += 5; while (bits >= 8) { bits -= 8; out.push((acc >> bits) & 255); } });
    return new Uint8Array(out);
  }
  // A bare naddr of a kind 35129 napplet -> { address: "35129:<pubkey>:<d>", relays? }.
  function naddrHint(fragment){
    var bytes = bech32Bytes(fragment, 'naddr'), d = null, author = null, kind = null, relays = [];
    if (!bytes) throw new TypeError('intent URI fragment must be a bare naddr');
    var text = new TextDecoder();
    for (var i = 0; i + 2 <= bytes.length;) {
      var t = bytes[i], l = bytes[i + 1], v = bytes.subarray(i + 2, i + 2 + l);
      if (v.length !== l) break;
      if (t === 0) d = text.decode(v);
      else if (t === 1) relays.push(text.decode(v));
      else if (t === 2 && l === 32) author = Array.prototype.map.call(v, function(b){ return (b < 16 ? '0' : '') + b.toString(16); }).join('');
      else if (t === 3 && l === 4) kind = ((v[0] << 24) | (v[1] << 16) | (v[2] << 8) | v[3]) >>> 0;
      i += 2 + l;
    }
    if (kind !== 35129 || !author || !d) throw new TypeError('intent handler hint must be a kind 35129 napplet address');
    var hint = { address: '35129:' + author + ':' + d };
    if (relays.length) hint.relays = relays;
    return hint;
  }
  function intentRequest(uri, options, mustOpen){
    if (typeof uri !== 'string' || uri.indexOf('napplet:') !== 0) throw new TypeError('intent needs a napplet:<archetype>/<intent> URI');
    var opts = options || {}, fragment = null, hash = uri.indexOf('#');
    if (hash >= 0) { fragment = uri.substring(hash + 1); uri = uri.substring(0, hash); }
    var routed = incTranspose(uri, opts.payload, opts.payload !== undefined);
    var m = /^napplet:([^\/?#]+)\/([^\/?#]+)$/.exec(routed.topic);
    if (!m) throw new TypeError('invalid convention');
    if (mustOpen && m[2] !== 'open') throw new TypeError('open takes a napplet:<archetype>/open URI');
    var request = { archetype: m[1], action: m[2], convention: routed.topic };
    if (routed.hasPayload) request.payload = routed.payload;
    if (opts.handler !== undefined) request.handler = opts.handler;
    if (opts.behavior !== undefined) request.behavior = opts.behavior;
    if (fragment !== null) {
      if (opts.handlerHint !== undefined) throw new TypeError('a URI fragment and a handlerHint cannot be combined');
      request.handlerHint = naddrHint(fragment);
    } else if (opts.handlerHint !== undefined) request.handlerHint = opts.handlerHint;
    return request;
  }
  function intentInvoke(uri, options, mustOpen){
    var request;
    try { request = intentRequest(uri, options, mustOpen); } catch (e) { return Promise.reject(e); }
    return field(call('intent.invoke', { request: request }), 'result');
  }
  var intentDomain = {
    invoke: function(uri, options){ return intentInvoke(uri, options, false); },
    open: function(uri, options){ return intentInvoke(uri, options, true); },
    available: function(archetype){ return field(call('intent.available', { archetype: archetype }), 'availability'); },
    handlers: function(){ return field(call('intent.handlers'), 'handlers'); },
    onChanged: function(handler){
      if (typeof handler !== 'function') return { close: function(){} };
      intentChangedHandlers.push(handler);
      return { close: function(){ incRemove(intentChangedHandlers, handler); } };
    },
    onDelivery: function(handler){
      if (typeof handler !== 'function') return { close: function(){} };
      intentDeliveryHandlers.push(handler);
      var waiting = intentDeliveries; intentDeliveries = [];
      waiting.forEach(function(d){ incSafe(handler, d); });
      return { close: function(){ incRemove(intentDeliveryHandlers, handler); } };
    }
  };
  // Build the methods Amethyst actually implements, then project only the explicit domains the
  // trusted host authorized for this launch. Domain-object presence is the NIP-5D availability
  // signal; there is deliberately no legacy window.napplet.shell capability probe.
  var available = {
    identity: {
      getPublicKey: function(){ return field(call('identity.getPublicKey'), 'pubkey'); },
      getProfile: function(){ return field(call('identity.getProfile'), 'profile'); },
      getRelays: function(){ return field(call('identity.getRelays'), 'relays'); },
      getFollows: function(){ return field(call('identity.getFollows'), 'pubkeys'); },
      getMutes: function(){ return field(call('identity.getMutes'), 'pubkeys'); },
      getBlocked: function(){ return field(call('identity.getBlocked'), 'pubkeys'); },
      getList: function(listType){ return field(call('identity.getList', { listType: listType }), 'entries'); },
      getZaps: function(){ return field(call('identity.getZaps'), 'zaps'); },
      getBadges: function(){ return field(call('identity.getBadges'), 'badges'); },
      // The runtime owns identity.changed delivery; handlers are entirely local to the shim.
      onChanged: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        identityHandlers.push(handler);
        return { close: function(){
          var i = identityHandlers.indexOf(handler); if (i >= 0) identityHandlers.splice(i, 1);
        } };
      }
    },
    // keys = keyboard / command action binding (NOT signing). The shell honors the action's defaultKey
    // and pushes keys.action when the bound combo is pressed, which fires the onAction callback.
    keys: {
      registerAction: function(action){ return call('keys.registerAction', { action: action }).then(function(m){ return { actionId: m.actionId, binding: m.binding }; }); },
      unregisterAction: function(actionId){ post('keys.unregisterAction', { actionId: actionId }); },
      onAction: function(actionId, cb){ actions[actionId] = cb; return { close: function(){ delete actions[actionId]; } }; }
    },
    relay: {
      // publish takes an UNSIGNED template (carried in the `event` field per @napplet/shim); the
      // shell signs it and resolves to the signed event.
      publish: function(template, options){ return field(call('relay.publish', { event: template, options: options }), 'event'); },
      publishEncrypted: function(template, recipient, encryption){ return field(call('relay.publishEncrypted', { event: template, recipient: recipient, encryption: encryption || 'nip44' }), 'event'); },
      query: function(filters){ return field(call('relay.query', normFilters(filters)), 'events'); },
      // subscribe is a live tail: the shell streams relay.event (stored + live), one relay.eose, and
      // relay.closed keyed by subId until close() sends relay.close.
      subscribe: function(filters, onEvent, onEose, options){
        var subId = 's' + (seq++);
        subs[subId] = { onEvent: onEvent, onEose: onEose };
        var env = normFilters(filters); env.subId = subId;
        if (options && options.relay) env.relay = options.relay;
        post('relay.subscribe', env);
        return { close: function(){ delete subs[subId]; post('relay.close', { subId: subId }); } };
      }
    },
    // NAP-OUTBOX: the shell finds each author's relays (NIP-65), reads and deduplicates, and fans a
    // publish out to the user's write relays and the inboxes named. Results are NAP-OUTBOX's objects.
    outbox: {
      getEvent: function(eventId, options){
        return callResult('outbox.getEvent', { eventId: eventId, options: options }).then(function(m){ return pick(m, ['result', 'incomplete', 'error']); });
      },
      query: function(filters, options){
        return callResult('outbox.query', { filters: filters, options: options }).then(function(m){ var r = pick(m, ['events', 'incomplete', 'error']); r.events = r.events || []; return r; });
      },
      // A live stream: on('event', cb) gets each RelayEventResult, on('closed', cb) the end; close() stops it.
      subscribe: function(filters, options){
        var subId = 'o' + (seq++);
        var sub = { event: [], closed: [] };
        outboxSubs[subId] = sub;
        post('outbox.subscribe', { subId: subId, filters: filters, options: options });
        var handle = {
          on: function(name, cb){ if (typeof cb === 'function' && sub[name]) sub[name].push(cb); return handle; },
          close: function(){ delete outboxSubs[subId]; post('outbox.close', { subId: subId }); }
        };
        return handle;
      },
      publish: function(template, options){
        return callResult('outbox.publish', { event: template, options: options }).then(function(m){
          var r = pick(m, ['event', 'eventId', 'relays', 'error']); r.ok = m.ok === true; return r;
        });
      },
      resolveRelays: function(target){
        return callResult('outbox.resolveRelays', { target: target }).then(function(m){ if (m.plan) return m.plan; var r = { relays: [], source: 'policy' }; if (m.error) r.error = m.error; return r; });
      }
    },
    storage: {
      // SDK method names are getItem/setItem/removeItem/keys; the wire types are storage.get/set/remove/keys.
      getItem: function(key){ return field(call('storage.get', { key: key }), 'value'); },
      setItem: function(key, value){ return call('storage.set', { key: key, value: value }).then(function(){}); },
      removeItem: function(key){ return call('storage.remove', { key: key }).then(function(){}); },
      keys: function(){ return field(call('storage.keys'), 'keys'); },
      instance: {
        getItem: function(key){ return field(call('storage.get', { key: key, scope: 'instance' }), 'value'); },
        setItem: function(key, value){ return call('storage.set', { key: key, value: value, scope: 'instance' }).then(function(){}); },
        removeItem: function(key){ return call('storage.remove', { key: key, scope: 'instance' }).then(function(){}); },
        keys: function(){ return field(call('storage.keys', { scope: 'instance' }), 'keys'); }
      }
    },
    // value.payInvoice is an Amethyst-specific extension (not part of @napplet/shim).
    value: {
      payInvoice: function(invoice){ return field(call('value.payInvoice', { invoice: invoice }), 'preimage'); }
    },
    resource: {
      info: function(){ return field(call('resource.info'), 'info'); },
      // The web binding converts canonical wire base64 into actual browser bytes.
      bytes: function(url, opts){ return resourceCall('resource.bytes', { url: url }, opts).then(function(m){ return resourceBlob(m.blob, m.mime); }); },
      bytesMany: function(urls, opts){ return resourceCall('resource.bytesMany', { urls: Array.from(urls || []) }, opts).then(function(m){ return m.items.map(function(item){ if (item.ok) { try { item.blob = resourceBlob(item.blob, item.mime); } catch (_) { return { url: item.url, ok: false, error: 'decode-failed' }; } } return item; }); }); },
      bytesAsObjectURL: function(url){
        var objectUrl = '', revoked = false;
        var handle = { url: '', revoke: function(){ if (revoked) return; revoked = true; if (objectUrl) URL.revokeObjectURL(objectUrl); } };
        var ready = available.resource.bytes(url).then(function(blob){
          if (revoked) return;
          objectUrl = URL.createObjectURL(blob); handle.url = objectUrl; return objectUrl;
        });
        Object.defineProperty(handle, 'ready', { value: ready, enumerable: false });
        return handle;
      }
    },
    // NAP-COMMON. Results are the spec's objects, `ok` and `error` included, so a refusal resolves
    // with `ok: false` instead of rejecting; `follows()` resolves to the hex keys.
    common: {
      encodeNip19: function(input){ return callResult('common.encodeNip19', { input: input }).then(commonResult); },
      decodeNip19: function(value){ return callResult('common.decodeNip19', { value: value }).then(commonResult); },
      getProfile: function(target){ return callResult('common.getProfile', { target: target }).then(commonResult); },
      follows: function(){
        return callResult('common.follows').then(function(m){ if (m.ok === false) throw new Error(m.error || 'unsupported'); return m.pubkeys || []; });
      },
      follow: function(){ return callResult('common.follow', { pubkeys: commonKeys(arguments) }).then(commonResult); },
      unfollow: function(){ return callResult('common.unfollow', { pubkeys: commonKeys(arguments) }).then(commonResult); },
      react: function(id, reaction, customEmojiHref){
        var env = { targetEventId: id, reaction: reaction }; if (customEmojiHref !== undefined) env.customEmojiHref = customEmojiHref;
        return callResult('common.react', env).then(commonResult);
      },
      // Takes a CommonReportTarget, or the shorthand: an npub for a user, a hex id for an event.
      report: function(target, reason, text){
        if (typeof target === 'string') target = target.indexOf('npub1') === 0 ? { type: 'pubkey', pubkey: target } : { type: 'event', id: target };
        return callResult('common.report', { target: target, reason: reason, text: text || '' }).then(commonResult);
      }
    },
    // NAP-UPLOAD. `request.data` (a Blob, ArrayBuffer or typed array) crosses as base64 text, which
    // the bridge carries; the napplet never encodes it. Results are the spec's UploadResult objects.
    upload: {
      info: function(){ return field(call('upload.info'), 'info'); },
      upload: function(request){
        if (!request || typeof request !== 'object') return Promise.reject(new TypeError('upload request must be an object'));
        return uploadBytes(request.data).then(function(bytes){
          var req = { dataBase64: bytesToB64(bytes) };
          var mime = request.mimeType || (typeof Blob !== 'undefined' && request.data instanceof Blob ? request.data.type : '');
          if (mime) req.mimeType = mime;
          ['rail', 'filename', 'caption', 'noTransform', 'metadata'].forEach(function(k){ if (request[k] !== undefined) req[k] = request[k]; });
          return field(call('upload.upload', { request: req }), 'result');
        });
      },
      status: function(uploadId){ return field(call('upload.status', { uploadId: uploadId }), 'status'); },
      onStatus: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        uploadHandlers.push(handler);
        return { close: function(){ var i = uploadHandlers.indexOf(handler); if (i >= 0) uploadHandlers.splice(i, 1); } };
      }
    },
    // NAP-FS: the files the user picks for this napplet. File bytes cross as standard base64 text both
    // ways, as the web binding carries them (a Uint8Array or ArrayBuffer given to write is encoded
    // here). A failure rejects with the NAP-FS error code ('cancelled', 'not-found', ...) as its message.
    fs: {
      info: function(){ return field(call('fs.info'), 'info'); },
      pickFile: function(options){ return field(call('fs.pickFile', { options: options }), 'result'); },
      pickFiles: function(options){ return field(call('fs.pickFiles', { options: options }), 'result'); },
      pickDirectory: function(options){ return field(call('fs.pickDirectory', { options: options }), 'result'); },
      pickSaveFile: function(options){ return field(call('fs.pickSaveFile', { options: options }), 'result'); },
      stat: function(path){ return field(call('fs.stat', { path: path }), 'metadata'); },
      list: function(path){ return field(call('fs.list', { path: path }), 'entries'); },
      read: function(path, options){ return field(call('fs.read', { path: path, options: options }), 'result'); },
      write: function(path, data, options){
        var encoded = typeof data === 'string' ? data : bytesToB64(data instanceof ArrayBuffer ? new Uint8Array(data) : data);
        return field(call('fs.write', { path: path, data: encoded, options: options }), 'result');
      },
      mkdir: function(path, options){ return call('fs.mkdir', { path: path, options: options }).then(function(){}); },
      remove: function(path, recursive){ return call('fs.remove', { path: path, recursive: recursive }).then(function(){}); },
      move: function(fromPath, toPath){ return call('fs.move', { fromPath: fromPath, toPath: toPath }).then(function(){}); },
      watch: function(path, options){ return field(call('fs.watch', { path: path, options: options }), 'watchId'); },
      unwatch: function(watchId){ return call('fs.unwatch', { watchId: watchId }).then(function(){}); },
      onChanged: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        fsHandlers.push(handler);
        return { close: function(){
          var i = fsHandlers.indexOf(handler); if (i >= 0) fsHandlers.splice(i, 1);
        } };
      }
    },
    inc: incDomain,
    intent: intentDomain,
    theme: {
      get: function(){ return field(call('theme.get'), 'theme'); },
      onChanged: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        themeHandlers.push(handler);
        return { close: function(){
          var i = themeHandlers.indexOf(handler); if (i >= 0) themeHandlers.splice(i, 1);
        } };
      }
    }
  };
  var requested = [];
  try { if (Array.isArray(window.__nappletDomains)) requested = window.__nappletDomains; } catch (_) {}
  var napplet = {};
  requested.forEach(function(domain){
    if (typeof domain === 'string' && Object.prototype.hasOwnProperty.call(available, domain)) napplet[domain] = available[domain];
  });
  window.napplet = Object.freeze(napplet);
  // Tells the runtime this napplet can take intent deliveries (they wait here until onDelivery).
  if (napplet.intent) post('intent.ready');

  // ---- IME agent (in-app browser only) -------------------------------------------------------
  // The embedded browser surface renders cross-process via SurfaceControlViewHost, which forwards
  // touch but NOT the soft keyboard (the embedded window can't be an IME target). So the host shows
  // the keyboard in the main app window and relays editing here, where we apply it to the focused
  // field with real input/composition events. Installed on any EMBEDDED surface (browser via the direct
  // bridge, napplet/nSite via the shell relay); both transports go through send(). The full-screen
  // hosts set neither flag (they have a native keyboard).
  var IME_PROXY = false; try { IME_PROXY = !!window.__nappletImeProxy; } catch (_) {}
  if (IME_PROXY) (function(){
    var el = null;            // the focused editable element, or null
    var inComposition = false;
    function perfNow(){ try { return performance.now(); } catch (_) { return 0; } }

    function isEditable(n){
      if (!n) return false;
      if (n.isContentEditable) return true;
      var t = (n.tagName || '').toUpperCase();
      if (t === 'TEXTAREA') return true;
      if (t === 'INPUT') {
        var ty = (n.type || 'text').toLowerCase();
        return ['text','search','url','email','tel','password','number',''].indexOf(ty) >= 0;
      }
      return false;
    }
    function isCE(n){ return !!(n && n.isContentEditable); }
    function valOf(n){ return isCE(n) ? ceText(n) : (n.value || ''); }
    // Controlled-input frameworks (React, Preact, …) install an INSTANCE-level `value` setter on the
    // <input>/<textarea> that records the last value they wrote, and then suppress their onChange whenever
    // the element's value already equals that recorded value. A plain `n.value = v` assignment goes through
    // that tracker, so our programmatic edit looks like a no-op to the framework: onChange never fires, its
    // state stays stale, and on the next render it reconciles the field straight back to the stale value —
    // wiping what we just typed. Writing through the NATIVE prototype setter sets the real value without
    // touching the tracker, so the framework's input handler sees value != tracked, detects the change, and
    // commits it. Identical to `n.value = v` for plain (non-framework) pages.
    var nativeInputValueSet = (function(){ try { return Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set; } catch (_) { return null; } })();
    var nativeAreaValueSet = (function(){ try { return Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set; } catch (_) { return null; } })();
    function setVal(n, v){
      if (isCE(n)) { n.textContent = v; return; }
      var set = (typeof HTMLTextAreaElement !== 'undefined' && n instanceof HTMLTextAreaElement) ? nativeAreaValueSet : nativeInputValueSet;
      if (set) { try { set.call(n, v); return; } catch (_) {} }
      n.value = v;
    }

    // --- contenteditable selection/replacement, mapped through char offsets into the field's text ---
    // We can't use setSelectionRange/value on a contenteditable root; instead we map a char offset into the
    // root's text to a DOM (node, offset) position, so we mutate in place and preserve the surrounding element
    // structure + caret rather than blowing away the content. The text is the text nodes in order with each
    // <br> as "\n" (a line break the user typed), except the trailing placeholder <br> a browser keeps so an
    // empty last line can hold the caret — the same text innerText shows, without its layout cost.
    // Within one edit the shim reads the same, unchanged DOM several times (its text, the selection, the
    // caret it sets); each read is a full walk of a long field. holdLeaves() keeps one walk for those reads and
    // dropLeaves() ends it — before anything that mutates the field or lets page code run (dispatching events).
    var heldLeaves = null;
    function holdLeaves(root){ heldLeaves = null; if (isCE(root)) heldLeaves = { root: root, list: ceLeaves(root) }; }
    function dropLeaves(){ heldLeaves = null; }
    function ceLeaves(root){
      if (heldLeaves && heldLeaves.root === root) return heldLeaves.list;
      // Filter in the loop, not through a TreeWalker filter: a JS acceptNode is called for every node and was
      // 85% of a keystroke in a long field. Without a <br> only text nodes matter, so walk just those.
      var out = [], hasBr = root.getElementsByTagName('br').length > 0, n;
      var w = document.createTreeWalker(root, hasBr ? (NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT) : NodeFilter.SHOW_TEXT);
      // Empty text nodes (Range.insertNode leaves one when it splits at a node's end) carry nothing; skipping
      // them keeps "the last leaf" meaningful for the placeholder checks.
      while ((n = w.nextNode())) if (n.nodeType === 3 ? n.data.length : n.nodeName === 'BR') out.push(n);
      return out;
    }
    // A <br> with no text after it is the placeholder for an empty last line, not a typed line break.
    function leafLen(leaves, i){
      var n = leaves[i];
      if (n.nodeType === 3) return n.data.length;
      for (var j = i + 1; j < leaves.length; j++) if (leaves[j].nodeType === 3 ? leaves[j].data.length : 1) return 1;
      return 0;
    }
    function ceText(root){
      // No <br>: the text is exactly textContent, which the engine produces far faster than a walk.
      if (!root.getElementsByTagName('br').length) return root.textContent;
      var leaves = ceLeaves(root), s = '';
      for (var i = 0; i < leaves.length; i++) s += leaves[i].nodeType === 3 ? leaves[i].data : (leafLen(leaves, i) ? '\n' : '');
      return s;
    }
    function brIndex(b){ return Array.prototype.indexOf.call(b.parentNode.childNodes, b); }
    // Char offset within the field's text for a DOM (container, nodeOffset) boundary. One walk that stops at
    // the boundary: a text container is reached directly; an element boundary sits before its child at
    // nodeOffset, so it is reached at that child's first leaf (or the first leaf past the element). Linear —
    // never asks a node for its index among its siblings, which made a many-line field quadratic.
    function ceOffsetOf(root, container, nodeOffset){
      try {
        var leaves = ceLeaves(root), acc = 0;
        var stop = container.nodeType === 3 ? container : (container.childNodes[nodeOffset] || null);
        for (var i = 0; i < leaves.length; i++){
          var n = leaves[i];
          if (container.nodeType === 3) {
            if (n === container) return acc + Math.min(nodeOffset, n.data.length);
            // An empty text node is not a leaf: a boundary in it falls before the next leaf that follows it.
            if (container.data.length === 0 && (container.compareDocumentPosition(n) & Node.DOCUMENT_POSITION_FOLLOWING)) return acc;
          } else if (stop) {
            if (n === stop || stop.contains(n) || (stop.compareDocumentPosition(n) & Node.DOCUMENT_POSITION_FOLLOWING)) return acc;
          } else if (!container.contains(n) && (container.compareDocumentPosition(n) & Node.DOCUMENT_POSITION_FOLLOWING)) {
            return acc;
          }
          acc += leafLen(leaves, i);
        }
        return acc;
      } catch (_) { return 0; }
    }
    // DOM {node, offset} position for a char offset into the field's text.
    function cePoint(root, off){
      var leaves = ceLeaves(root), acc = 0;
      for (var i = 0; i < leaves.length; i++){
        var n = leaves[i], len = leafLen(leaves, i);
        if (n.nodeType === 3) { if (off <= acc + len) return { node: n, offset: off - acc }; }
        else if (off === acc) return { node: n.parentNode, offset: brIndex(n) };
        acc += len;
      }
      if (leaves.length) {
        var last = leaves[leaves.length - 1];
        if (last.nodeType === 3) return { node: last, offset: last.data.length };
        // After the last typed line break: before the placeholder, if there is one.
        return leafLen(leaves, leaves.length - 1) ? { node: last.parentNode, offset: brIndex(last) + 1 } : { node: last.parentNode, offset: brIndex(last) };
      }
      return { node: root, offset: 0 };
    }
    function ceSelOf(root){
      var s = window.getSelection();
      if (!s || s.rangeCount === 0) { var v = ceText(root).length; return [v, v]; }
      var r = s.getRangeAt(0);
      if (!root.contains(r.startContainer) || !root.contains(r.endContainer)) { var v2 = ceText(root).length; return [v2, v2]; }
      var a = ceOffsetOf(root, r.startContainer, r.startOffset);
      return r.collapsed ? [a, a] : [a, ceOffsetOf(root, r.endContainer, r.endOffset)];
    }
    function ceSetSel(root, s, e){
      try {
        var a = cePoint(root, s), b = (e === s) ? a : cePoint(root, e), r = document.createRange();
        r.setStart(a.node, a.offset); r.setEnd(b.node, b.offset);
        var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(r);
      } catch (_) {}
    }
    // Whether "\n" in this field's text renders as a line break, or must be a <br>.
    function ceKeepsNewlines(root){
      try { var ws = getComputedStyle(root).whiteSpace; return ws === 'pre' || ws === 'pre-wrap' || ws === 'pre-line' || ws === 'break-spaces'; }
      catch (_) { return false; }
    }
    // Replace char range [from, to) in root with ins, leaving the rest of the DOM intact. Line breaks become
    // <br> where whitespace collapses, and a break that ends the field gets the placeholder <br> that lets the
    // new empty line show and hold the caret.
    function ceReplace(root, from, to, ins){
      try {
        holdLeaves(root);
        var a = cePoint(root, from), b = (to === from) ? a : cePoint(root, to), r = document.createRange();
        dropLeaves();
        r.setStart(a.node, a.offset); r.setEnd(b.node, b.offset);
        r.deleteContents();
        if (ins) {
          var frag = document.createDocumentFragment(), parts = ceKeepsNewlines(root) ? [ins] : ins.split('\n');
          for (var i = 0; i < parts.length; i++) {
            if (i > 0) frag.appendChild(document.createElement('br'));
            if (parts[i]) frag.appendChild(document.createTextNode(parts[i]));
          }
          var tail = frag.lastChild, breaks = ins.indexOf('\n') >= 0;
          r.insertNode(frag);
          if (!breaks) return;
          // A break we just typed at the very end would itself read as the placeholder (and not show): give
          // it one. Same for a pre-wrap field whose text now ends in "\n", which renders no empty line either.
          var leaves = ceLeaves(root), li = leaves.length - 1;
          var endsInOurBr = tail && tail.nodeName === 'BR' && leaves[li] === tail;
          var endsInNewline = li >= 0 && leaves[li].nodeType === 3 && /\n$/.test(leaves[li].data);
          if (endsInOurBr || endsInNewline) leaves[li].parentNode.insertBefore(document.createElement('br'), leaves[li].nextSibling);
        }
      } catch (_) { root.textContent = ceText(root).slice(0, from) + (ins || '') + ceText(root).slice(to); }
    }

    function selOf(n){
      if (isCE(n)) return ceSelOf(n);
      return [n.selectionStart || 0, n.selectionEnd || 0];
    }
    function placeSel(n, s, e){
      lastSelActivityAt = perfNow();
      if (isCE(n)) ceSetSel(n, s, e);
      else { try { n.setSelectionRange(s, e); } catch (_) {} }
    }
    function setSel(n, s, e){
      // No-op if already there: re-applying the same selection still fires `select`/`selectionchange`, and
      // every such redundant event ripples into a host report → geometry update → toolbar/handle recompose
      // (visible churn). Our re-asserts/re-applies frequently target the current range, so guard them here.
      var cur = selOf(n);
      if (cur[0] === s && cur[1] === e) return;
      lastSelActivityAt = perfNow(); // a real selection write → the field may auto-scroll to reveal it
      if (isCE(n)) ceSetSel(n, s, e);
      else { try { n.setSelectionRange(s, e); } catch (_) {} }
    }
    // The DOM exposes no caret/selection rect for a position inside an <input>/<textarea>, so we mirror the
    // field into a hidden div (same font/padding/wrapping) and measure where a marker span lands — the
    // well-known "textarea-caret-position" technique. Used to place the insertion handle (and drag it).
    var CARET_PROPS = ['direction','boxSizing','width','height','overflowX','overflowY','borderTopWidth','borderRightWidth','borderBottomWidth','borderLeftWidth','paddingTop','paddingRight','paddingBottom','paddingLeft','fontStyle','fontVariant','fontWeight','fontStretch','fontSize','fontSizeAdjust','lineHeight','fontFamily','textAlign','textTransform','textIndent','textDecoration','letterSpacing','wordSpacing','tabSize','MozTabSize'];
    function caretCoords(n, position){
      try {
        var isInput = (n.nodeName || '').toUpperCase() === 'INPUT';
        var computed = window.getComputedStyle(n);
        var div = document.createElement('div');
        var s = div.style;
        s.position = 'absolute'; s.visibility = 'hidden'; s.whiteSpace = isInput ? 'nowrap' : 'pre-wrap'; s.wordWrap = 'break-word'; s.overflow = 'hidden';
        for (var i = 0; i < CARET_PROPS.length; i++) { s[CARET_PROPS[i]] = computed[CARET_PROPS[i]]; }
        var val = valOf(n);
        div.textContent = val.substring(0, position);
        if (isInput) div.textContent = div.textContent.replace(/\s/g, ' ');
        var span = document.createElement('span');
        span.textContent = val.substring(position) || '.';
        div.appendChild(span);
        document.body.appendChild(div);
        var caretL = span.offsetLeft, caretT = span.offsetTop;
        var lineHeight = parseInt(computed.lineHeight) || parseInt(computed.fontSize) || 16;
        document.body.removeChild(div);
        // offsetLeft/Top are measured from the mirror's *inner* (padding) edge, but getBoundingClientRect is the
        // *outer* border box — so add the field's border widths to land on the real caret.
        var bl = parseFloat(computed.borderLeftWidth) || 0, bt = parseFloat(computed.borderTopWidth) || 0;
        var rect = n.getBoundingClientRect();
        var x = rect.left + bl + caretL - n.scrollLeft;
        var top = rect.top + bt + caretT - n.scrollTop;
        return { x: x, top: top, bottom: top + lineHeight };
      } catch (_) { return null; }
    }
    // Inverse: the char offset whose caret is nearest the CSS-px point (x,y). Binary search in reading order
    // (offset increases left-to-right, top-to-bottom), so a handle drag maps back to a cursor position. Y is
    // first clamped into the field's text rows, so dragging the handle (which sits below the line) or off the
    // field keeps the cursor on the nearest line and lets X drive the column — like Android.
    function offsetFromPoint(n, x, y){
      var len = valOf(n).length;
      var first = caretCoords(n, 0), last = caretCoords(n, len);
      if (first && y < first.top) y = (first.top + first.bottom) / 2;
      else if (last && y > last.bottom) y = (last.top + last.bottom) / 2;
      var lo = 0, hi = len;
      while (lo < hi) {
        var mid = (lo + hi) >> 1, c = caretCoords(n, mid);
        if (!c) break;
        if (y < c.top) hi = mid;
        else if (y > c.bottom) lo = mid + 1;
        else if (x < c.x) hi = mid;
        else lo = mid + 1;
      }
      // The search lands on the boundary just RIGHT of x; round to the NEAREST boundary instead (native
      // getOffsetForPosition) so tapping the left half of a glyph doesn't advance the caret past it. Only
      // compare within the same line (skip when lo sits at a wrap, where lo-1 is on the previous row).
      if (lo > 0) {
        var cl = caretCoords(n, lo - 1), cr = caretCoords(n, lo);
        if (cl && cr && cl.top === cr.top && (x - cl.x) < (cr.x - x)) lo = lo - 1;
      }
      return lo;
    }
    // The focused field's bounding box in CSS px (toolbar anchor). When the selection is a bare caret it also
    // carries the caret rect (cx/ct/cb) so the host can show a draggable insertion handle.
    function fieldGeom(n){
      try {
        var b = n.getBoundingClientRect();
        var g = { l: b.left, t: b.top, r: b.right, b: b.bottom, sx: b.left, sb: b.bottom, ex: b.right, eb: b.bottom, vw: window.innerWidth };
        if (!isCE(n)) {
          var sel = selOf(n);
          if (sel[0] === sel[1]) {
            // Bare caret → carry the caret rect so the host shows the insertion handle.
            var c = caretCoords(n, sel[0]);
            if (c) { g.cx = c.x; g.ct = c.top; g.cb = c.bottom; }
          } else {
            // Range → carry the start/end caret feet so the host shows draggable selection handles, and
            // tighten the box to the selected line span so the toolbar anchors above the selection.
            var cs = caretCoords(n, sel[0]), ce = caretCoords(n, sel[1]);
            if (cs && ce) {
              g.rng = true; // marks a real range so the host keeps these feet through Chrome's collapse fight
              g.sx = cs.x; g.sb = cs.bottom; g.ex = ce.x; g.eb = ce.bottom;
              g.t = Math.min(cs.top, ce.top); g.b = Math.max(cs.bottom, ce.bottom);
            }
          }
        }
        return g;
      } catch (_) { return null; }
    }
    // The text fields the keyboard's Next/Previous walk, as Chrome's IME focus advance does: focusable, enabled
    // editable fields (and selects) sharing this field's form — or, for a field outside any form, the others
    // outside forms — in document order.
    function formSiblings(n){
      var form = n.form || null, out = [];
      var all = document.querySelectorAll('input,textarea,select,[contenteditable=""],[contenteditable="true"]');
      for (var i = 0; i < all.length; i++) {
        var c = all[i];
        if (c !== n && (c.disabled || c.readOnly || c.tabIndex < 0 || !c.getClientRects().length)) continue;
        if (c !== n && !(isEditable(c) || c.tagName === 'SELECT')) continue;
        if ((c.form || null) !== form) continue;
        out.push(c);
      }
      return out;
    }
    function neighbour(n, step){
      var list = formSiblings(n), i = list.indexOf(n);
      return (i >= 0 && list[i + step]) || null;
    }
    function focusInfo(n){
      var t = (n.tagName || '').toUpperCase();
      var multiline = isCE(n) || t === 'TEXTAREA';
      var inputType = isCE(n) ? 'text' : (t === 'TEXTAREA' ? 'textarea' : (n.type || 'text').toLowerCase());
      var sel = selOf(n);
      return { type:'ime.focus', inputType: inputType, enterKeyHint: (n.enterKeyHint || ''),
               multiline: multiline, readOnly: !!n.readOnly, hasNext: !multiline && !!neighbour(n, 1), text: valOf(n), selStart: sel[0],
               selEnd: sel[1], geom: fieldGeom(n) };
    }
    // Like `ime.focus`, but for a field that is ALREADY focused: the answer to the host's `ime.resync`, which
    // it asks for when it needs to (re-)take a field whose focus never moved in the page.
    //
    // Carries NO geometry, unlike focusInfo: fieldGeom's caret measurement mirrors the whole field into a
    // hidden div and forces a synchronous layout (~3.5ms on a 40k-char textarea). The host only needs the
    // editing state here; the `ime.carettap` that rides along with a tap carries fresh geometry.
    function refocusInfo(n){
      var t = (n.tagName || '').toUpperCase();
      var sel = selOf(n);
      return { type:'ime.refocus',
               inputType: isCE(n) ? 'text' : (t === 'TEXTAREA' ? 'textarea' : (n.type || 'text').toLowerCase()),
               enterKeyHint: (n.enterKeyHint || ''), multiline: isCE(n) || t === 'TEXTAREA',
               readOnly: !!n.readOnly, hasNext: !(isCE(n) || t === 'TEXTAREA') && !!neighbour(n, 1), text: valOf(n), selStart: sel[0], selEnd: sel[1] };
    }
    // Last selection we either applied (applyState) or already reported, so the asynchronous
    // selectionchange our own setSel triggers doesn't echo back to the host as a fresh edit.
    var lastSel = null;
    // The live non-collapsed field range we protect from Chrome's off-window abandonment (see the
    // selectionchange handler). Tracked centrally so handle-extends and select-all keep it current.
    var lastFieldRange = null, lastFieldAt = -1;
    // When the selection last changed (Chrome-driven OR our own setSel). Forming/re-asserting a selection makes
    // the browser auto-scroll the field to reveal it, firing `scroll` events that are NOT a user content scroll.
    // The hide-on-scroll path uses this to ignore those: hiding the host overlays on a selection-reveal scroll
    // makes the handles/toolbar blink off-and-on every time a selection settles. See onAnyScroll.
    var lastSelActivityAt = -1;
    function sameSel(a, b){ return !!(a && b && a[0] === b[0] && a[1] === b[1]); }
    function noteSel(sel){
      lastSel = sel;
      if (el && !isCE(el)) {
        if (sel[0] !== sel[1]) { lastFieldRange = [sel[0], sel[1]]; lastFieldAt = perfNow(); }
        else { lastFieldRange = null; }
      }
    }
    // The field text the host's mirror holds as far as this side knows: what we last applied or reported.
    var hostText = null;
    function reportState(){
      if (!el) return;
      var sel = selOf(el);
      noteSel(sel);
      hostText = valOf(el);
      send({ type:'ime.state', text: valOf(el), selStart: sel[0], selEnd: sel[1], geom: fieldGeom(el) });
    }

    document.addEventListener('focusin', function(e){
      if (isEditable(e.target)) {
        el = e.target; inComposition = false; lastSel = selOf(el); hostText = valOf(el); watchField(el); send(focusInfo(el));
        // Focusing a field clears any page-text selection in the browser. The page selectionchange handler is
        // muted while a field is focused (el is set), so it never emits the `active:false` — emit it here, or
        // the host's page handles + Copy bar linger ABOVE the field overlays (and, being z-above, steal the
        // caret/selection-handle drag so the caret can't be moved).
        if (pageSelText) { lastPageRange = null; sendPageSel(false, null); }
        // Cancel any in-flight scroll-hide from the page phase so the new field overlays aren't suppressed by a
        // stale `scrolling` state (its settle would otherwise keep getting re-armed by the field reveal-scrolls).
        scrolling = false; if (scrollTimer) { clearTimeout(scrollTimer); scrollTimer = null; }
        // The host shrinks the surface for the keyboard, but also nudge the field into view in case IME
        // insets aren't delivered (some hosts) so it never sits behind the keyboard.
        try { el.scrollIntoView({ block: 'center', inline: 'nearest' }); } catch (_) {}
      } else if (el) { el = null; inComposition = false; watchField(null); send({ type:'ime.blur' }); }
    }, true);
    document.addEventListener('focusout', function(e){
      if (e.target === el) { el = null; inComposition = false; watchField(null); send({ type:'ime.blur' }); }
    }, true);
    // The page (its own JS, autofill) changed the field: resync the host keyboard's view of it.
    document.addEventListener('input', function(e){ if (e.target === el && !el.__nappletIme) reportState(); }, true);
    // ...and the page's JS writing the value directly, which fires no `input`: a chat composer clearing itself
    // after sending (`textarea.value = ''`, what React/Preact do on a state reset). Without this the host kept the
    // sent text, and the next keystroke wrote it all back. Wraps the prototype setters, which frameworks capture
    // when they mount (after this document-start script), so their writes come through here too; our own writes
    // use the native setters captured above, so they never do. Coalesced to one report per task.
    var writeReportQueued = false;
    function onPageWrite(n){
      if (n !== el || n.__nappletIme || writeReportQueued) return;
      writeReportQueued = true;
      // Frameworks often write back the value the field already holds (Vue, Angular on every input): that
      // changes nothing the host needs, and a report costs a caret-geometry layout.
      Promise.resolve().then(function(){ writeReportQueued = false; if (el === n && valOf(n) !== hostText) reportState(); });
    }
    [window.HTMLInputElement, window.HTMLTextAreaElement].forEach(function(C){
      try {
        var d = Object.getOwnPropertyDescriptor(C.prototype, 'value');
        if (!d || !d.set || !d.configurable) return;
        Object.defineProperty(C.prototype, 'value', {
          configurable: true, enumerable: d.enumerable, get: d.get,
          set: function(v){ d.set.call(this, v); onPageWrite(this); }
        });
      } catch (_) {}
    });
    // Mirror selection changes inside the focused editable to the host. Off-window Chrome abandons a field
    // selection by collapsing the caret to one of its endpoints; we re-assert it RIGHT HERE, synchronously,
    // the same way the page-text path does — reverting before the collapse paints, so it doesn't blink (the
    // old path round-tripped through the host EditText, leaving a visible collapsed frame each cycle). The
    // host's own re-assert in RemoteImeView.onPageState stays as a fallback for collapses we don't catch.
    document.addEventListener('selectionchange', function(){
      if (!el || el.__nappletIme) return; // our own applyState/setSel
      lastSelActivityAt = perfNow(); // Chrome moved the selection → an imminent reveal-scroll isn't a user scroll
      var sel = selOf(el);
      if (sameSel(sel, lastSel)) return; // echo of what we just applied
      if (!isCE(el) && lastFieldRange && sel[0] === sel[1] &&
          (sel[0] === lastFieldRange[0] || sel[0] === lastFieldRange[1]) &&
          (perfNow() - lastFieldAt) < 1500) {
        // Chrome's off-window abandonment collapsed our live range to an endpoint → snap it back
        // synchronously (reverts before paint, so no blink) and keep the window open. The host already
        // holds this range, so we don't re-report (which would feed the slow round-trip loop).
        el.__nappletIme = true;
        setSel(el, lastFieldRange[0], lastFieldRange[1]);
        el.__nappletIme = false;
        lastSel = selOf(el);
        lastFieldAt = perfNow();
        return;
      }
      reportState();
    }, true);
    // Tap handling on the focused editable. A single tap collapses any selection to a caret at the tap point
    // and shows the insertion handle (native; off-window Chrome won't collapse-on-tap itself). A DOUBLE tap
    // selects the word — but `click` fires before `dblclick`, so instead of guessing with timing we DEFER the
    // collapse and let the real `dblclick` cancel it. This is robust to Chrome's own double-click timing
    // (a timing guess raced it and sometimes ate the word selection → "cursor jumps to end of word").
    var collapseTimer = null;
    function clearCollapse() { if (collapseTimer) { clearTimeout(collapseTimer); collapseTimer = null; } }
    document.addEventListener('dblclick', function(e){
      if (!el || isCE(el)) return;
      clearCollapse(); // a real double-tap → don't collapse; keep Chrome's word selection
      var s = selOf(el);
      if (s[0] !== s[1] && !sameSel(s, lastSel)) reportState(); // report the word only if not already sent
    }, true);
    document.addEventListener('click', function(e){
      if (!el) return;
      // A tap inside the ALREADY-focused editable fires no `focusin`, so the host — whose only keyboard-raising
      // signal used to be `ime.focus` — never learned that the user wants the keyboard back after dismissing it
      // (or after a tab switch dropped it). Ring the doorbell on every such tap; the host puts the keyboard
      // back, and asks for a resync first if it no longer mirrors this field.
      //
      // Deliberately payload-free: this fires on every tap in a field, including the tap that focused it (the
      // host answers that one with a no-op — telling the two apart here would take a focusin-to-click timing
      // guess, and a guess that lands late swallows a real "give me the keyboard back" tap). Attaching the
      // editing state would put the field's whole text — 40KB for a long textarea — on the wire per tap.
      if (e.target === el || (el.contains && el.contains(e.target))) send({ type:'ime.wantkb' });
      if (isCE(el) || e.target !== el) return;
      var sel = selOf(el);
      if (sel[0] !== sel[1]) {
        // Tap landed on a selection. Defer the collapse: if a dblclick follows (within the tap window) it
        // cancels this and the word stays selected; otherwise this fires and collapses to the tapped offset.
        var x = e.clientX, y = e.clientY;
        clearCollapse();
        collapseTimer = setTimeout(function(){
          collapseTimer = null;
          if (!el) return;
          var s = selOf(el);
          if (s[0] === s[1]) return; // already collapsed
          var off = offsetFromPoint(el, x, y);
          el.__nappletIme = true;
          setSel(el, off, off);
          el.__nappletIme = false;
          lastSel = selOf(el);
          reportState();
          send({ type:'ime.carettap', geom: fieldGeom(el) });
        }, 300);
      } else {
        // Tap on a bare caret → (re-)show the insertion handle. If a double-tap follows, dblclick selects the
        // word and supersedes this.
        send({ type:'ime.carettap', geom: fieldGeom(el) });
      }
    }, true);

    // Returns false when the page cancelled the key (preventDefault), the way a native keystroke reports it.
    function key(n, type){
      try { return n.dispatchEvent(new KeyboardEvent(type, { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13, charCode: type === 'keypress' ? 13 : 0 })); }
      catch (_) { return true; }
    }
    // Like a native line break, announced first and cancelable: editors built on contenteditable (and some on
    // textareas) take Enter here, from `beforeinput`, rather than from the key.
    function beforeLineBreak(n, inputType){
      try { return n.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: inputType, data: null })); }
      catch (_) { return true; }
    }
    // The keyboard's Enter, in Chrome's order: keydown, keypress, then the default action — a cancelable
    // `beforeinput` and the line break — only as far as the page lets each step through. Chat composers send
    // on a keydown Enter and cancel it; rich editors (Brainstorm's search box is a contenteditable) send from
    // `beforeinput` insertParagraph. Inserting the line break straight away, as this used to, let neither see
    // the Enter: it made a new line instead of sending or searching.
    function enter(n){
      if (!n) return;
      var t = (n.tagName || '').toUpperCase();
      var multiline = isCE(n) || t === 'TEXTAREA';
      var proceed = key(n, 'keydown') && key(n, 'keypress') &&
        (!multiline || beforeLineBreak(n, isCE(n) ? 'insertParagraph' : 'insertLineBreak'));
      if (proceed) {
        if (multiline) {
          var s = selOf(n);
          if (isCE(n)) ceReplace(n, s[0], s[1], '\n');
          else { var v = valOf(n); setVal(n, v.slice(0, s[0]) + '\n' + v.slice(s[1])); }
          // Not flagged as ours, so the input listener reports the new line back to the host's mirror,
          // which never inserted it.
          setSel(n, s[0] + 1, s[0] + 1); fireInput(n, 'insertLineBreak', '\n', false);
        } else if (n.form) {
          try { (n.form.requestSubmit ? n.form.requestSubmit() : n.form.submit()); } catch (_) {}
        }
      }
      key(n, 'keyup');
    }
    function fireInput(n, inputType, data, isComposing, dataTransfer){
      try { n.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: inputType, data: data == null ? null : data, isComposing: !!isComposing, dataTransfer: dataTransfer || null })); }
      catch (_) { n.dispatchEvent(new Event('input', { bubbles: true })); }
    }
    function fireComp(n, type, data){
      try { n.dispatchEvent(new CompositionEvent(type, { bubbles: true, cancelable: false, data: data || '' })); } catch (_) {}
    }
    function isHigh(c){ return c >= 0xD800 && c <= 0xDBFF; }
    function isLow(c){ return c >= 0xDC00 && c <= 0xDFFF; }
    // Common-prefix/suffix diff so we can classify the edit (insert vs delete vs replace). Operates on
    // UTF-16 code units but snaps both boundaries off surrogate halves, so a changed astral char (emoji,
    // CJK-supplement) is never split into a lone surrogate in `inserted`/`removed` or the replace range.
    function diff(prev, next){
      var pl = prev.length, nl = next.length, p = 0;
      while (p < pl && p < nl && prev.charCodeAt(p) === next.charCodeAt(p)) p++;
      if (p > 0 && isHigh(prev.charCodeAt(p - 1))) p--; // don't cut a pair at the prefix boundary
      var s1 = pl, s2 = nl;
      while (s1 > p && s2 > p && prev.charCodeAt(s1 - 1) === next.charCodeAt(s2 - 1)) { s1--; s2--; }
      // pl - s1 === nl - s2 (suffixes share length), so s1 < pl iff s2 < nl.
      if (s1 < pl && (isLow(prev.charCodeAt(s1)) || isLow(next.charCodeAt(s2)))) { s1++; s2++; }
      return { from: p, prevEnd: s1, inserted: next.slice(p, s2), removed: prev.slice(p, s1) };
    }
    // Adopt the host's authoritative editing STATE (Flutter's model) and synthesize the matching DOM
    // input/composition events so the page's framework reacts as if typed natively.
    function applyState(msg, kind){
      if (!el) return;
      var n = el;
      dropLeaves();
      // The page dropped the field without a blur (it re-rendered it): nothing typed can reach it any more, so
      // let the host put its keyboard away, as Chrome does when the focused element goes.
      if (!n.isConnected) { el = null; inComposition = false; watchField(null); send({ type:'ime.blur' }); return; }
      holdLeaves(n);
      var prev = valOf(n);
      var next = (msg.text != null) ? String(msg.text) : prev;
      var cs = (msg.composingStart != null) ? msg.composingStart : -1;
      var ce = (msg.composingEnd != null) ? msg.composingEnd : -1;
      var composingActive = (cs >= 0 && ce > cs);
      var d = diff(prev, next);
      var cancelled = false;
      var write = next, selS = msg.selStart, selE = msg.selEnd;
      // maxlength caps what the USER can type or paste, not what script writes — and the shim writes through
      // the value setter, so it has to apply the cap itself: keep the part of the insertion that fits, as the
      // browser does. The post-check below then hands the host the shortened text.
      if (!isCE(n) && n.maxLength >= 0 && d.inserted && !composingActive) {
        var room = n.maxLength - (prev.length - d.removed.length);
        if (d.inserted.length > room) {
          var keep = room > 0 ? d.inserted.slice(0, room) : '';
          if (keep && isHigh(keep.charCodeAt(keep.length - 1))) keep = keep.slice(0, -1); // never split a pair
          write = prev.slice(0, d.from) + keep + prev.slice(d.prevEnd);
          d = diff(prev, write);
          selS = selE = d.from + keep.length;
        }
      }
      var cur = selOf(n); // the selection the edit starts from; read once, the walks are the cost here
      dropLeaves();
      var editType = composingActive ? 'insertCompositionText' : (kind && kind.inputType) || editTypeOf(cur, d);

      n.__nappletIme = true;
      try {
        if (composingActive && !inComposition) { inComposition = true; fireComp(n, 'compositionstart', ''); }
        // A typed character or a backspace outside a composition is announced first, cancelably, as Chrome
        // does. Rich editors act there instead of in `input` — Brainstorm's search box deletes a whole filter
        // chip on the backspace next to it — and input masks refuse characters there.
        if (write !== prev && !composingActive && !inComposition) cancelled = !beforeEdit(n, d, editType, kind && kind.data, cur);
        if (write !== prev && !cancelled) {
          if (isCE(n)) ceReplace(n, d.from, d.prevEnd, d.inserted); // in-place, preserves structure
          else setVal(n, write);
          if (inComposition) fireComp(n, 'compositionupdate', next.slice(cs, ce));
          fireInput(n, editType, kind && kind.data ? null : d.inserted, composingActive, kind && kind.data);
        }
        // After a text change the caret has moved anyway, so set it without setSel's "already there?" read.
        if (!cancelled) { if (write !== prev) placeSel(n, selS, selE); else setSel(n, selS, selE); }
        if (!composingActive && inComposition) { inComposition = false; fireComp(n, 'compositionend', d.inserted || ''); }
      } finally { n.__nappletIme = false; holdLeaves(n); noteSel(selOf(n)); }
      var after = valOf(n);
      dropLeaves();
      hostText = next;
      if (ceObserver) ceObserver.takeRecords(); // our own edits, not the page's
      // The page rewrote the field while handling the edit (a cancelled edit it performed itself, an editor
      // re-rendering its text into chips, a mask): the host's mirror no longer matches, and its next edit would
      // be diffed against text the field doesn't have. Hand it the field as it really is.
      if (!composingActive && !(kind && kind.reports) && after !== next) reportState();
    }

    // The inputType a native edit of this shape reports: a forward delete leaves the caret where it was, at
    // the start of the removed text; a backspace (or deleting a range) is backward.
    function editTypeOf(cur, d){
      if (d.inserted && !d.removed) return 'insertText';
      if (d.removed && !d.inserted) return (cur[0] === d.from && cur[1] === d.from) ? 'deleteContentForward' : 'deleteContentBackward';
      return 'insertReplacementText';
    }
    function beforeEdit(n, d, type, dataTransfer, cur){
      // The selection a native edit starts from: the caret after the backspaced text (or before a forward
      // delete), or the range the inserted text replaces. Already true when the mirror is in sync.
      var collapsedAt = type === 'deleteContentBackward' ? d.prevEnd : d.from;
      var inPlace = (d.prevEnd === d.from) ? (cur[0] === d.from && cur[1] === d.from)
        : ((cur[0] === d.from && cur[1] === d.prevEnd) || (cur[0] === collapsedAt && cur[1] === collapsedAt));
      if (!inPlace) setSel(n, d.from, d.prevEnd);
      try { return n.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: type, data: dataTransfer ? null : (d.inserted || null), dataTransfer: dataTransfer || null })); }
      catch (_) { return true; }
    }

    function clipboardData(text){
      try { var dt = new DataTransfer(); if (text != null) dt.setData('text/plain', text); return dt; } catch (_) { return null; }
    }
    // Paste and cut from the host's toolbar or a hardware keyboard, in Chrome's order: the cancelable clipboard
    // event (pages clean pastes there, or take images), then the edit as insertFromPaste / deleteByCut. The host
    // left its own buffer alone, so it is handed the result either way.
    function paste(n, text){
      if (!n || n.readOnly) return;
      var dt = clipboardData(text), ok = true;
      try { ok = n.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: dt })); } catch (_) {}
      if (ok && n.isConnected) {
        var s = selOf(n), prev = valOf(n), next = prev.slice(0, s[0]) + text + prev.slice(s[1]);
        // As in Chrome: an <input>/<textarea> paste reports its text as `data`, a contenteditable's as `dataTransfer`.
        applyState({ text: next, selStart: s[0] + text.length, selEnd: s[0] + text.length }, { inputType: 'insertFromPaste', data: isCE(n) ? dt : null, reports: true });
      }
      if (el === n) reportState();
    }
    function cut(n){
      if (!n || n.readOnly) return;
      var s = selOf(n), ok = true;
      if (s[0] === s[1]) return;
      try { ok = n.dispatchEvent(new ClipboardEvent('cut', { bubbles: true, cancelable: true, clipboardData: clipboardData(valOf(n).slice(s[0], s[1])) })); } catch (_) {}
      if (ok && n.isConnected) {
        var prev = valOf(n);
        applyState({ text: prev.slice(0, s[0]) + prev.slice(s[1]), selStart: s[0], selEnd: s[0] }, { inputType: 'deleteByCut', reports: true });
      }
      if (el === n) reportState();
    }

    // A contenteditable the page changes on its own (a chip editor re-rendering after a pick, a reset) fires
    // no `input`; watch its DOM and resync the host when its text drifts from what the mirror holds. Only the
    // focused one is observed (see watchField), so the rest of the page's DOM churn costs nothing here.
    var ceObserver = null;
    try {
      ceObserver = new MutationObserver(function(){
        if (!el || !isCE(el) || el.__nappletIme || inComposition) return;
        if (valOf(el) !== hostText) reportState();
      });
    } catch (_) { ceObserver = null; }
    function watchField(n){
      if (!ceObserver) return;
      ceObserver.disconnect();
      if (n && isCE(n)) ceObserver.observe(n, { subtree: true, childList: true, characterData: true });
    }

    // --- Page (non-editable) text selection re-hosting ---
    // Chrome can't present its selection handles/toolbar in the cross-process embedded surface, so a
    // long-press on ordinary page text selects a word and then ~60ms later abandons (collapses) it, the
    // same way it does inside inputs. Mirror the document selection: re-assert it when it collapses right
    // after forming, and report the selected text so the host can show its own Copy bar over the page.
    var pageSelText = '', lastPageRange = null, lastPageAt = -1, pageReasserting = false;
    // Selection geometry in CSS px (viewport coords). The host maps these to screen px (scale = surface
    // width / vw) to draw the toolbar above the selection and a handle at each end. l/t/r/b is the bounding
    // box; (sx,sb) the start-caret foot, (ex,eb) the end-caret foot; vw lets the host derive the scale.
    function pageGeom(r){
      try {
        var b = r.getBoundingClientRect();
        var sr = r.cloneRange(); sr.collapse(true); var s = sr.getBoundingClientRect();
        var er = r.cloneRange(); er.collapse(false); var e = er.getBoundingClientRect();
        return { l: b.left, t: b.top, r: b.right, b: b.bottom, sx: s.left, sb: s.bottom, ex: e.left, eb: e.bottom, vw: window.innerWidth };
      } catch (_) { return null; }
    }
    function sendPageSel(active, r){
      var text = active ? String(window.getSelection()) : '';
      pageSelText = text;
      send({ type: 'ime.pagesel', active: active, text: text, geom: active && r ? pageGeom(r) : null });
    }
    document.addEventListener('selectionchange', function(){
      if (el || pageReasserting) return; // selections inside an editable are handled above
      lastSelActivityAt = perfNow(); // page selection moved → an imminent reveal-scroll isn't a user scroll
      var s = window.getSelection();
      if (s && s.rangeCount && !s.isCollapsed) {
        var r = s.getRangeAt(0);
        lastPageRange = r.cloneRange(); lastPageAt = perfNow();
        sendPageSel(true, r);
      } else if (lastPageRange && (perfNow() - lastPageAt) < 400) {
        pageReasserting = true;
        try { s.removeAllRanges(); s.addRange(lastPageRange); } catch (_) {}
        pageReasserting = false;
        lastPageAt = perfNow();
      } else if (pageSelText) {
        lastPageRange = null;
        sendPageSel(false, null);
      }
    }, true);
    // Host drag of a selection handle: move the dragged edge to the text position under (x,y) CSS px,
    // keeping the opposite edge anchored. setBaseAndExtent tolerates either drag direction.
    function pageExtend(edge, x, y){
      try {
        var pt = document.caretRangeFromPoint && document.caretRangeFromPoint(x, y);
        var s = window.getSelection();
        if (!pt || !s.rangeCount) return;
        var cur = s.getRangeAt(0);
        var aN, aO;
        if (edge === 'start') { aN = cur.endContainer; aO = cur.endOffset; } else { aN = cur.startContainer; aO = cur.startOffset; }
        pageReasserting = true;
        s.setBaseAndExtent(aN, aO, pt.startContainer, pt.startOffset);
        pageReasserting = false;
        if (!s.isCollapsed) {
          var nr = s.getRangeAt(0);
          lastPageRange = nr.cloneRange(); lastPageAt = perfNow();
          sendPageSel(true, nr);
        }
      } catch (_) {}
    }

    // Word-granularity snapping (native: dragging a word selection's handle extends a word at a time). The
    // end handle snaps to the end of the word at/after the offset; the start handle to the start of the word
    // at/before it. Whitespace between words extends to the adjacent word so you never stop mid-gap.
    function isWordChar(c){ return c != null && /\S/.test(c); }
    function wordEndAt(text, off){
      var i = off;
      while (i < text.length && !isWordChar(text[i])) i++;
      while (i < text.length && isWordChar(text[i])) i++;
      return i;
    }
    function wordStartAt(text, off){
      var i = off;
      while (i > 0 && !isWordChar(text[i - 1])) i--;
      while (i > 0 && isWordChar(text[i - 1])) i--;
      return i;
    }

    // Per-drag state for the hybrid word/char handle extend below. `fieldDragWordEnd`/`fieldDragWordStart`
    // remember how far the dragged edge has been word-snapped so far this gesture; a >250ms gap between
    // `ime.fieldextend` ops (or a switch of edge) means a NEW drag, so we re-baseline to the live selection.
    var fieldDragAt = -1, fieldDragEdge = null, fieldDragWordEnd = -1, fieldDragWordStart = -1;
    // Host drag of an in-field selection handle: move the dragged edge to the offset under (x,y) CSS px,
    // keeping the other edge anchored, clamped so it can't cross the anchor. HYBRID granularity, matching
    // native `Editor` word-selection drags (#5): the gesture starts anchored to the current selection edge,
    // and as the finger sweeps PAST that word's far boundary it snaps the dragged edge to the next WHOLE word
    // (so sweeping across words grabs them whole and never stops mid-gap); moving WITHIN or back from the
    // furthest-reached word gives CHARACTER precision (so you can fine-tune to a single character).
    function fieldExtend(edge, x, y){
      if (!el || isCE(el)) return;
      try {
        var off = offsetFromPoint(el, x, y);
        var text = valOf(el);
        var sel = selOf(el);
        var now = perfNow();
        var fresh = (now - fieldDragAt > 250) || edge !== fieldDragEdge;
        fieldDragAt = now; fieldDragEdge = edge;
        var s, e;
        if (edge === 'start') {
          e = sel[1];
          if (fresh) fieldDragWordStart = sel[0]; // baseline at the current selection start
          if (off < fieldDragWordStart) { s = wordStartAt(text, off); fieldDragWordStart = s; } // swept into a new word → snap whole
          else s = off; // within / back from the furthest word → character precision
          s = Math.max(0, Math.min(s, e));
        } else {
          s = sel[0];
          if (fresh) fieldDragWordEnd = sel[1]; // baseline at the current selection end
          if (off > fieldDragWordEnd) { e = wordEndAt(text, off); fieldDragWordEnd = e; } // swept into a new word → snap whole
          else e = off; // within / back from the furthest word → character precision
          e = Math.min(text.length, Math.max(e, s));
        }
        el.__nappletIme = true;
        setSel(el, s, e);
        el.__nappletIme = false;
        lastSel = selOf(el);
        reportState();
      } catch (_) {}
    }

    // While the page scrolls, host-drawn selection UI (toolbar + handles) would float at stale positions, so
    // the host hides it on scroll-start and we re-report fresh geometry on scroll-idle so it reappears in the
    // right place — like Android. Only signal when there's a selection to hide (a field range or page text).
    var scrolling = false, scrollTimer = null, autoScrolling = false;
    // How long after a selection change a scroll is treated as the browser's auto-reveal of that selection
    // (not a user content scroll). Generous enough to catch the reveal-scroll that fires a frame or two later.
    var SCROLL_SEL_GUARD_MS = 350;
    function hasSelectionUi(){ return !!pageSelText || !!(el && (function(s){ return s[0] !== s[1]; })(selOf(el))); }
    function onAnyScroll(){
      if (autoScrolling) return; // our own drag-to-edge auto-scroll: keep the overlays up, don't hide them
      if (!hasSelectionUi()) return;
      if ((perfNow() - lastSelActivityAt) < SCROLL_SEL_GUARD_MS) {
        // The browser auto-scrolled to reveal a just-changed selection (forming/re-asserting a range scrolls a
        // textarea). That's not a user content scroll: hiding here would blink the host overlays off-and-on every
        // time a selection settles. Reposition them in place instead (geometry shifted by the reveal-scroll).
        // Crucially we do NOT touch the hide-on-scroll timer: if a real scroll-hide is somehow active, these
        // reveal-scrolls must not keep re-arming it (that would leave the overlays hidden indefinitely).
        if (el) reportState();
        else { var sr = window.getSelection(); if (sr && sr.rangeCount && !sr.isCollapsed) sendPageSel(true, sr.getRangeAt(0)); }
        return;
      }
      if (!scrolling) { scrolling = true; send({ type:'ime.scroll', active: true }); }
      if (scrollTimer) clearTimeout(scrollTimer);
      scrollTimer = setTimeout(function(){
        scrolling = false; scrollTimer = null;
        // Refresh geometry FIRST (so overlays reposition), then tell the host to show them again.
        if (el) reportState();
        else { var s = window.getSelection(); if (s && s.rangeCount && !s.isCollapsed) sendPageSel(true, s.getRangeAt(0)); }
        send({ type:'ime.scroll', active: false });
      }, 150);
    }
    document.addEventListener('scroll', onAnyScroll, true); // capture: any scroller, not just the document

    window.__nappletImeHandle = function(msg){
      // The host needs to (re-)take a field whose focus never left the page — the tab came back on screen, or
      // a tap rang the doorbell above and the host has no mirror for it. No `focusin` will fire for a field
      // that never blurred, so hand it the editing state here.
      if (msg.type === 'ime.resync') { if (el) { hostText = valOf(el); send(refocusInfo(el)); } return; }
      if (msg.type === 'ime.set') applyState(msg);
      else if (msg.type === 'ime.action') enter(el);
      else if (msg.type === 'ime.next' || msg.type === 'ime.prev') { var to = el && neighbour(el, msg.type === 'ime.next' ? 1 : -1); if (to) try { to.focus(); } catch (_) {} }
      else if (msg.type === 'ime.paste') paste(el, String(msg.text || ''));
      else if (msg.type === 'ime.cut') cut(el);
      else if (msg.type === 'ime.pageextend') pageExtend(msg.edge, msg.x, msg.y);
      else if (msg.type === 'ime.fieldextend') fieldExtend(msg.edge, msg.x, msg.y);
      else if (msg.type === 'ime.autoscroll') {
        // Host drag of a handle near the surface's top/bottom edge → scroll the content (the textarea if it
        // scrolls, else the page) so the selection can keep extending, then re-report geometry so the
        // overlays follow. Flagged so our own scroll doesn't trip the hide-on-scroll path above.
        var dy = msg.dy || 0;
        autoScrolling = true;
        try {
          if (el && (el.tagName || '').toUpperCase() === 'TEXTAREA') el.scrollTop += dy;
          window.scrollBy(0, dy);
        } catch (_) {}
        if (el) reportState();
        else { var s = window.getSelection(); if (s && s.rangeCount && !s.isCollapsed) sendPageSel(true, s.getRangeAt(0)); }
        setTimeout(function(){ autoScrolling = false; }, 0);
      }
      else if (msg.type === 'ime.caretmove') {
        if (el && !isCE(el)) {
          var off = offsetFromPoint(el, msg.x, msg.y);
          el.__nappletIme = true;
          setSel(el, off, off);
          el.__nappletIme = false;
          lastSel = selOf(el);
          reportState();
        }
      }
    };
  })();

  // NIP-07 provider (window.nostr), installed only for nSites in website mode (the host sets
  // window.__nappletNip07 synchronously before this shim). Lets standard Nostr web apps "log in with
  // Amethyst" and sign, bridged to the same consent-gated signer: getPublicKey + getRelays reuse the
  // identity reads; signEvent is sign-only (no publish) and honors the app's created_at.
  // nip44 is optional in NIP-07 but not optional in practice: a kind:13 seal is NIP-44 ciphertext
  // authored by the real key, so without it a page can sign yet cannot build a NIP-59 gift wrap —
  // NIP-17 DMs and every gift-wrapped app protocol are simply unreachable. The shell does the crypto
  // and returns only the result; the key never enters the page. nip04 stays deliberately absent
  // (deprecated, and nothing that still needs it should be encouraged).
  if (window.__nappletNip07 && !window.nostr) {
    window.nostr = Object.freeze({
      getPublicKey: function(){ return field(call('identity.getPublicKey'), 'pubkey'); },
      getRelays: function(){ return field(call('identity.getRelays'), 'relays'); },
      signEvent: function(event){ return field(call('nostr.signEvent', { event: event }), 'event'); },
      nip44: Object.freeze({
        encrypt: function(peer, plaintext){ return field(call('nostr.nip44Encrypt', { peer: peer, plaintext: String(plaintext) }), 'value'); },
        decrypt: function(peer, ciphertext){ return field(call('nostr.nip44Decrypt', { peer: peer, ciphertext: String(ciphertext) }), 'value'); }
      })
    });
  }
})();
