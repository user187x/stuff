#!/usr/bin/env python3

import sys
import time
import threading

class Spinner:
    """
    A lightweight context‑manager that displays a rotating spinner in the terminal.
    The spinner stops automatically when exiting the `with` block.
    """
    _SPINNER_STATES = ['|', '/', '-', '\\']

    def __init__(self, message: str = '', delay: float = 0.1):
        """
        Parameters
        ----------
        message : str
            Optional text to print before the spinner (e.g. "Loading…").
        delay   : float
            Time in seconds between frame updates.
        """
        self.message = message
        self.delay = delay
        self._running = False
        self._thread = None

    def _spin(self):
        """Internal thread target – rotates the spinner until stopped."""
        idx = 0
        while self._running:
            char = self._SPINNER_STATES[idx % len(self._SPINNER_STATES)]
            sys.stdout.write(f'\r{self.message} {char}')
            sys.stdout.flush()
            time.sleep(self.delay)
            idx += 1
        # Clean up the line when done
        sys.stdout.write('\r' + ' ' * (len(self.message) + 2) + '\r')
        sys.stdout.flush()

    def start(self):
        """Start the spinner thread."""
        if not self._running:
            self._running = True
            self._thread = threading.Thread(target=self._spin, daemon=True)
            self._thread.start()

    def stop(self):
        """Stop the spinner and wait for the thread to finish."""
        if self._running:
            self._running = False
            self._thread.join()
            self._thread = None

    # Context‑manager helpers ---------------------------------------------
    def __enter__(self):
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.stop()

# -------------------------------------------------------------------------
# Example usage – replace `do_some_work()` with your long‑running code.
if __name__ == "__main__":
    import random

    def do_some_work():
        """Simulate a task that takes 3–6 seconds."""
        time.sleep(random.uniform(3, 6))

    print("Starting demo. Press Ctrl+C to abort.")
    try:
        with Spinner('Working'):
            do_some_work()
        print("Done!")
    except KeyboardInterrupt:
        print("\nAborted by user")
