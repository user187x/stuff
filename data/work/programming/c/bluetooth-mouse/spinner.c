/*
 * spinner.c  -  Animated braille throbber. See spinner.h.
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
static pthread_mutex_t g_lock    = PTHREAD_MUTEX_INITIALIZER;
static volatile int    g_running = 0;
static int             g_started = 0;
static int             g_tty     = 0;
static char            g_msg[256];

static void *spin_loop(void *arg)
{
    (void)arg;
    int i = 0;
    while (g_running) {
        pthread_mutex_lock(&g_lock);
        /* \r to column 0, \033[K clears the line, then draw frame + msg. */
        printf("\r\033[K\033[36m%s\033[0m %s", FRAMES[i % NFRAMES], g_msg);
        fflush(stdout);
        pthread_mutex_unlock(&g_lock);
        i++;
        usleep(INTERVAL_US);
    }
    return NULL;
}

void spinner_start(const char *msg)
{
    if (g_started) return;

    snprintf(g_msg, sizeof(g_msg), "%s", msg ? msg : "");
    g_tty = isatty(STDOUT_FILENO);

    if (!g_tty) {
        /* No animation when not attached to a terminal. */
        printf("%s\n", g_msg);
        fflush(stdout);
        return;
    }

    g_running = 1;
    if (pthread_create(&g_thread, NULL, spin_loop, NULL) == 0) {
        g_started = 1;
    } else {
        g_running = 0;   /* fall back silently */
    }
}

void spinner_set_message(const char *msg)
{
    pthread_mutex_lock(&g_lock);
    snprintf(g_msg, sizeof(g_msg), "%s", msg ? msg : "");
    pthread_mutex_unlock(&g_lock);
}

void spinner_stop(void)
{
    if (g_started) {
        g_running = 0;
        pthread_join(g_thread, NULL);
        g_started = 0;
        printf("\r\033[K");   /* erase the spinner line */
        fflush(stdout);
    }
}
