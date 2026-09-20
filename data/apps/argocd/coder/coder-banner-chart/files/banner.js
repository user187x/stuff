/*
 * Coder announcement banner loader.
 *
 * Injected into Coder's HTML by a Traefik rewrite-body middleware. The dashboard is a client-rendered
 * SPA, so the header does not exist in the server HTML: this script places the banner as the sibling
 * right after the sticky header once React has rendered it, re-placing it whenever React re-renders.
 *
 * Changes arrive instantly over a WebSocket to the banner service (/__banner/live): the tab is sent the
 * current banner on connect and every change the moment an admin publishes it, so nobody has to refresh.
 * If the socket cannot be used, it falls back to polling /__banner/banner.json.
 *
 * Served same-origin because Coder's CSP is `script-src 'self'`. All text is set with textContent.
 */
(function () {
  'use strict';

  var thisScript = document.currentScript;
  var BASE = '/__banner/';
  var BANNER_ID = 'coder-system-banner';
  var STORAGE_KEY = 'coder-banner-dismissed';
  var HEADER_SELECTOR = 'div.sticky.top-0.bg-surface-primary';

  var LEVELS = {
    info: { bg: '#1e40af', fg: '#ffffff' },
    success: { bg: '#166534', fg: '#ffffff' },
    warning: { bg: '#b45309', fg: '#ffffff' },
    critical: { bg: '#b91c1c', fg: '#ffffff' },
  };

  var LIVE_PATH = BASE + 'live';
  var SLOW_POLL_SECONDS = 300; // safety net while the live socket is up
  var SILENCE_MS = 60000; // the server sends a heartbeat every 20 s; this long without a word = dead connection

  var cfg = null;
  var pending = false;
  var announce = false; // the next placement comes from a live push: draw attention to it
  var socket = null;
  var liveUp = false;
  var retries = 0;
  var retryTimer = null;
  var lastMessage = 0;

  function safeUrl(url) {
    return typeof url === 'string' && /^(https?:\/\/|\/(?!\/))/i.test(url) ? url : '';
  }

  // What is drawn, as opposed to which announcement it is: `id` decides whether it was dismissed, this decides
  // whether the element on screen is out of date (e.g. an admin changed only "let people dismiss it").
  function signature(c) {
    return JSON.stringify([c.level, c.title, c.message, c.linkText, c.linkUrl, !!c.dismissible]);
  }

  function isDismissed() {
    if (!cfg.dismissible) return false; // an undismissable banner is always shown, whatever was dismissed before
    try {
      return !!cfg.id && window.localStorage.getItem(STORAGE_KEY) === cfg.id;
    } catch (e) {
      return false;
    }
  }

  function dismiss() {
    try {
      window.localStorage.setItem(STORAGE_KEY, cfg.id);
    } catch (e) {
      /* storage unavailable: the banner just returns on the next load */
    }
    schedule();
  }

  function build(cfg, onDismiss) {
    var level = LEVELS[cfg.level] || LEVELS.info;
    var el = document.createElement('div');
    el.id = BANNER_ID;
    el.setAttribute('role', 'status');
    el.setAttribute('data-banner-id', cfg.id || '');
    el.setAttribute('data-banner-sig', signature(cfg));
    el.style.cssText =
      'display:flex;align-items:center;justify-content:center;gap:12px;flex-shrink:0;' +
      'box-sizing:border-box;width:100%;padding:10px 24px;font-size:14px;line-height:20px;' +
      'text-align:center;background:' + level.bg + ';color:' + level.fg + ';';

    var text = document.createElement('span');
    if (cfg.title) {
      var title = document.createElement('strong');
      title.textContent = cfg.title + ' ';
      text.appendChild(title);
    }
    text.appendChild(document.createTextNode(cfg.message || ''));
    var href = safeUrl(cfg.linkUrl);
    if (href) {
      text.appendChild(document.createTextNode(' '));
      var link = document.createElement('a');
      link.href = href;
      link.textContent = cfg.linkText || href;
      link.style.cssText = 'color:inherit;text-decoration:underline;font-weight:600;';
      if (/^https?:/i.test(href)) {
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
      }
      text.appendChild(link);
    }
    el.appendChild(text);

    if (cfg.dismissible) {
      var close = document.createElement('button');
      close.type = 'button';
      close.setAttribute('aria-label', 'Dismiss announcement');
      close.textContent = '×';
      close.style.cssText =
        'background:transparent;border:0;color:inherit;cursor:pointer;font-size:20px;line-height:1;padding:0 4px;';
      close.addEventListener('click', onDismiss);
      el.appendChild(close);
    }
    return el;
  }

  // The header is the sticky *top* bar; prefer the one directly followed by <main id="main-content">.
  function findHeader() {
    var headers = document.querySelectorAll(HEADER_SELECTOR);
    for (var i = 0; i < headers.length; i++) {
      var next = headers[i].nextElementSibling;
      if (next && next.id === 'main-content') return headers[i];
    }
    return headers[0] || null;
  }

  function emphasise(el) {
    if (!el.animate) return;
    try {
      el.animate([{ opacity: 0, transform: 'translateY(-8px)' }, { opacity: 1, transform: 'none' }], { duration: 300, easing: 'ease-out' });
    } catch (e) {
      /* purely cosmetic */
    }
  }

  function place() {
    pending = false;
    var animate = announce;
    announce = false;
    var existing = document.getElementById(BANNER_ID);
    var wanted = cfg && cfg.enabled && cfg.message && !isDismissed();
    if (!wanted) {
      if (existing) existing.remove();
      return;
    }
    if (existing && (existing.getAttribute('data-banner-id') !== (cfg.id || '') || existing.getAttribute('data-banner-sig') !== signature(cfg))) {
      existing.remove();
      existing = null;
    }

    var header = findHeader();
    var root = document.getElementById('root');
    if (!header && !(cfg.showOnLoginPage && root)) {
      if (existing) existing.remove();
      return;
    }
    var el = existing || build(cfg, dismiss);
    if (header) {
      if (header.nextElementSibling !== el) header.insertAdjacentElement('afterend', el);
    } else if (root.previousElementSibling !== el) {
      root.parentNode.insertBefore(el, root);
    }
    if (!existing && animate) emphasise(el);
  }

  // Coalesce bursts of DOM mutations (React renders) into one placement per frame. A hidden tab never gets
  // animation frames, so place immediately there: the banner is already in the page when the user returns.
  function schedule() {
    if (pending || !cfg) return;
    pending = true;
    if (document.hidden) window.setTimeout(place, 0);
    else window.requestAnimationFrame(place);
  }

  function apply(next, live) {
    cfg = next;
    announce = !!live;
    schedule();
  }

  function load() {
    return window
      .fetch(BASE + 'banner.json?t=' + Date.now(), { cache: 'no-store', credentials: 'omit' })
      .then(function (res) {
        return res.ok ? res.json() : Promise.reject(new Error('HTTP ' + res.status));
      })
      .then(function (next) {
        apply(next, false);
        return next;
      })
      .catch(function () {
        /* keep showing the last good config; try again on the next tick */
      });
  }

  // ---- live channel -------------------------------------------------------------------------------
  function liveUrl() {
    return (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host + LIVE_PATH;
  }

  // Exponential backoff with jitter, so a restart of the service does not have every tab reconnect at once.
  function reconnectSoon() {
    if (retryTimer) return;
    var delay = Math.min(30000, 1000 * Math.pow(2, retries)) * (0.5 + Math.random() / 2);
    retries++;
    retryTimer = window.setTimeout(function () {
      retryTimer = null;
      connect();
    }, delay);
  }

  function lost(ws) {
    if (socket !== ws) return;
    socket = null;
    liveUp = false;
    reconnectSoon();
  }

  function connect() {
    if (socket || !window.WebSocket) return;
    var ws;
    try {
      ws = new window.WebSocket(liveUrl());
    } catch (e) {
      reconnectSoon();
      return;
    }
    socket = ws;
    lastMessage = Date.now();
    ws.onmessage = function (event) {
      lastMessage = Date.now();
      retries = 0;
      liveUp = true;
      var msg;
      try {
        msg = JSON.parse(event.data);
      } catch (e) {
        return;
      }
      if (msg && msg.type === 'banner' && msg.banner) apply(msg.banner, true); // heartbeats ({"type":"hb"}) only prove life
    };
    ws.onclose = ws.onerror = function () {
      lost(ws);
    };
  }

  // A connection can die silently (laptop sleep, NAT timeout): no close event ever arrives.
  function watchdog() {
    if (socket && Date.now() - lastMessage > SILENCE_MS) {
      var ws = socket;
      lost(ws);
      try {
        ws.close();
      } catch (e) {
        /* already gone */
      }
    }
  }

  // ---- polling (safety net) -------------------------------------------------------------------------
  // Re-read the interval every cycle so an admin can change it. While the live socket is up polling only
  // guards against a missed push; without it, polling is the whole mechanism.
  function poll() {
    var seconds = liveUp ? SLOW_POLL_SECONDS : Math.max(15, Number((cfg && cfg.refreshSeconds) || 60));
    window.setTimeout(function () {
      load().then(poll);
    }, seconds * 1000);
  }

  function start() {
    load().then(poll);
    connect();
    window.setInterval(watchdog, 10000);
    new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true });
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) {
        load();
        connect();
      }
    });
    window.addEventListener('online', function () {
      retries = 0;
      connect();
    });
  }

  // The admin page loads this file (with data-preview) only to reuse the exact rendering for its
  // live preview; it must not start polling or touch the page.
  if (thisScript && thisScript.hasAttribute('data-preview')) {
    window.__coderBannerPreview = {
      build: function (c) {
        return build(c, function () {});
      },
    };
    return;
  }

  if (document.body) start();
  else document.addEventListener('DOMContentLoaded', start);
})();
