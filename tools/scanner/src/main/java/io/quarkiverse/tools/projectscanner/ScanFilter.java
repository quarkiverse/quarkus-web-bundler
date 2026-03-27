package io.quarkiverse.tools.projectscanner;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.function.Predicate;

public interface ScanFilter {
    boolean test(String path);

    /**
     * Creates a filter from a prefixed pattern (e.g. {@code "glob:**.html"} or {@code "regex:.*\\.js"}).
     *
     * @return the filter, or {@code null} if the pattern is null/empty
     */
    static ScanFilter fromPattern(String pattern) {
        var matcher = ProjectScanner.createPathMatcher(pattern);
        return matcher != null ? new PathFilter(matcher) : null;
    }

    /**
     * Creates a filter from a glob pattern (e.g. {@code "**.html"}, {@code "*.js"}).
     *
     * @return the filter, or {@code null} if the glob is null/empty
     */
    static ScanFilter fromGlob(String glob) {
        if (glob == null || glob.isEmpty()) {
            return null;
        }
        return fromPattern("glob:" + glob);
    }

    record PathFilter(PathMatcher matcher) implements ScanFilter {
        public boolean test(String path) {
            return matcher.matches(Path.of(path));
        }
    }

    record CustomFilter(Predicate<String> predicate) implements ScanFilter {
        public boolean test(String path) {
            return predicate.test(path);
        }
    }
}
