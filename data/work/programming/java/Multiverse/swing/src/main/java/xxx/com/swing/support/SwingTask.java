package xxx.com.swing.support;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/***
 * Example Usage : No Progress
 *
 * SwingTasks.run(
 *  () -> connect(ip, port),          // background work
 *    ok -> {                         // onDone (EDT)
 *    setConnectionState(ok, (ok ? "Connected to: " : "Failed: ") + ip + ":" + port);
 *    logMessage("System", ok ? "Successfully connected to host." : "Connection failed.");
 *   },
 *   err -> {                         // onError (EDT)
 *     setConnectionState(false, "Error connecting to: " + ip + ":" + port);
 *     logMessage("System", "Error: " + err.getMessage());
 *    }
 *  );
 **/

/***
 * Example Usage : With Progress
 *
 * SwingTasks.run(
 *   publisher -> {                                                               // background with publisher
 *     publisher.accept("Resolving host…");
 *
 *     // ... busy work ... //
 *     // ... busy work ... //
 *     // ... busy work ... //
 *
 *     publisher.accept("Opening socket…");
 *     boolean ok = connect(ip, port);
 *     publisher.accept(ok ? "Handshake complete." : "Handshake failed.");
 *     return ok;
 *   },
 *   chunks -> chunks.forEach(msg -> logMessage("System", msg)),                           // onProcess (EDT)
 *   ok -> setConnectionState(ok, (ok ? "Connected to: " : "Failed: ") + ip + ":" + port), // onDone (EDT)
 *   err -> logMessage("System", "Error: " + err.getMessage())                             // onError (EDT)
 * );
 */

public final class SwingTask {

  private SwingTask() {}

  /** Simplest form: run background work, then deliver result on EDT. */
  public static <R> void run(Supplier<R> background, Consumer<R> onDone, Consumer<Throwable> onError) {
    new SwingWorker<R, String>() {
      @Override protected R doInBackground() throws Exception {
        return background.get();
      }
      @Override protected void done() {
        try {
          R r = get(); // marshalling exceptions too
          if (onDone != null) onDone.accept(r);
        } catch (Throwable t) {
          if (onError != null) onError.accept(t);
        }
      }
    }.execute();
  }
  /**
   * Advanced: background code can publish progress via the provided publisher.
   * - background.apply(publisher) runs off-EDT
   * - onProcess(chunks) runs on EDT with published messages
   * - onDone(result) runs on EDT when finished
   * - onError(error) runs on EDT if something fails
   */
  public static <R> void run(
      Function<Consumer<String>, R> background,
      Consumer<List<String>> onProcess,
      Consumer<R> onDone,
      Consumer<Throwable> onError) {

    new SwingWorker<R, String>() {
      @Override protected R doInBackground() throws Exception {
        return background.apply(this::publish);
      }
      @Override protected void process(List<String> chunks) {
        if (onProcess != null && chunks != null && !chunks.isEmpty()) {
          onProcess.accept(chunks);
        }
      }
      @Override protected void done() {
        try {
          R r = get();
          if (onDone != null) onDone.accept(r);
        } catch (Throwable t) {
          if (onError != null) onError.accept(t);
        }
      }
    }.execute();
  }
}
