/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import kotlinx.serialization.json.JsonPrimitive

/**
 * The messenger's `webxdc.js`, which every webxdc app loads and none ships: `window.webxdc`.
 *
 * Updates stay on this device for now. `sendUpdate` appends to a log kept in the app's own
 * IndexedDB, under a database name an app has no reason to touch, and `setUpdateListener` replays
 * it from the serial the app asks for, then delivers new ones. The app's origin is its own (one
 * per post, per account), so the log is too, and it survives the app being closed or killed.
 * Sharing updates over Nostr (NIP-DC kind 4932) replaces only the transport under this API.
 */
object WebxdcRuntime {
    /** Largest update accepted, serialized; the relays updates will travel through cap events near 128 KB. */
    const val MAX_UPDATE_BYTES = 128_000

    /** The `webxdc.js` for one launch, with who is running it filled in. */
    fun script(
        selfAddr: String,
        selfName: String,
        isAppSender: Boolean,
    ): String =
        "(function(){" +
            "var SELF_ADDR=" + JsonPrimitive(selfAddr) + ",SELF_NAME=" + JsonPrimitive(selfName) +
            ",IS_SENDER=" + isAppSender + ",MAX=" + MAX_UPDATE_BYTES + ";" +
            BODY +
            "})();"

    private val BODY =
        """
        if (window.webxdc) return;
        var updates = [], loaded = false, waiting = [], listener = null, db = null, channel = null;
        function last() { return updates.length ? updates[updates.length - 1].serial : 0; }
        function bySerial(a, b) { return a.serial - b.serial; }
        function ready() { loaded = true; var w = waiting; waiting = []; w.forEach(function (f) { try { f(); } catch (e) { console.error(e); } }); }
        function whenLoaded(f) { if (loaded) f(); else waiting.push(f); }
        function tell(fresh) {
          var max = fresh.length ? fresh[fresh.length - 1].serial : last();
          fresh.forEach(function (u) { if (listener) { try { listener(view(u, max)); } catch (e) { console.error(e); } } });
        }
        // Another document of this app (the note's run and a window share one origin) may have added
        // updates: read whatever is past ours, in order.
        var syncing = false, again = false;
        function sync() {
          if (!db) return;
          if (syncing) { again = true; return; }
          syncing = true;
          function done() { syncing = false; if (again) { again = false; sync(); } }
          try {
            var r = db.transaction('updates', 'readonly').objectStore('updates').getAll(IDBKeyRange.lowerBound(last(), true));
            r.onsuccess = function () {
              var fresh = (r.result || []).sort(bySerial).filter(function (u) { return u.serial > last(); });
              fresh.forEach(function (u) { updates.push(u); });
              tell(fresh);
              done();
            };
            r.onerror = done;
          } catch (e) { done(); }
        }
        try {
          var req = indexedDB.open('__amethyst_webxdc__', 1);
          req.onupgradeneeded = function () { req.result.createObjectStore('updates', { keyPath: 'serial' }); };
          req.onsuccess = function () {
            db = req.result;
            var all = db.transaction('updates', 'readonly').objectStore('updates').getAll();
            all.onsuccess = function () { updates = (all.result || []).sort(bySerial); ready(); };
            all.onerror = ready;
            if (typeof BroadcastChannel === 'function') { channel = new BroadcastChannel('__amethyst_webxdc__'); channel.onmessage = sync; }
          };
          req.onerror = ready;
        } catch (e) { ready(); }
        // The serial is taken inside the write: two documents of one app never take the same one.
        function append(u) {
          if (!db) { u.serial = last() + 1; updates.push(u); tell([u]); return; }
          try {
            var tx = db.transaction('updates', 'readwrite'), store = tx.objectStore('updates');
            var top = store.openCursor(null, 'prev');
            top.onsuccess = function () { u.serial = (top.result ? top.result.key : 0) + 1; store.add(u); };
            tx.oncomplete = function () { sync(); if (channel) channel.postMessage(1); };
            tx.onerror = function () { console.warn('webxdc: update not saved', tx.error); };
          } catch (e) { console.warn('webxdc: update not saved', e); }
        }
        function view(u, max) { var o = { max_serial: max }; for (var k in u) o[k] = u[k]; return o; }
        function lonelyChannel() {
          var listeners = [];
          return {
            setListener: function (cb) { listeners = cb ? [cb] : []; },
            send: function (data) { if (!(data instanceof Uint8Array)) throw new Error('send takes a Uint8Array'); },
            leave: function () { listeners = []; }
          };
        }
        window.webxdc = {
          selfAddr: SELF_ADDR,
          selfName: SELF_NAME,
          isAppSender: IS_SENDER,
          isBroadcast: false,
          sendUpdateInterval: 1000,
          sendUpdateMaxSize: MAX,
          sendUpdate: function (update, descr) {
            var json = JSON.stringify(update && update.payload === undefined ? null : update.payload);
            if (json === undefined) json = 'null';
            if (new TextEncoder().encode(json).length > MAX) { console.warn('webxdc: update larger than sendUpdateMaxSize dropped'); return; }
            var u = { payload: JSON.parse(json) };
            ['info', 'document', 'summary', 'href'].forEach(function (k) { if (update && update[k] != null) u[k] = String(update[k]); });
            whenLoaded(function () { append(u); });
          },
          setUpdateListener: function (cb, serial) {
            listener = cb;
            var from = serial || 0;
            return new Promise(function (resolve) {
              whenLoaded(function () {
                var max = last();
                updates.forEach(function (u) { if (u.serial > from && listener) { try { listener(view(u, max)); } catch (e) { console.error(e); } } });
                resolve();
              });
            });
          },
          getAllUpdates: function () {
            return new Promise(function (resolve) { whenLoaded(function () { var max = last(); resolve(updates.map(function (u) { return view(u, max); })); }); });
          },
          sendToChat: function () { return Promise.reject(new Error('sendToChat is not available yet')); },
          importFiles: function (filter) {
            filter = filter || {};
            return new Promise(function (resolve) {
              var input = document.createElement('input');
              input.type = 'file';
              var accept = [].concat(filter.mimeTypes || [], filter.extensions || []);
              if (accept.length) input.accept = accept.join(',');
              if (filter.multiple) input.multiple = true;
              input.style.display = 'none';
              input.addEventListener('change', function () { resolve(Array.prototype.slice.call(input.files || [])); input.remove(); });
              input.addEventListener('cancel', function () { resolve([]); input.remove(); });
              (document.body || document.documentElement).appendChild(input);
              input.click();
            });
          },
          // Realtime peers need a transport this host doesn't have yet: a channel that reaches no one.
          joinRealtimeChannel: function () { return lonelyChannel(); }
        };
        """.trimIndent()
}
