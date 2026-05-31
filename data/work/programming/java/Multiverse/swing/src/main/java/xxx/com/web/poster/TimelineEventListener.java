package xxx.com.web.poster;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Request;
import okhttp3.Response;
import org.jfree.data.gantt.Task;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An OkHttp EventListener that records timing data for various phases of a request
 * and formats it for use in a JFreeChart Gantt chart.
 */
public class TimelineEventListener extends EventListener {
  private final long callStartNanos;
  private final TaskSeries series;

  // Track start times for each phase
  private long dnsStartNanos;
  private long connectStartNanos;
  private long secureConnectStartNanos;
  private long requestHeadersStartNanos;
  private long requestBodyStartNanos;
  private long responseHeadersStartNanos;
  private long responseBodyStartNanos;


  private TimelineEventListener(long callStartNanos) {
    this.callStartNanos = callStartNanos;
    this.series = new TaskSeries("Request Phases");
  }

  private void recordEvent(String eventName, long startNanos, long endNanos) {
    long startMs = TimeUnit.NANOSECONDS.toMillis(startNanos - callStartNanos);
    long endMs = TimeUnit.NANOSECONDS.toMillis(endNanos - callStartNanos);
    if (startMs < endMs) { // Only add tasks with a duration
      series.add(new Task(eventName, new Date(startMs), new Date(endMs)));
    }
  }

  @Override
  public void callStart(Call call) {
    // Mark the absolute start of the call
  }

  @Override
  public void dnsStart(Call call, String domainName) {
    dnsStartNanos = System.nanoTime();
  }

  @Override
  public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
    if (dnsStartNanos > 0) {
      recordEvent("DNS Lookup", dnsStartNanos, System.nanoTime());
    }
  }

  @Override
  public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
    connectStartNanos = System.nanoTime();
  }

  @Override
  public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, okhttp3.Protocol protocol) {
    if (connectStartNanos > 0) {
      recordEvent("Connect", connectStartNanos, System.nanoTime());
    }
  }


  @Override
  public void secureConnectStart(Call call) {
    secureConnectStartNanos = System.nanoTime();
  }

  @Override
  public void secureConnectEnd(Call call, okhttp3.Handshake handshake) {
    if (secureConnectStartNanos > 0) {
      recordEvent("TLS Handshake", secureConnectStartNanos, System.nanoTime());
    }
  }

  @Override
  public void requestHeadersStart(Call call) {
    requestHeadersStartNanos = System.nanoTime();
  }

  @Override
  public void requestHeadersEnd(Call call, Request request) {
    if (requestHeadersStartNanos > 0) {
      recordEvent("Sending Headers", requestHeadersStartNanos, System.nanoTime());
    }
  }

  @Override
  public void requestBodyStart(Call call) {
    requestBodyStartNanos = System.nanoTime();
  }

  @Override
  public void requestBodyEnd(Call call, long byteCount) {
    if (requestBodyStartNanos > 0) {
      recordEvent("Sending Body", requestBodyStartNanos, System.nanoTime());
    }
  }


  @Override
  public void responseHeadersStart(Call call) {
    responseHeadersStartNanos = System.nanoTime();
  }

  @Override
  public void responseHeadersEnd(Call call, Response response) {
    if (responseHeadersStartNanos > 0) {
      recordEvent("Waiting (TTFB)", responseHeadersStartNanos, System.nanoTime());
    }
    responseBodyStartNanos = System.nanoTime();
  }

  @Override
  public void responseBodyStart(Call call) {
    if (responseBodyStartNanos == 0) {
      responseBodyStartNanos = System.nanoTime();
    }
  }

  @Override
  public void responseBodyEnd(Call call, long byteCount) {
    if (responseBodyStartNanos > 0) {
      recordEvent("Content Download", responseBodyStartNanos, System.nanoTime());
    }
  }

  @Override
  public void callEnd(Call call) {
    // Total duration - create a summary task if needed
    long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callStartNanos);
    // Could add a "Total" task here if desired
  }

  @Override
  public void callFailed(Call call, IOException ioe) {
    // Handle failed calls if necessary
    long failureTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callStartNanos);
    series.add(new Task("Failed", new Date(0), new Date(failureTimeMs)));
  }

  public static class Factory implements EventListener.Factory {
    private TimelineEventListener lastInstance;

    @Override
    public EventListener create(Call call) {
      lastInstance = new TimelineEventListener(System.nanoTime());
      return lastInstance;
    }

    public TaskSeriesCollection getTimelineData() {
      if (lastInstance != null) {
        TaskSeriesCollection dataset = new TaskSeriesCollection();
        dataset.add(lastInstance.series);
        return dataset;
      }
      return new TaskSeriesCollection(); // Return empty dataset if no request was made
    }
  }
}
