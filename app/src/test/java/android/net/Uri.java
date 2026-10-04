package android.net;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** JVM-only boundary for the explicit TCP path URLs in transport contract tests.
 * Not an Android Uri compatibility implementation. */
public final class Uri {
    private final URI value;
    private Uri(String input) { value = URI.create(input); }
    public static Uri parse(String input) { return new Uri(input); }
    public String getScheme() { return value.getScheme(); }
    public String getHost() { return value.getHost(); }
    public int getPort() { return value.getPort(); }
    public String getQueryParameter(String key) {
        if (value.getRawQuery() != null) {
            throw new UnsupportedOperationException("Query URLs are outside this JVM fixture contract");
        }
        return null;
    }
    public List<String> getPathSegments() {
        // Split encoded segments first: %2F is data within a segment, not a separator.
        String path = value.getRawPath();
        return path == null || path.equals("") || path.equals("/")
            ? Collections.emptyList()
            : Arrays.stream(path.substring(1).split("/"))
                .map(segment -> URI.create("/" + segment).getPath().substring(1))
                .collect(java.util.stream.Collectors.toList());
    }
}
