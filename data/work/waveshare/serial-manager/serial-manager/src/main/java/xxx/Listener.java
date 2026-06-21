package xxx;

public interface Listener {
 boolean isRunning();
 void start();
 void stop();

 /** Temporarily ignores incoming data to prevent reading local echoes */
 void mute();

 /** Resumes reading incoming data */
 void unmute();
}