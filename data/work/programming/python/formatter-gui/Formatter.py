#!/usr/bin/env python3
"""
Fmt — a minimalist GTK4 file formatter.

Supported types → external tools:
  xml          → xmllint          (1-space indent via XMLLINT_INDENT)
  json         → prettier         (fallback: python -m json.tool, 1-space)
  yaml         → yq               (mikefarah yq, -I 1)
  toml         → yq               (-p toml -o toml -I 1)
  java         → google-java-format.jar  (java -jar …)
  c / c++      → clang-format     (IndentWidth: 1, BreakBeforeBraces: Attach)
  shell        → shfmt            (-i 1, braces stay on the same line)
  python       → ruff format      (indent-width = 1)

Default indentation: 1 space.
Brace style: opening bracket on the same line as the declaration (K&R / Attach).

Runtime deps:  python3-gi, gir1.2-gtk-4.0, libadwaita (gir1.2-adw-1)
Optional env:  GJF_JAR=/path/to/google-java-format-all-deps.jar
"""

import os
import shutil
import subprocess
import sys
import tempfile

import gi

gi.require_version('Gtk', '4.0')
try:
 gi.require_version('Adw', '1')
 from gi.repository import Adw

 HAS_ADW = True
except (ValueError, ImportError):
 HAS_ADW = False

from gi.repository import Gdk, Gio, GLib, Gtk  # noqa: E402

INDENT = 1  # default spacing: 1 space

# ---------------------------------------------------------------------------
# Formatter registry
# ---------------------------------------------------------------------------

LANGS = [
 # (label, key, extensions)
 ('Auto detect', 'auto', []),
 ('XML', 'xml', ['.xml', '.xsd', '.svg', '.ui']),
 ('JSON', 'json', ['.json']),
 ('YAML', 'yaml', ['.yaml', '.yml']),
 ('TOML', 'toml', ['.toml']),
 ('Java', 'java', ['.java']),
 ('C / C++', 'c', ['.c', '.h', '.cpp', '.cc', '.cxx', '.hpp', '.hh']),
 ('Shell', 'sh', ['.sh', '.bash']),
 ('Python', 'python', ['.py']),
]

CLANG_STYLE = (
 '{BasedOnStyle: LLVM, IndentWidth: %d, TabWidth: %d, UseTab: Never, '
 'BreakBeforeBraces: Attach, AllowShortFunctionsOnASingleLine: None}' % (INDENT, INDENT)
)


def _gjf_jar():
 """Locate google-java-format jar: $GJF_JAR, next to the script, or ~/.local/share."""
 candidates = [os.environ.get('GJF_JAR', '')]
 here = os.path.dirname(os.path.abspath(__file__))
 for root in (here, os.path.expanduser('~/.local/share/fmt')):
  if os.path.isdir(root):
   candidates += [
    os.path.join(root, f)
    for f in os.listdir(root)
    if f.startswith('google-java-format') and f.endswith('.jar')
   ]
 for c in candidates:
  if c and os.path.isfile(c):
   return c
 return None


def detect_lang(path):
 if not path:
  return None
 ext = os.path.splitext(path)[1].lower()
 for _, key, exts in LANGS:
  if ext in exts:
   return key
 return None


def run(cmd, text, env=None):
 """Run cmd feeding *text* on stdin; return formatted text or raise RuntimeError."""
 e = dict(os.environ)
 if env:
  e.update(env)
 try:
  p = subprocess.run(cmd, input=text, capture_output=True, text=True, env=e, timeout=30)
 except FileNotFoundError:
  raise RuntimeError(f"'{cmd[0]}' is not installed")
 except subprocess.TimeoutExpired:
  raise RuntimeError(f"'{cmd[0]}' timed out")
 if p.returncode != 0:
  msg = (p.stderr or p.stdout or 'unknown error').strip().splitlines()
  raise RuntimeError(msg[0] if msg else f"'{cmd[0]}' failed")
 return p.stdout


def format_text(lang, text):
 """Dispatch to the right external formatter. Returns formatted text."""
 if lang == 'xml':
  return run(['xmllint', '--format', '-'], text, env={'XMLLINT_INDENT': ' ' * INDENT})

 if lang == 'json':
  if shutil.which('prettier'):
   return run(['prettier', '--parser', 'json', '--tab-width', str(INDENT)], text)
  return run([sys.executable, '-m', 'json.tool', '--indent', str(INDENT)], text)

 if lang == 'yaml':
  return run(['yq', '-P', '-I', str(INDENT), '.'], text)

 if lang == 'toml':
  return run(['yq', '-p', 'toml', '-o', 'toml', '-I', str(INDENT), '.'], text)

 if lang == 'java':
  jar = _gjf_jar()
  if not jar:
   raise RuntimeError(
    'google-java-format jar not found — set $GJF_JAR or drop the '
    'jar next to formatter.py'
   )
  return run(['java', '-jar', jar, '-'], text)

 if lang == 'c':
  return run(['clang-format', f'-style={CLANG_STYLE}'], text)

 if lang == 'sh':
  # shfmt keeps `{` on the same line as the function name by default
  return run(['shfmt', '-i', str(INDENT), '-'], text)

 if lang == 'python':
  return run(
   [
    'ruff',
    'format',
    '--stdin-filename',
    'buffer.py',
    '--config',
    f'indent-width={INDENT}',
    '-',
   ],
   text,
  )

 raise RuntimeError('Pick a file type first — auto detect needs a file extension')


