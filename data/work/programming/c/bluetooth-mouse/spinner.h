/*
 * spinner.h  -  Animated braille throbber (the pip/npm "dots" style).
 *
 * Shows a spinning ⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏ indicator with a message so the user can
 * see the program is alive and waiting. Animation runs on its own thread.
 *
 * If stdout is not a TTY (piped, or running under a service manager), it
 * degrades to a single static line and does not emit any control codes.
 */
#ifndef SPINNER_H
#define SPINNER_H

/* Begin animating with the given message. No-op if already running. */
void spinner_start(const char *msg);

/* Update the message shown next to the spinner while it runs. */
void spinner_set_message(const char *msg);

/* Stop the animation and clear the line. Safe to call when not running. */
void spinner_stop(void);

#endif /* SPINNER_H */
