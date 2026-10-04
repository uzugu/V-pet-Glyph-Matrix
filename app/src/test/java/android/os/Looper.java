package android.os;

/** JVM-only main-looper boundary; no Android event loop is exercised. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
}
