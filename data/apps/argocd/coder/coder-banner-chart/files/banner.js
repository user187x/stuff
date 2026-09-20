/*
 * Coder announcement banner loader.
 *
 * Injected into Coder's HTML by a Traefik rewrite-body middleware. The dashboard is a client-rendered
 * SPA, so the header does not exist in the server HTML: this script polls /__banner/banner.json and
 * places the banner as the sibling right after the sticky header once React has rendered it,
 * re-placing it whenever React re-renders the layout.
 *
 * Served same-origin because Coder's CSP is `script-src 'self'`. All text is set with textContent.
 */
(function () {
  'use strict';

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

  var cfg = null;
  var pending = false;

  function safeUrl(url) {
    return typeof url === 'string' && /^(https?:\/\/|\/(?!\/))/i.test(url) ? url : '';
  }

  function isDismissed() {
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

  function build() {
    var level = LEVELS[cfg.level] || LEVELS.info;
    var el = document.createElement('div');
    el.id = BANNER_ID;
    el.setAttribute('role', 'status');
    el.setAttribute('data-banner-id', cfg.id || '');
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
      close.addEventListener('click', dismiss);
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

  function place() {
    pending = false;
    var existing = document.getElementById(BANNER_ID);
    var wanted = cfg && cfg.enabled && cfg.message && !isDismissed();
    if (!wanted) {
      if (existing) existing.remove();
      return;
    }
    if (existing && existing.getAttribute('data-banner-id') !== (cfg.id || '')) {
      existing.remove();
      existing = null;
    }

    var header = findHeader();
    var root = document.getElementById('root');
    if (!header && !(cfg.showOnLoginPage && root)) {
      if (existing) existing.remove();
      return;
    }
    var el = existing || build();
    if (header) {
      if (header.nextElementSibling !== el) header.insertAdjacentElement('afterend', el);
    } else if (root.previousElementSibling !== el) {
      root.parentNode.insertBefore(el, root);
    }
  }

  // Coalesce bursts of DOM mutations (React renders) into one placement per frame.
  function schedule() {
    if (pending || !cfg) return;
    pending = true;
    window.requestAnimationFrame(place);
  }

  function load() {
    return window
      .fetch(BASE + 'banner.json?t=' + Date.now(), { cache: 'no-store', credentials: 'omit' })
      .then(function (res) {
        return res.ok ? res.json() : Promise.reject(new Error('HTTP ' + res.status));
      })
      .then(function (next) {
        cfg = next;
        schedule();
        return next;
      })
      .catch(function () {
        /* keep showing the last good config; try again on the next tick */
      });
  }

  function start() {
    load().then(function () {
      var seconds = Math.max(15, Number((cfg && cfg.refreshSeconds) || 60));
      window.setInterval(load, seconds * 1000);
    });
    new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true });
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) load();
    });
  }

  if (document.body) start();
  else document.addEventListener('DOMContentLoaded', start);
})();