# ---------------------------------------------------------------------------
# UI
# ---------------------------------------------------------------------------

CSS = b"""
window {
 background: #ffffff;
}

.card {
 background: #f1f2f4;
 border-radius: 18px;
 box-shadow: 0 8px 28px alpha(#000, 0.10), 0 1px 2px alpha(#000, 0.06);
}

.toolbar-top {
 padding: 10px 12px;
}

button.chip {
 background: #ffffff;
 border-radius: 10px;
 border: 1px solid alpha(#000, 0.08);
 padding: 4px 12px;
 font-weight: 600;
 font-size: 13px;
 box-shadow: none;
}
button.chip:hover { background: #fafafa; }

.tool-btn {
 background: transparent;
 border: none;
 border-radius: 10px;
 min-width: 34px;
 min-height: 34px;
 padding: 0;
 box-shadow: none;
 color: #3a3a3f;
}
.tool-btn:hover { background: alpha(#000, 0.06); }
.tool-btn.active { background: #ffffff; border: 1px solid alpha(#000, 0.08); }

separator.vsep {
 background: alpha(#000, 0.10);
 min-width: 1px;
 margin: 6px 4px;
}

textview, textview text {
 background: transparent;
 color: #26262b;
 font-family: "JetBrains Mono", "Cascadia Code", monospace;
 font-size: 13px;
 caret-color: #26262b;
}
textview { padding: 6px 18px 14px 18px; }

.dock {
 background: #232327;
 border-radius: 26px;
 padding: 7px 14px;
 box-shadow: 0 10px 24px alpha(#000, 0.28);
}

.dock button {
 background: transparent;
 border: none;
 box-shadow: none;
 border-radius: 999px;
 min-width: 34px;
 min-height: 34px;
 padding: 0;
 color: #cfcfd4;
}
.dock button:hover { background: alpha(#fff, 0.10); color: #ffffff; }

.dock button.accent {
 background: #3b3b41;
 color: #ffffff;
}
.dock button.accent:hover { background: #4a4a52; }

.hint {
 color: #9a9aa2;
 font-size: 12px;
}

.filename {
 color: #6b6b73;
 font-size: 12px;
 font-weight: 600;
}
"""


