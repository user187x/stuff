# pam_certmanager — build & install
CC      ?= gcc
CFLAGS  ?= -O2 -Wall -Wextra -fPIC -D_GNU_SOURCE
LDFLAGS ?= -shared
LIBS     = -lpam

# where this distro keeps PAM modules (Debian/Ubuntu multiarch aware)
PAM_DIR ?= $(shell dirname $$(ls /lib/*/security/pam_unix.so /usr/lib/*/security/pam_unix.so /lib/security/pam_unix.so 2>/dev/null | head -1))
HOOK_DIR ?= /usr/local/libexec
PAMCFG_DIR ?= /usr/share/pam-configs

all: pam_certmanager.so

pam_certmanager.so: pam_certmanager.c
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS)

install: pam_certmanager.so
	install -d -m 755 $(DESTDIR)$(PAM_DIR) $(DESTDIR)$(HOOK_DIR) $(DESTDIR)$(PAMCFG_DIR)
	install -m 644 -o root -g root pam_certmanager.so $(DESTDIR)$(PAM_DIR)/pam_certmanager.so
	install -m 755 -o root -g root cert-manager-pam-hook $(DESTDIR)$(HOOK_DIR)/cert-manager-pam-hook
	install -m 644 -o root -g root cert-manager.pam-config $(DESTDIR)$(PAMCFG_DIR)/cert-manager

uninstall:
	rm -f $(DESTDIR)$(PAM_DIR)/pam_certmanager.so $(DESTDIR)$(HOOK_DIR)/cert-manager-pam-hook $(DESTDIR)$(PAMCFG_DIR)/cert-manager

clean:
	rm -f pam_certmanager.so

.PHONY: all install uninstall clean
