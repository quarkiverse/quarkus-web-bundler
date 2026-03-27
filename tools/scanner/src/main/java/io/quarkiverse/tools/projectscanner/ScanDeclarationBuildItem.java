package io.quarkiverse.tools.projectscanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item that declares which resources an extension is interested in.
 * The scanner unions all declarations and only indexes files matching at least one.
 * <p>
 * Wraps a {@link ScanQuery}. Use the static factory methods for common cases,
 * or construct a {@link ScanQuery} directly for advanced use.
 */
public final class ScanDeclarationBuildItem extends MultiBuildItem {
    private final ScanQuery query;

    public ScanDeclarationBuildItem(ScanQuery query) {
        this.query = Objects.requireNonNull(query, "query");
    }

    /**
     * Declares interest in all files under the given scope directory.
     */
    public static ScanDeclarationBuildItem of(String scopeDir) {
        return new ScanDeclarationBuildItem(new ScanQuery(
                List.of(normalizeScopeDir(scopeDir)),
                List.of(), List.of(), Set.of()));
    }

    /**
     * Declares interest in all files under the given scope directory, excluding patterns.
     *
     * @param excludePatterns patterns to exclude (e.g. {@code "glob:**.map"})
     */
    public static ScanDeclarationBuildItem of(String scopeDir, List<String> excludePatterns) {
        return new ScanDeclarationBuildItem(new ScanQuery(
                List.of(normalizeScopeDir(scopeDir)),
                List.of(), compileExcludes(excludePatterns), Set.of()));
    }

    /**
     * Declares interest in files under the given scope directory matching the glob.
     */
    public static ScanDeclarationBuildItem ofGlob(String scopeDir, String glob) {
        var filter = ScanFilter.fromGlob(glob);
        return new ScanDeclarationBuildItem(new ScanQuery(
                List.of(normalizeScopeDir(scopeDir)),
                filter != null ? List.of(filter) : List.of(),
                List.of(), Set.of()));
    }

    public ScanQuery query() {
        return query;
    }

    private static String normalizeScopeDir(String dir) {
        if (dir == null || dir.isEmpty()) {
            return "";
        }
        return StringPaths.addTrailingSlash(dir);
    }

    private static List<ScanFilter> compileExcludes(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }
        List<ScanFilter> filters = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            var filter = ScanFilter.fromPattern(pattern);
            if (filter != null) {
                filters.add(filter);
            }
        }
        return List.copyOf(filters);
    }
}
