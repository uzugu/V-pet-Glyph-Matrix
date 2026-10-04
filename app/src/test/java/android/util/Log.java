package android.util;

/** JVM-only logging boundary, not a replacement in the application APK. */
public final class Log {
    public static int d(String tag, String message) { return 0; }
    public static int i(String tag, String message) { return 0; }
    public static int w(String tag, String message) { return 0; }
    public static int e(String tag, String message) { return 0; }
    public static int e(String tag, String message, Throwable error) { return 0; }
}
