package android.os;

/** JVM-only scheduler boundary: delayed keepalive/reconnect never run. */
public class Handler {
    public Handler(Looper looper) { }
    public boolean postDelayed(Runnable runnable, long delayMillis) { return true; }
    public void removeCallbacks(Runnable runnable) { }
    public void removeCallbacksAndMessages(Object token) { }
}
