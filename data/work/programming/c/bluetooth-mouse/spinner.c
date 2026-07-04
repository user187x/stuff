/*
 * spinner.c  -  Animated braille throbber (persistent-thread model).
 * See spinner.h.
 */
#define _GNU_SOURCE
#include "spinner.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>

/* The classic "dots" frames used by pip and many CLIs. */
static const char *FRAMES[] = {
    "\u280b", "\u2819", "\u2839", "\u2838", "\u283c",
    "\u2834", "\u2826", "\u2827", "\u2807", "\u280f"
};
#define NFRAMES 10
#define INTERVAL_US (80 * 1000)   /* 80 ms per frame */

static pthread_t       g_thread;
static pthread_mutex_t g_lock   = PTHREAD_MUTEX_INITIALIZER;
static volatile int    g_alive  = 0;   /* thread should keep running */
static volatile int    g_active = 0;   /* currently drawing          */
static int             g_started = 0;
static int             g_tty    = 0;
static char            g_msg[256];

static void *spin_loop(void *arg)
{
    (void)arg;
    int i = 0;
    while (g_alive) {
        if (g_active && g_tty) {
            pthread_mutex_lock(&g_lock);
            /* \r to col 0, \033[K clears line, cyan frame + message. */
            printf("\r\033[K\033[36m%s\033[0m %s",
                   FRAMES[i % NFRAMES], g_msg);
            fflush(stdout);
            pthread_mutex_unlock(&g_lock);
            i++;
        }
        usleep(INTERVAL_US);
    }
    return NULL;
}

void spinner_init(void)
{
    if (g_started) return;
    g_tty  = isatty(STDOUT_FILENO);
    g_alive = 1;
    if (pthread_create(&g_thread, NULL, spin_loop, NULL) == 0)
        g_started = 1;
    else
        g_alive = 0;
}

void spinner_show(const char *msg)
{
    pthread_mutex_lock(&g_lock);
    snprintf(g_msg, sizeof(g_msg), "%s", msg ? msg : "");
    g_active = 1;
    pthread_mutex_unlock(&g_lock);

    if (!g_tty) {   /* no animation off-terminal: one static line */
        printf("%s\n", msg ? msg : "");
        fflush(stdout);
    }
}

void spinner_set_message(const char *msg)
{
    pthread_mutex_lock(&g_lock);
    snprintf(g_msg, sizeof(g_msg), "%s", msg ? msg : "");
    pthread_mutex_unlock(&g_lock);
}

void spinner_hide(void)
{
    pthread_mutex_lock(&g_lock);
    int was = g_active;
    g_active = 0;
    pthread_mutex_unlock(&g_lock);

    if (was && g_tty) {
        printf("\r\033[K");   /* erase the spinner line */
        fflush(stdout);
    }
}

void spinner_shutdown(void)
{
    if (g_started) {
        g_alive = 0;
        pthread_join(g_thread, NULL);
        g_started = 0;
    }
}
