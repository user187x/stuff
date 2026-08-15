/*
 * pam_certmanager.c — PAM session module for cert-manager
 *
 * Guarantees that a user's unlocked cert-manager entries (the password +
 * PEM copies kept in the runtime dir) never outlive the user's login:
 *
 *   - close_session : when the user's LAST session ends, every unlocked
 *                     entry is relocked (runtime secrets shredded, timer
 *                     killed, gpg-agent cache dropped).
 *   - open_session  : (default on) stale unlocked state left over from a
 *                     previous login is wiped, so an entry can never be
 *                     "open" for a user who is not logged in.
 *
 * All real work is delegated to a helper that runs AS THE USER with
 * privileges dropped:  /usr/local/libexec/cert-manager-pam-hook
 *
 * The module never fails a login: it always returns PAM_SUCCESS (or
 * PAM_IGNORE for the auth/account stacks it does not implement).
 *
 * Options (in /etc/pam.d/…):
 *   hook=/path/to/hook   helper to execute        (default: see DEFAULT_HOOK)
 *   lock_on_open=yes|no  also lock on session open (default: yes)
 *   always               lock on every close, even if other sessions remain
 *   min_uid=N            ignore users below this uid (default: 1000)
 *   debug                verbose syslog
 *
 * Example (/etc/pam.d/common-session, AFTER pam_systemd.so):
 *   session optional pam_certmanager.so
 */

#define PAM_SM_SESSION
#define PAM_SM_AUTH
#define PAM_SM_ACCOUNT

#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <limits.h>
#include <pwd.h>
#include <security/pam_ext.h>
#include <security/pam_modules.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <syslog.h>
#include <unistd.h>

#ifndef DEFAULT_HOOK
#define DEFAULT_HOOK "/usr/local/libexec/cert-manager-pam-hook"
#endif

struct opts {
 const char* hook;
 int debug;
 int lock_on_open;
 int always;
 uid_t min_uid;
};

static void parse_opts(pam_handle_t* pamh, struct opts* o, int argc,
                       const char** argv) {
 o->hook = DEFAULT_HOOK;
 o->debug = 0;
 o->lock_on_open = 1;
 o->always = 0;
 o->min_uid = 1000;

 for (int i = 0; i < argc; i++) {
  if (strncmp(argv[i], "hook=", 5) == 0)
   o->hook = argv[i] + 5;
  else if (strcmp(argv[i], "debug") == 0)
   o->debug = 1;
  else if (strcmp(argv[i], "always") == 0)
   o->always = 1;
  else if (strncmp(argv[i], "lock_on_open=", 13) == 0)
   o->lock_on_open =
       (strcmp(argv[i] + 13, "no") != 0 && strcmp(argv[i] + 13, "0") != 0);
  else if (strncmp(argv[i], "min_uid=", 8) == 0)
   o->min_uid = (uid_t)strtoul(argv[i] + 8, NULL, 10);
  else
   pam_syslog(pamh, LOG_WARNING, "unknown option: %s", argv[i]);
 }
}

/* Redirect fd to /dev/null (rw). */
static void to_devnull(int fd) {
 int nul = open("/dev/null", O_RDWR);
 if (nul >= 0) {
  dup2(nul, fd);
  if (nul != fd) close(nul);
 }
}

