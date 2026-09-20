/* Coder banner admin page. All text from the server is inserted with textContent. */
(function () {
  'use strict';

  var API = '/__banner/api/';
  var PRESETS = {
    maintenance: {
      level: 'warning', title: 'Scheduled maintenance:', dismissible: true,
      message: 'Coder will be unavailable on [date] from [start] to [end] UTC. Please save your work and stop your workspaces beforehand.',
    },
    emergency: {
      level: 'critical', title: 'Emergency security patch:', dismissible: false,
      message: 'Coder will restart at [time] UTC to apply a critical security fix. Save your work and push any code you need to keep now.',
    },
    clear: {
      level: 'success', title: 'All clear:', dismissible: true,
      message: 'Maintenance is complete and Coder is back to normal. Thanks for your patience.',
    },
  };
  var TEXT_FIELDS = ['message', 'title', 'linkText', 'linkUrl'];
  var BOOL_FIELDS = ['dismissible', 'showOnLoginPage'];

  var $ = function (id) { return document.getElementById(id); };
  var server = null; // last state from the service
  var busy = false;
  var toastTimer = null;

  function api(path, body) {
    var options = { credentials: 'same-origin', headers: {} };
    if (body !== undefined) {
      options.method = 'POST';
      options.headers = { 'Content-Type': 'application/json', 'X-Requested-With': 'coder-banner-admin' };
      options.body = JSON.stringify(body);
    }
    return fetch(API + path, options).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (res.status === 401) {
          window.location.href = '/login?redirect=' + encodeURIComponent('/__banner/admin');
          return new Promise(function () {});
        }
        if (!res.ok) throw new Error(data.error || 'Request failed (HTTP ' + res.status + ')');
        return data;
      });
    });
  }

  // ---- form <-> banner
  function readForm() {
    var out = { enabled: server ? server.banner.enabled : true };
    TEXT_FIELDS.forEach(function (k) { out[k] = $(k).value.trim(); });
    BOOL_FIELDS.forEach(function (k) { out[k] = $(k).checked; });
    var level = document.querySelector('input[name=level]:checked');
    out.level = level ? level.value : 'info';
    out.refreshSeconds = parseInt($('refreshSeconds').value, 10) || 60;
    return out;
  }

  function writeForm(b) {
    TEXT_FIELDS.forEach(function (k) { $(k).value = b[k] || ''; });
    BOOL_FIELDS.forEach(function (k) { $(k).checked = !!b[k]; });
    var radio = document.querySelector('input[name=level][value=' + (b.level || 'info') + ']');
    if (radio) radio.checked = true;
    $('refreshSeconds').value = b.refreshSeconds || 60;
  }

  function sameAsServer(form) {
    var s = server.banner;
    return TEXT_FIELDS.concat(BOOL_FIELDS, ['level', 'refreshSeconds']).every(function (k) { return form[k] === s[k]; });
  }

  // ---- rendering
  function renderPreview() {
    var form = readForm();
    var box = $('preview');
    box.textContent = '';
    if (!form.message) {
      var empty = document.createElement('div');
      empty.className = 'preview-empty';
      empty.textContent = 'Type a message to see a preview.';
      box.appendChild(empty);
    } else {
      form.enabled = true;
      box.appendChild(window.__coderBannerPreview.build(form));
    }
  }

  function renderStatus() {
    var pill = $('status');
    var on = server.banner.enabled && !!server.banner.message;
    pill.textContent = on ? '● Live for all developers' : '○ Hidden';
    pill.className = 'pill ' + (on ? 'live' : 'off');
    $('toggle').textContent = on ? 'Hide banner' : 'Show banner';
    $('reappear').disabled = !server.banner.message;
  }

  function renderState() {
    var form = readForm();
    var dirty = !sameAsServer(form);
    $('count').textContent = $('message').value.length + ' / 400';
    $('publish').disabled = busy || !dirty || !form.message;
    $('revert').disabled = busy || !dirty;
    $('toggle').disabled = busy;
    $('reappear').disabled = busy || !server.banner.message;
    $('reset').hidden = !server.overrideActive;
    var note = '';
    if (dirty) note = 'You have unpublished changes.';
    else if (/\[[^\]]+\]/.test(form.message)) note = 'Replace the [bracketed] parts of the message before publishing.';
    $('dirty').textContent = note;
    renderPreview();
  }

  function renderMeta() {
    var meta = $('meta');
    if (server.overrideActive && server.updatedBy) {
      meta.textContent = 'Last published by ' + server.updatedBy + ' on ' + new Date(server.updatedAt).toLocaleString() + '.';
    } else if (server.overrideActive) {
      meta.textContent = 'Published from this page.';
    } else {
      meta.textContent = 'Nothing has been published from this page yet, so the chart defaults are showing.';
    }
    var secs = server.banner.refreshSeconds;
    $('reappear-note').textContent = 'Open Coder tabs pick this up within about ' + secs + ' seconds; anyone who loads Coder afterwards sees it immediately.';
  }

  function apply(data, resetForm) {
    server = data;
    $('who').textContent = 'Signed in as ' + data.user.username;
    if (resetForm) writeForm(data.banner);
    renderStatus();
    renderMeta();
    renderState();
  }

  function toast(text, isError) {
    var el = $('toast');
    el.textContent = text;
    el.className = 'toast' + (isError ? ' error' : '');
    el.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { el.hidden = true; }, isError ? 8000 : 4000);
  }

  function showProblem(text) {
    var el = $('problem');
    el.textContent = text || '';
    el.hidden = !text;
  }

  // ---- actions
  function run(promise, success) {
    busy = true;
    showProblem('');
    renderState();
    return promise.then(function (data) {
      busy = false;
      apply(data, true);
      if (success) toast(success);
    }).catch(function (e) {
      busy = false;
      renderState();
      showProblem(e.message);
      toast(e.message, true);
    });
  }

  function publish() {
    var form = readForm();
    if (/\[[^\]]+\]/.test(form.message) && !window.confirm('The message still contains [bracketed] placeholders. Publish it anyway?')) return;
    form.enabled = true;
    run(api('banner', form), 'Published. Developers will see the new message.');
  }

  function toggle() {
    var current = Object.assign({}, server.banner);
    current.enabled = !server.banner.enabled;
    if (current.enabled && !current.message) { showProblem('Write and publish a message first.'); return; }
    run(api('banner', current), current.enabled ? 'Banner is showing again.' : 'Banner hidden.');
  }

  function reappear() {
    var ok = window.confirm('Show the banner again to EVERYONE, including people who dismissed it?\n\nOpen Coder tabs will show it within about ' + server.banner.refreshSeconds + ' seconds.');
    if (!ok) return;
    run(api('reappear', {}), 'Done. The banner will reappear for everyone, including people who dismissed it.');
  }

  function reset() {
    if (!window.confirm('Discard what was published from this page and go back to the chart defaults?')) return;
    run(api('reset', {}), 'Back to the chart defaults.');
  }

  function preset(name) {
    var p = PRESETS[name];
    var f = readForm();
    f.level = p.level; f.title = p.title; f.message = p.message; f.dismissible = p.dismissible;
    writeForm(f);
    renderState();
    $('message').focus();
  }

  function init() {
    ['input', 'change'].forEach(function (evt) {
      document.querySelector('main').addEventListener(evt, function (e) {
        if (e.target.closest('#preview') || !server) return;
        renderState();
      });
    });
    $('publish').addEventListener('click', publish);
    $('revert').addEventListener('click', function () { writeForm(server.banner); showProblem(''); renderState(); });
    $('toggle').addEventListener('click', toggle);
    $('reappear').addEventListener('click', reappear);
    $('reset').addEventListener('click', reset);
    Array.prototype.forEach.call(document.querySelectorAll('[data-preset]'), function (b) {
      b.addEventListener('click', function () { preset(b.getAttribute('data-preset')); });
    });
    window.addEventListener('beforeunload', function (e) {
      if (server && !sameAsServer(readForm())) { e.preventDefault(); e.returnValue = ''; }
    });
    api('state').then(function (data) { apply(data, true); }).catch(function (e) {
      $('status').textContent = 'Error';
      showProblem(e.message);
    });
  }

  init();
})();
