# Dev Environment Cheatsheet

> Tmux prefix is '$PREFIX_KEY' unless rebound.

## Tmux — Sessions

| Keys              | Action                          |
|-------------------|---------------------------------|
| `prefix d`        | Detach from session             |
| `prefix s`        | List sessions (interactive)     |
| `prefix $`        | Rename current session          |
| `tmux ls`         | List sessions (shell)           |
| `tmux a -t name`  | Attach to named session         |

## Tmux — Windows

| Keys           | Action                  |
|----------------|-------------------------|
| `prefix c`     | Create new window       |
| `prefix ,`     | Rename window           |
| `prefix n / p` | Next / previous window  |
| `prefix 0-9`   | Jump to window by index |
| `prefix &`     | Kill window             |

## Tmux — Panes

| Keys             | Action                       |
|------------------|------------------------------|
| `prefix |`       | Split horizontal (custom)    |
| `prefix -`       | Split vertical (custom)      |
| `prefix arrows`  | Move between panes           |
| `prefix z`       | Toggle zoom on current pane  |
| `prefix {` / `}` | Swap pane with prev / next   |
| `prefix x`       | Kill pane                    |
| `prefix S`       | Toggle synchronize-panes     |

## Tmux — Copy Mode

| Keys             | Action                |
|------------------|-----------------------|
| `prefix [`       | Enter copy mode       |
| `/` or `?`       | Search forward / back |
| `space`          | Start selection       |
| `enter`          | Yank selection        |
| `prefix ]`       | Paste                 |

## Tmux — Notes & Gotchas

- **Sync-panes** (`prefix S`) broadcasts keystrokes to every pane in the window —
  great for multi-host ops, dangerous near password prompts. Toggle it off
  before typing anything sensitive.
- **Zoom** (`prefix z`) is your friend for tailing logs; the indicator in the
  status bar reminds you you're zoomed.
- **Reload config**: `prefix r` if bound, otherwise `tmux source ~/.tmux.conf`.
- **Nested sessions** (tmux inside tmux over SSH): press `prefix` twice to send
  the key to the inner session.

---

## Vim — Window Focus (NerdTree ⇄ Main View)

NerdTree opens as a vertical split on the **left** by default. Bouncing focus
between the tree and your code is just vim's standard window-movement commands
— no NerdTree-specific keys needed.

| Keys         | Action                                            |
|--------------|---------------------------------------------------|
| `Ctrl+n`     | Toggle NerdTree open/closed (custom binding)      |
| `Ctrl+w h`   | Move focus **left** → into NerdTree               |
| `Ctrl+w l`   | Move focus **right** → back to the main view      |
| `Ctrl+w w`   | Cycle through all windows in order                |
| `Ctrl+w p`   | Jump to the **previous** window (toggle-style)    |
| `Ctrl+w =`   | Equalize all window sizes                         |
| `Ctrl+w o`   | Close every window except the current one         |

### The fast pattern

`Ctrl+w` followed by `h`/`l` is the muscle-memory move — same hand position as
hjkl navigation, just with `Ctrl+w` as the prefix. If you live in NerdTree,
`Ctrl+w p` is even faster: it always toggles to the last window you were in,
so once you've moved from tree → file once, `Ctrl+w p` bounces you back and
forth indefinitely.

### Inside NerdTree (when focus IS on the tree)

| Keys     | Action                                     |
|----------|--------------------------------------------|
| `o`      | Open file / toggle directory expansion     |
| `t`      | Open file in a **new tab**                 |
| `s`      | Open file in a **vertical split**          |
| `i`      | Open file in a **horizontal split**        |
| `p`      | Jump to parent directory                   |
| `R`      | Refresh the tree                           |
| `m`      | Open the NerdTree menu (add/move/delete)   |
| `?`      | NerdTree's own built-in help               |

### Optional one-key focus jump

If `Ctrl+w h` still feels like too many keys, drop this into your vimrc to
get a single-leader jump that *also* opens NerdTree if it's closed:

```vim
nnoremap <leader>e :NERDTreeFocus<CR>
```

Now `Space+e` puts you in the tree from anywhere, and `Ctrl+w p` (or
`Ctrl+w l`) takes you back to the code.

## My Custom Bindings

_Edit this section as you grow your config._

- `prefix H` — this cheatsheet
- `prefix |` / `prefix -` — splits (intuitive direction)
- `prefix r` — reload config
- `Ctrl+n` (vim) — toggle NerdTree
- `Space+x` (vim) — chmod +x current shell/python file