static int run_hook(pam_handle_t* pamh, const struct opts* o,
                    const char* event) {
 const char* user = NULL;
 const char* service = NULL;
 struct passwd pwbuf, *pw = NULL;
 char buf[16384];
 struct stat st;

 if (pam_get_user(pamh, &user, NULL) != PAM_SUCCESS || !user || !*user) {
  pam_syslog(pamh, LOG_WARNING, "could not determine user; skipping");
  return PAM_SUCCESS;
 }
 if (pam_get_item(pamh, PAM_SERVICE, (const void**)&service) != PAM_SUCCESS ||
     !service)
  service = "unknown";

 if (getpwnam_r(user, &pwbuf, buf, sizeof buf, &pw) != 0 || !pw) {
  pam_syslog(pamh, LOG_WARNING, "unknown user '%s'; skipping", user);
  return PAM_SUCCESS;
 }
 if (pw->pw_uid < o->min_uid) {
  if (o->debug)
   pam_syslog(pamh, LOG_DEBUG, "uid %u < min_uid %u; skipping", pw->pw_uid,
              o->min_uid);
  return PAM_SUCCESS;
 }

 /* Hook must exist, be a regular file, root-owned, and not group/world
  * writable. */
 if (stat(o->hook, &st) != 0 || !S_ISREG(st.st_mode)) {
  pam_syslog(pamh, LOG_ERR, "hook '%s' not found; skipping", o->hook);
  return PAM_SUCCESS;
 }
 if (st.st_uid != 0 || (st.st_mode & (S_IWGRP | S_IWOTH))) {
  pam_syslog(
      pamh, LOG_ERR,
      "hook '%s' must be root-owned and not group/world writable; refusing",
      o->hook);
  return PAM_SUCCESS;
 }

 pid_t pid = fork();
 if (pid < 0) {
  pam_syslog(pamh, LOG_ERR, "fork failed: %s", strerror(errno));
  return PAM_SUCCESS;
 }

 if (pid == 0) {
  /* ---- child: drop privileges and exec the hook as the user ---- */
  char uidbuf[32], rtdir[PATH_MAX];

  /* close inherited descriptors */
  long maxfd = sysconf(_SC_OPEN_MAX);
  if (maxfd < 0 || maxfd > 4096) maxfd = 4096;
  for (int fd = 3; fd < maxfd; fd++) close(fd);
  to_devnull(0);
  to_devnull(1);
  to_devnull(2);

  if (getuid() == 0 || geteuid() == 0) {
   if (initgroups(pw->pw_name, pw->pw_gid) != 0 || setgid(pw->pw_gid) != 0 ||
       setuid(pw->pw_uid) != 0) {
    _exit(126);
   }
   /* verify the drop really happened */
   if (setuid(0) == 0 && pw->pw_uid != 0) _exit(126);
  }

  snprintf(uidbuf, sizeof uidbuf, "%u", (unsigned)pw->pw_uid);
  snprintf(rtdir, sizeof rtdir, "/run/user/%u", (unsigned)pw->pw_uid);

  clearenv();
  setenv("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
         1);
  setenv("HOME", pw->pw_dir, 1);
  setenv("USER", pw->pw_name, 1);
  setenv("LOGNAME", pw->pw_name, 1);
  setenv("SHELL", pw->pw_shell && *pw->pw_shell ? pw->pw_shell : "/bin/sh", 1);
  setenv("UID", uidbuf, 1);
  setenv("XDG_RUNTIME_DIR", rtdir, 1);
  setenv("CM_PAM_EVENT", event, 1);
  setenv("CM_PAM_SERVICE", service, 1);
  setenv("CM_PAM_ALWAYS", o->always ? "1" : "0", 1);
  setenv("CM_PAM_DEBUG", o->debug ? "1" : "0", 1);
  if (chdir(pw->pw_dir) != 0 && chdir("/") != 0) { /* ignore */
  }

  execl(o->hook, o->hook, event, pw->pw_name, (char*)NULL);
  _exit(127);
 }

 /* ---- parent ---- */
 int status = 0;
 while (waitpid(pid, &status, 0) < 0) {
  if (errno != EINTR) {
   pam_syslog(pamh, LOG_ERR, "waitpid failed: %s", strerror(errno));
   return PAM_SUCCESS;
  }
 }
 if (WIFEXITED(status)) {
  int rc = WEXITSTATUS(status);
  if (rc == 0) {
   if (o->debug)
    pam_syslog(pamh, LOG_INFO, "hook '%s' ok for user %s (service %s)", event,
               user, service);
  } else if (rc == 126) {
   pam_syslog(pamh, LOG_ERR, "could not drop privileges to %s; hook not run",
              user);
  } else if (rc == 127) {
   pam_syslog(pamh, LOG_ERR, "failed to exec hook %s", o->hook);
  } else {
   pam_syslog(pamh, LOG_WARNING, "hook '%s' for %s exited %d", event, user, rc);
  }
 } else if (WIFSIGNALED(status)) {
  pam_syslog(pamh, LOG_WARNING, "hook '%s' for %s killed by signal %d", event,
             user, WTERMSIG(status));
 }
 return PAM_SUCCESS;
}

/* ------------------------------------------------------------------ session */
PAM_EXTERN int pam_sm_open_session(pam_handle_t* pamh, int flags, int argc,
                                   const char** argv) {
 (void)flags;
 struct opts o;
 parse_opts(pamh, &o, argc, argv);
 if (!o.lock_on_open) return PAM_SUCCESS;
 return run_hook(pamh, &o, "open");
}

PAM_EXTERN int pam_sm_close_session(pam_handle_t* pamh, int flags, int argc,
                                    const char** argv) {
 (void)flags;
 struct opts o;
 parse_opts(pamh, &o, argc, argv);
 return run_hook(pamh, &o, "close");
}

/* ---------------------------------------------- unused stacks: be transparent
 */
PAM_EXTERN int pam_sm_authenticate(pam_handle_t* pamh, int flags, int argc,
                                   const char** argv) {
 (void)pamh;
 (void)flags;
 (void)argc;
 (void)argv;
 return PAM_IGNORE;
}

PAM_EXTERN int pam_sm_setcred(pam_handle_t* pamh, int flags, int argc,
                              const char** argv) {
 (void)pamh;
 (void)flags;
 (void)argc;
 (void)argv;
 return PAM_IGNORE;
}

PAM_EXTERN int pam_sm_acct_mgmt(pam_handle_t* pamh, int flags, int argc,
                                const char** argv) {
 (void)pamh;
 (void)flags;
 (void)argc;
 (void)argv;
 return PAM_IGNORE;
}