class FmtWindow(Gtk.ApplicationWindow):
 def __init__(self, app):
  super().__init__(application=app, title='Fmt')
  self.set_default_size(760, 560)
  self.current_path = None

  provider = Gtk.CssProvider()
  provider.load_from_data(CSS)
  Gtk.StyleContext.add_provider_for_display(
   Gdk.Display.get_default(),
   provider,
   Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION,
  )

  # -- headerbar: flat, borderless ---------------------------------
  hb = Gtk.HeaderBar()
  hb.add_css_class('flat')
  self.set_titlebar(hb)
  self.file_label = Gtk.Label(label='No file open')
  self.file_label.add_css_class('filename')
  hb.set_title_widget(self.file_label)

  # -- outer layout ------------------------------------------------
  outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
  outer.set_margin_top(6)
  outer.set_margin_bottom(22)
  outer.set_margin_start(26)
  outer.set_margin_end(26)
  self.set_child(outer)

  card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
  card.add_css_class('card')
  card.set_vexpand(True)
  outer.append(card)

  # -- top toolbar (dropdown + tool icons) -------------------------
  top = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
  top.add_css_class('toolbar-top')
  card.append(top)

  labels = Gtk.StringList.new([l for l, _, _ in LANGS])
  self.lang_dd = Gtk.DropDown(model=labels)
  self.lang_dd.add_css_class('chip')
  self.lang_dd.set_tooltip_text('File type')
  top.append(self.lang_dd)

  top.append(self._vsep())

  for icon, tip, cb in (
   ('format-indent-more-symbolic', 'Format (Ctrl+Shift+F)', self.on_format),
   ('edit-copy-symbolic', 'Copy result', self.on_copy),
   ('edit-clear-all-symbolic', 'Clear', self.on_clear),
  ):
   b = Gtk.Button.new_from_icon_name(icon)
   b.add_css_class('tool-btn')
   b.set_tooltip_text(tip)
   b.connect('clicked', cb)
   top.append(b)

  spacer = Gtk.Box()
  spacer.set_hexpand(True)
  top.append(spacer)

  self.status = Gtk.Label(label=f'{INDENT}-space indent')
  self.status.add_css_class('hint')
  top.append(self.status)

  # -- editor ------------------------------------------------------
  sw = Gtk.ScrolledWindow()
  sw.set_vexpand(True)
  card.append(sw)

  self.view = Gtk.TextView()
  self.view.set_monospace(True)
  self.view.set_wrap_mode(Gtk.WrapMode.NONE)
  self.buf = self.view.get_buffer()
  self.buf.set_text('Drop a file here, or paste your code…\n')
  sw.set_child(self.view)

  # drag & drop
  drop = Gtk.DropTarget.new(Gio.File, Gdk.DragAction.COPY)
  drop.connect('drop', self.on_drop)
  self.view.add_controller(drop)

  # -- floating bottom dock ---------------------------------------
  dock_row = Gtk.CenterBox()
  dock_row.set_margin_top(14)
  outer.append(dock_row)

  dock = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
  dock.add_css_class('dock')
  dock_row.set_center_widget(dock)

  for icon, tip, cb, accent in (
   ('list-add-symbolic', 'Open file (Ctrl+O)', self.on_open, True),
   ('format-indent-more-symbolic', 'Format', self.on_format, False),
   ('document-save-symbolic', 'Save (Ctrl+S)', self.on_save, False),
   ('edit-copy-symbolic', 'Copy', self.on_copy, False),
  ):
   b = Gtk.Button.new_from_icon_name(icon)
   if accent:
    b.add_css_class('accent')
   b.set_tooltip_text(tip)
   b.connect('clicked', cb)
   dock.append(b)

  # -- shortcuts ---------------------------------------------------
  self._shortcut('<Control>o', self.on_open)
  self._shortcut('<Control>s', self.on_save)
  self._shortcut('<Control><Shift>f', self.on_format)

 # -- helpers ---------------------------------------------------------

 def _vsep(self):
  s = Gtk.Separator(orientation=Gtk.Orientation.VERTICAL)
  s.add_css_class('vsep')
  return s

 def _shortcut(self, accel, cb):
  sc = Gtk.ShortcutController()
  sc.set_scope(Gtk.ShortcutScope.GLOBAL)
  sc.add_shortcut(
   Gtk.Shortcut.new(
    Gtk.ShortcutTrigger.parse_string(accel),
    Gtk.CallbackAction.new(lambda *_: (cb(None), True)[1]),
   )
  )
  self.add_controller(sc)

 def _text(self):
  return self.buf.get_text(self.buf.get_start_iter(), self.buf.get_end_iter(), True)

 def _flash(self, msg, error=False):
  self.status.set_text(msg)
  GLib.timeout_add_seconds(
   4, lambda: (self.status.set_text(f'{INDENT}-space indent'), False)[1]
  )
  if error:
   print(msg, file=sys.stderr)

 def _selected_lang(self):
  key = LANGS[self.lang_dd.get_selected()][1]
  if key == 'auto':
   key = detect_lang(self.current_path)
  return key

 def _select_lang(self, key):
  for i, (_, k, _) in enumerate(LANGS):
   if k == key:
    self.lang_dd.set_selected(i)
    return

 # -- actions ---------------------------------------------------------

 def on_open(self, *_):
  dlg = Gtk.FileDialog()
  dlg.open(self, None, self._opened)

 def _opened(self, dlg, result):
  try:
   f = dlg.open_finish(result)
  except GLib.Error:
   return
  self._load(f.get_path())

 def on_drop(self, _t, f, _x, _y):
  self._load(f.get_path())
  return True

 def _load(self, path):
  try:
   with open(path, 'r', encoding='utf-8') as fh:
    self.buf.set_text(fh.read())
  except OSError as e:
   self._flash(str(e), error=True)
   return
  self.current_path = path
  self.file_label.set_text(os.path.basename(path))
  lang = detect_lang(path)
  if lang:
   self._select_lang(lang)
   self._flash(f'Opened as {lang}')
  else:
   self._flash('Unknown file type — pick one from the dropdown')

 def on_format(self, *_):
  lang = self._selected_lang()
  text = self._text()
  if not text.strip():
   self._flash('Nothing to format')
   return
  try:
   self.buf.set_text(format_text(lang, text))
   self._flash('Formatted ✓')
  except RuntimeError as e:
   self._flash(str(e), error=True)

 def on_copy(self, *_):
  self.get_clipboard().set(self._text())
  self._flash('Copied')

 def on_clear(self, *_):
  self.buf.set_text('')
  self.current_path = None
  self.file_label.set_text('No file open')

 def on_save(self, *_):
  if self.current_path:
   self._write(self.current_path)
   return
  dlg = Gtk.FileDialog()
  dlg.save(self, None, self._saved)

 def _saved(self, dlg, result):
  try:
   f = dlg.save_finish(result)
  except GLib.Error:
   return
  self.current_path = f.get_path()
  self.file_label.set_text(os.path.basename(self.current_path))
  self._write(self.current_path)

 def _write(self, path):
  try:
   with open(path, 'w', encoding='utf-8') as fh:
    fh.write(self._text())
   self._flash(f'Saved → {os.path.basename(path)}')
  except OSError as e:
   self._flash(str(e), error=True)


class FmtApp(Adw.Application if HAS_ADW else Gtk.Application):
 def __init__(self):
  super().__init__(application_id='dev.fmt.Formatter')

 def do_activate(self):
  win = self.props.active_window or FmtWindow(self)
  win.present()


if __name__ == '__main__':
 sys.exit(FmtApp().run(sys.argv))
