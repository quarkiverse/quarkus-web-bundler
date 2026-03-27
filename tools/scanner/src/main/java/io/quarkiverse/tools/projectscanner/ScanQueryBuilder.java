package io.quarkiverse.tools.projectscanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.quarkiverse.tools.stringpaths.StringPaths;

/**
 * Builder for scanning project resources against a pre-built index.
 * <p>
 * A query has two phases:
 * <ol>
 * <li><b>Scope</b> — optional directory constraint set via {@link #scopeDirs(String...)}. Narrows the search
 * to files under the given directories. Multiple directories are OR-composed.</li>
 * <li><b>Filters</b> — matching and exclude filters applied to paths <em>relativized from the scope directory</em>.
 * Matching filters are AND-composed; exclude filters are OR-composed.</li>
 * </ol>
 * When the same path exists from multiple sources (local project, application resource, dependency),
 * the {@link DuplicateStrategy} controls which version is kept (defaults to {@link DuplicateStrategy#PREFER_APP}).
 * <p>
 * Results are always sorted alphabetically by their full indexed path.
 */
public final class ScanQueryBuilder {
    private final ProjectScanner indexedScanner;
    private final List<String> scopeDirs = new ArrayList<>();
    private final List<ScanFilter> matching = new ArrayList<>();
    private final List<ScanFilter> excludes = new ArrayList<>();
    private final Set<ProjectFile.Origin> origins = EnumSet.noneOf(ProjectFile.Origin.class);
    private DuplicateStrategy duplicateStrategy = DuplicateStrategy.PREFER_APP;

    ScanQueryBuilder(ProjectScanner indexedScanner) {
        this.indexedScanner = indexedScanner;
    }

    /** @see #scopeDirs(String...) */
    public ScanQueryBuilder scopeDir(String dir) {
        return scopeDirs(dir);
    }

    /**
     * Constrains the search to files under the given directories.
     * Multiple directories are OR-composed (files under any of them are candidates).
     * Can be called multiple times to add more directories.
     * <p>
     * When a scope is set, all matching and exclude filters receive paths
     * <em>relativized from the scope directory</em>. For example, with scope {@code "web/app"}
     * a file at {@code web/app/index.js} is tested as {@code index.js}.
     *
     * @param dirs the directories (e.g. {@code "web/app"}, {@code "web/public"})
     * @return this builder
     */
    public ScanQueryBuilder scopeDirs(String... dirs) {
        return scopeDirs(List.of(dirs));
    }

    /** @see #scopeDirs(String...) */
    public ScanQueryBuilder scopeDirs(List<String> dirs) {
        dirs.stream()
                .filter(d -> d != null && !d.isEmpty())
                .map(StringPaths::addTrailingSlash)
                .forEach(scopeDirs::add);
        return this;
    }

    /**
     * Restricts results to files from the given origins.
     * Multiple origins are OR-composed. Can be called multiple times to add more origins.
     * When no origin is set, files from all origins are returned.
     */
    public ScanQueryBuilder origin(ProjectFile.Origin... origins) {
        this.origins.addAll(List.of(origins));
        return this;
    }

    /**
     * Adds a matching filter using a glob pattern.
     * AND-composed with other matching filters.
     */
    public ScanQueryBuilder matchingGlob(String glob) {
        var filter = ScanFilter.fromGlob(glob);
        if (filter != null) {
            matching.add(filter);
        }
        return this;
    }

    /**
     * Adds a matching filter using a regex pattern.
     * AND-composed with other matching filters.
     */
    public ScanQueryBuilder matchingRegex(String regex) {
        if (regex != null && !regex.isEmpty()) {
            var filter = ScanFilter.fromPattern("regex:" + regex);
            if (filter != null) {
                matching.add(filter);
            }
        }
        return this;
    }

    /**
     * Adds a matching filter using a prefixed pattern (e.g. {@code "glob:**.html"} or {@code "regex:.*\\.js"}).
     * AND-composed with other matching filters.
     */
    public ScanQueryBuilder matching(String pattern) {
        var filter = ScanFilter.fromPattern(pattern);
        if (filter != null) {
            matching.add(filter);
        }
        return this;
    }

    /**
     * Adds a custom matching filter. AND-composed with other matching filters.
     */
    public ScanQueryBuilder matching(ScanFilter filter) {
        matching.add(Objects.requireNonNull(filter, "filter"));
        return this;
    }

    /**
     * Excludes assets matching this filter.
     */
    public ScanQueryBuilder exclude(ScanFilter filter) {
        excludes.add(Objects.requireNonNull(filter, "filter"));
        return this;
    }

    /**
     * Excludes assets matching this pattern.
     */
    public ScanQueryBuilder exclude(String pattern) {
        var filter = ScanFilter.fromPattern(pattern);
        if (filter != null) {
            excludes.add(filter);
        }
        return this;
    }

    /**
     * Sets the strategy for handling duplicate paths from different sources.
     * Defaults to {@link DuplicateStrategy#PREFER_APP}.
     */
    public ScanQueryBuilder duplicateStrategy(DuplicateStrategy strategy) {
        this.duplicateStrategy = Objects.requireNonNull(strategy, "duplicateStrategy");
        return this;
    }

    /**
     * Excludes assets matching any of the given patterns.
     */
    public ScanQueryBuilder addExcluded(List<String> patterns) {
        for (String pattern : patterns) {
            exclude(pattern);
        }
        return this;
    }

    /**
     * Executes the search against the pre-built index.
     * Results are sorted alphabetically by path and deduplicated
     * according to the configured {@link DuplicateStrategy}.
     */
    public List<ProjectFile> list() throws IOException {
        return indexedScanner.find(buildQuery(), duplicateStrategy);
    }

    /**
     * Merges multiple lists of project files, deduplicating by {@link ProjectFile#scopedPath()}.
     * Earlier lists have higher priority — the first occurrence of a given scoped path wins.
     */
    @SafeVarargs
    public static List<ProjectFile> mergeByScopedPath(List<ProjectFile>... results) {
        Map<String, ProjectFile> byPath = new LinkedHashMap<>();
        for (List<ProjectFile> list : results) {
            for (ProjectFile file : list) {
                byPath.putIfAbsent(file.scopedPath(), file);
            }
        }
        return new ArrayList<>(byPath.values());
    }

    /**
     * Builds the query without executing it.
     * Defaults to root scope (empty string) when no scope dirs are set.
     */
    ScanQuery buildQuery() {
        List<String> dirs = scopeDirs.isEmpty() ? List.of("") : List.copyOf(scopeDirs);
        return new ScanQuery(dirs, List.copyOf(matching), List.copyOf(excludes),
                origins.isEmpty() ? Set.of() : EnumSet.copyOf(origins));
    }
}
