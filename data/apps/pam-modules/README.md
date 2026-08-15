# pam_certmanager — relock cert-manager entries on logout

A PAM **session** module that guarantees a user's unlocked `cert-manager` entries
(the password + PEM copies in the runtime dir) never outlive the user's login.

| Event | What happens |
|---|---|
| `close_session` (last session of that user) | every unlocked entry is relocked: auto-relock timer killed, `.pw/.pem/.meta` shredded, `last_locked=… (pam close)` recorded, gpg-agent stopped so `pass` re-prompts |
| `open_session` (default on) | any stale unlocked state from a previous login is wiped, so an entry is never "open" for a user who is not logged in |

The module (`pam_certmanager.so`, root) only does one thing: it forks, drops privileges
to the logging-in/out user (initgroups/setgid/setuid, environment scrubbed) and
executes `/usr/local/libexec/cert-manager-pam-hook <open|close> <user>`. The hook is
self-contained bash that mirrors cert-manager's file layout, so it works even if the
user's cert-manager install is missing or broken. The module **never fails a login**
(always `PAM_SUCCESS`; `PAM_IGNORE` on auth/account stacks) and refuses to run a hook
that is not root-owned / is group- or world-writable.

## Files
```
pam_certmanager.c        the PAM module
cert-manager-pam-hook    helper run as the user
cert-manager.pam-config  pam-auth-update profile (session, optional, priority -10 → after pam_systemd)
Makefile                 build / install / uninstall
install.sh               one-shot: deps → build → install → pam-auth-update --enable
```

## Install (Ubuntu/Debian)
```bash
sudo ./install.sh            # or: make && sudo make install && sudo pam-auth-update --package --enable cert-manager
sudo ./install.sh remove     # uninstall
```
Manual alternative — add **after** `pam_systemd.so` in `/etc/pam.d/common-session`:
```
session optional pam_certmanager.so
```

## Module options
```
session optional pam_certmanager.so [hook=/path] [lock_on_open=yes|no] [always] [min_uid=1000] [debug]
```
* `always` – lock on every session close, even if the user still has other sessions open (default: only on the last one, detected via `loginctl`/`who`).
* `lock_on_open=no` – don't wipe on login (not recommended).
* `min_uid` – skip system accounts (default 1000).
* `debug` – verbose `auth.debug` syslog.

Logs go to syslog with tag `cert-manager-pam` (`journalctl -t cert-manager-pam`).

## Notes / caveats
* systemd-logind removes `/run/user/<uid>` on the user's final logout anyway, which already destroys the tmpfs secrets; the module adds the pieces logind doesn't do: kills the detached timer, records the lock in the entry's info file, handles the `/tmp` fallback dir, wipes stale state on login, and stops gpg-agent.
* "Last session" detection compares logind's session list; with two concurrent logins, closing one leaves entries unlocked until the second ends (use `always` to change that).
* No pam_exec/compile-free variant is needed, but if you prefer it: `session optional pam_exec.so quiet /usr/local/libexec/cert-manager-pam-hook close` — note pam_exec runs as root, so the hook's root guard will refuse; the compiled module is the supported path.

## Test without logging out
```bash
sudo -u alice XDG_RUNTIME_DIR=/run/user/$(id -u alice) CM_PAM_ALWAYS=1 CM_PAM_DEBUG=1 \
     /usr/local/libexec/cert-manager-pam-hook close alice
```
