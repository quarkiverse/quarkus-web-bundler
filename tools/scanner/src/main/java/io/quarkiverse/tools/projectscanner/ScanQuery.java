package io.quarkiverse.tools.projectscanner;

import java.util.List;
import java.util.Set;

/**
 * Describes which files to match: scope directories, matching filters, exclude filters, and origin constraints.
 * <p>
 * Used both for index-time declarations ({@link ScanDeclarationBuildItem}) and query-time filtering
 * ({@link ScanQueryBuilder}).
 *
 * @param scopeDirs directories to include (OR-composed). Empty string matches the root.
 * @param matching optional filters within the scope (AND-composed)
 * @param excludes optional filters to exclude files (OR-composed)
 * @param origins optional origin constraints (OR-composed). Empty means all origins.
 */
public record ScanQuery(
        List<String> scopeDirs,
        List<ScanFilter> matching,
        List<ScanFilter> excludes,
        Set<ProjectFile.Origin> origins) {

    public ScanQuery {
        scopeDirs = List.copyOf(scopeDirs);
        matching = List.copyOf(matching);
        excludes = List.copyOf(excludes);
        origins = origins.isEmpty() ? Set.of() : Set.copyOf(origins);
    }

    /**
     * Tests if a file at the given index path matches this query's scope + filters.
     * Does not check origins.
     */
    public boolean matches(String indexPath) {
        String scopedPath = null;
        for (String scopeDir : scopeDirs) {
            if (scopeDir.isEmpty()) {
                scopedPath = indexPath;
                break;
            }
            if (indexPath.startsWith(scopeDir)) {
                scopedPath = indexPath.substring(scopeDir.length());
                break;
            }
        }
        if (scopedPath == null) {
            return false;
        }
        for (ScanFilter filter : matching) {
            if (!filter.test(scopedPath)) {
                return false;
            }
        }
        for (ScanFilter filter : excludes) {
            if (filter.test(scopedPath)) {
                return false;
            }
        }
        return true;
    }
}
