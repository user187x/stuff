package xxx.beta.usb;

import com.fazecast.jSerialComm.SerialPort;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class which services a {@link SerialPort} in its {@link #runWrite()} and {@link #runRead()} methods.
 */
public class SerialInputOutputManager {

    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }

    public static boolean DEBUG = false;

    private static final Logger LOGGER = Logger.getLogger(SerialInputOutputManager.class.getName());
    private static final int DEFAULT_READ_BUFFER_SIZE = 4096;
    private static final int WRITE_BUFFER_SIZE = 4096;

    private int mReadTimeout = 0;
    private int mWriteTimeout = 0;

    private final Object mReadBufferLock = new Object();
    private final Object mWriteBufferLock = new Object();

    private ByteBuffer mReadBuffer;
    private ByteBuffer mWriteBuffer = ByteBuffer.allocate(WRITE_BUFFER_SIZE);

    private int mThreadPriority = Thread.MAX_PRIORITY;
    private final AtomicReference<State> mState = new AtomicReference<>(State.STOPPED);
    private CountDownLatch mStartuplatch = new CountDownLatch(2);
    private Listener mListener; // Synchronized by 'this'
    private final SerialPort mSerialPort;

    public interface Listener {
        /**
         * Called when new incoming data is available.
         */
        void onNewData(byte[] data);

        /**
         * Called when {@link SerialInputOutputManager#runRead()} or {@link SerialInputOutputManager#runWrite()} aborts due to an error.
         */
        void onRunError(Exception e);
    }

    public SerialInputOutputManager(SerialPort serialPort) {
        mSerialPort = serialPort;
        mReadBuffer = ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE);
    }

    public SerialInputOutputManager(SerialPort serialPort, Listener listener) {
        this(serialPort);
        mListener = listener;
    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;
    }

    public synchronized Listener getListener() {
        return mListener;
    }

    /**
     * setThreadPriority. By default a higher priority than UI thread is used to prevent data loss
     *
     * @param threadPriority  see {@link Thread#setPriority(int)}
     */
    public void setThreadPriority(int threadPriority) {
        if (!mState.compareAndSet(State.STOPPED, State.STOPPED)) {
            throw new IllegalStateException("threadPriority only configurable before SerialInputOutputManager is started");
        }
        mThreadPriority = threadPriority;
    }

    /**
     * read/write timeout
     */
    public void setReadTimeout(int timeout) {
        if(mReadTimeout == 0 && timeout != 0 && mState.get() != State.STOPPED)
            throw new IllegalStateException("readTimeout only configurable before SerialInputOutputManager is started");
        mReadTimeout = timeout;
        updatePortTimeouts();
    }

    public int getReadTimeout() {
        return mReadTimeout;
    }

    public void setWriteTimeout(int timeout) {
        mWriteTimeout = timeout;
        updatePortTimeouts();
    }

    public int getWriteTimeout() {
        return mWriteTimeout;
    }

    private void updatePortTimeouts() {
        if (mSerialPort != null) {
            mSerialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, mReadTimeout, mWriteTimeout);
        }
    }

    /**
     * read/write buffer size
     */
    public void setReadBufferSize(int bufferSize) {
        if (getReadBufferSize() == bufferSize)
            return;
        synchronized (mReadBufferLock) {
            mReadBuffer = ByteBuffer.allocate(bufferSize);
        }
    }

    public int getReadBufferSize() {
        return mReadBuffer.capacity();
    }

    public void setWriteBufferSize(int bufferSize) {
        if(getWriteBufferSize() == bufferSize)
            return;
        synchronized (mWriteBufferLock) {
            ByteBuffer newWriteBuffer = ByteBuffer.allocate(bufferSize);
            if(mWriteBuffer.position() > 0)
                newWriteBuffer.put(mWriteBuffer.array(), 0, mWriteBuffer.position());
            mWriteBuffer = newWriteBuffer;
        }
    }

    public int getWriteBufferSize() {
        return mWriteBuffer.capacity();
    }

    /**
     * write data asynchronously
     */
    public void writeAsync(byte[] data) {
        synchronized (mWriteBufferLock) {
            mWriteBuffer.put(data);
            mWriteBufferLock.notifyAll(); // Notify waiting threads
        }
    }

    /**
     * start SerialInputOutputManager in separate threads
     */
    public void start() {
        if(mState.compareAndSet(State.STOPPED, State.STARTING)) {
            mStartuplatch = new CountDownLatch(2);
            new Thread(this::runRead, this.getClass().getSimpleName() + "_read").start();
            new Thread(this::runWrite, this.getClass().getSimpleName() + "_write").start();
            try {
                mStartuplatch.await();
                mState.set(State.RUNNING);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            throw new IllegalStateException("already started");
        }
    }

    /**
     * stop SerialInputOutputManager threads
     */
    public void stop() {
        if(mState.compareAndSet(State.RUNNING, State.STOPPING)) {
            synchronized (mWriteBufferLock) {
                mWriteBufferLock.notifyAll(); // wake up write thread to check the stop condition
            }
            LOGGER.info("Stop requested");
        }
    }

    public State getState() {
        return mState.get();
    }

    private boolean isStillRunning() {
        State state = mState.get();
        return ((state == State.RUNNING) || (state == State.STARTING))
                && !Thread.currentThread().isInterrupted();
    }

    private void notifyErrorListener(Throwable e) {
        Listener listener = getListener();
        if (listener != null) {
            try {
                listener.onRunError(e instanceof Exception ? (Exception) e : new Exception(e));
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Exception in onRunError: " + t.getMessage(), t);
            }
        }
    }

    private void applyThreadPriority() {
        Thread.currentThread().setPriority(mThreadPriority);
    }

    void runRead() {
        LOGGER.info("runRead running ...");
        try {
            applyThreadPriority();
            mStartuplatch.countDown();
            do {
                stepRead();
            } while (isStillRunning());
            LOGGER.info("runRead: Stopping mState=" + getState());
        } catch (Throwable e) {
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.warning("runRead: interrupted");
            } else if(mSerialPort.isOpen()) {
                LOGGER.log(Level.WARNING, "runRead ending due to exception: " + e.getMessage(), e);
            } else {
                LOGGER.info("runRead: Socket closed");
            }
            notifyErrorListener(e);
        } finally {
            if (mState.compareAndSet(State.RUNNING, State.STOPPING)) {
                synchronized (mWriteBufferLock) {
                    mWriteBufferLock.notifyAll();
                }
            } else if (mState.compareAndSet(State.STOPPING, State.STOPPED)) {
                LOGGER.info("runRead: Stopped mState=" + getState());
            }
        }
    }

    void runWrite() {
        LOGGER.info("runWrite running ...");
        try {
            applyThreadPriority();
            mStartuplatch.countDown();
            do {
                stepWrite();
            } while (isStillRunning());
            LOGGER.info("runWrite: Stopping mState=" + getState());
        } catch (Throwable e) {
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.warning("runWrite: interrupted");
            } else if(mSerialPort.isOpen()) {
                LOGGER.log(Level.WARNING, "runWrite ending due to exception: " + e.getMessage(), e);
            } else {
                LOGGER.info("runWrite: Socket closed");
            }
            notifyErrorListener(e);
        } finally {
            if (!mState.compareAndSet(State.RUNNING, State.STOPPING)) {
                if (mState.compareAndSet(State.STOPPING, State.STOPPED)) {
                    LOGGER.info("runWrite: Stopped mState=" + getState());
                }
            }
        }
    }

    private void stepRead() {
        byte[] buffer;
        synchronized (mReadBufferLock) {
            buffer = mReadBuffer.array();
        }

        // Block and read from jSerialComm
        int len = mSerialPort.readBytes(buffer, buffer.length);

        if (len > 0) {
            if (DEBUG) {
                LOGGER.fine("Read data len=" + len);
            }
            final Listener listener = getListener();
            if (listener != null) {
                final byte[] data = new byte[len];
                System.arraycopy(buffer, 0, data, 0, len);
                listener.onNewData(data);
            }
        }
    }

    private void stepWrite() throws InterruptedException {
        byte[] buffer = null;
        synchronized (mWriteBufferLock) {
            int len = mWriteBuffer.position();
            if (len > 0) {
                buffer = new byte[len];
                mWriteBuffer.rewind();
                mWriteBuffer.get(buffer, 0, len);
                mWriteBuffer.clear();
                mWriteBufferLock.notifyAll(); // Notify writeAsync that there is space in the buffer
            } else {
                mWriteBufferLock.wait();
            }
        }
        if (buffer != null) {
            if (DEBUG) {
                LOGGER.fine("Writing data len=" + buffer.length);
            }
            mSerialPort.writeBytes(buffer, buffer.length);
        }
    }
}