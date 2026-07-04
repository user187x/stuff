/*
 * spinner.h  -  Animated braille throbber (the pip/npm "dots" style).
 *
 * Shows a spinning ⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏ indicator with a message so the user can
 * see the program is alive. A single background thread is created once by
 * spinner_init(); show/hide toggle whether it is drawing.
 *
 * If stdout is not a TTY (piped, or under a service manager), it degrades
 * to a single static line per show() and emits no control codes.
 */
#ifndef SPINNER_H
#define SPINNER_H

/* Create the persistent spinner thread (call once, early). */
void spinner_init(void);

/* Begin drawing the spinner with the given message. */
void spinner_show(const char *msg);

/* Update the message shown while the spinner is visible. */
void spinner_set_message(const char *msg);

/* Stop drawing and clear the line (thread keeps running, idle). */
void spinner_hide(void);

/* Stop and join the spinner thread (call once, at shutdown). */
void spinner_shutdown(void);

#endif /* SPINNER_H */
