package android.util;

/** JVM boundary for StateManager's NO_WRAP binary save fields. */
public final class Base64 {
    public static final int NO_WRAP = 2;
    public static final int DEFAULT = 0;
    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }
    public static byte[] decode(String input, int flags) {
        return java.util.Base64.getMimeDecoder().decode(input);
    }
}
