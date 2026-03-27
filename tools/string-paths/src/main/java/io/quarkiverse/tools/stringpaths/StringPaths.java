package io.quarkiverse.tools.stringpaths;

import java.util.Objects;

/**
 * Utility methods for manipulating string-based paths using forward-slash ({@code /}) separators.
 * All methods operate on plain strings and never touch the filesystem.
 */
public final class StringPaths {

    private StringPaths() {
    }

    /**
     * Returns {@code true} if the path looks absolute on any platform:
     * starts with {@code /}, {@code \}, or a Windows drive letter ({@code C:\}, {@code C:/}).
     */
    public static boolean isRooted(String path) {
        Objects.requireNonNull(path, "path is required");
        if (path.isEmpty()) {
            return false;
        }
        char first = path.charAt(0);
        if (first == '/' || first == '\\') {
            return true;
        }
        return path.length() >= 3 && Character.isLetter(first) && path.charAt(1) == ':'
                && (path.charAt(2) == '/' || path.charAt(2) == '\\');
    }

    /**
     * Converts Windows-style backslashes to forward slashes.
     */
    public static String toUnixPath(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.replaceAll("\\\\", "/");
    }

    /**
     * Ensures the path starts with a leading {@code /}.
     */
    public static String prefixWithSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Ensures the path starts and ends with {@code /}.
     */
    public static String surroundWithSlashes(String path) {
        return prefixWithSlash(addTrailingSlash(path));
    }

    /**
     * Ensures the path ends with a trailing {@code /}.
     */
    public static String addTrailingSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * Adds a trailing {@code /} only if the path has no file extension (no {@code .}).
     */
    public static String addTrailingSlashIfNoExt(String path) {
        Objects.requireNonNull(path, "path is required");
        if (path.contains(".")) {
            return path;
        }
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * Joins two path segments, ensuring exactly one {@code /} between them.
     * If {@code path2} is {@code null}, returns {@code path1} unchanged.
     */
    public static String join(String path1, String path2) {
        Objects.requireNonNull(path1, "path1 is required");
        if (path2 == null) {
            return path1;
        }
        if (path1.isEmpty()) {
            return path2;
        }
        return addTrailingSlash(path1) + removeLeadingSlash(path2);
    }

    /**
     * Removes a leading {@code /} if present.
     */
    public static String removeLeadingSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * Removes a trailing {@code /} if present.
     */
    public static String removeTrailingSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Strips the given prefix (with trailing {@code /} normalization) from the path.
     * Returns the path unchanged if it does not start with the prefix.
     */
    public static String stripPrefix(String path, String prefix) {
        Objects.requireNonNull(path, "path is required");
        Objects.requireNonNull(prefix, "prefix is required");
        String normalizedPrefix = addTrailingSlash(prefix);
        if (path.startsWith(normalizedPrefix)) {
            return path.substring(normalizedPrefix.length());
        }
        return path;
    }

    /**
     * Returns the portion of the path after the first occurrence of the given directory segment.
     * The segment is matched as a complete directory name — bounded by {@code /} or start/end.
     * A partial name match (e.g., "content" inside "my-content") is NOT a match.
     * Returns the path unchanged if the segment is not found.
     *
     * @param path the path to search
     * @param segment the directory segment to find
     * @return the sub-path after the segment, or the original path if not found
     */
    public static String subPathFrom(String path, String segment) {
        Objects.requireNonNull(path, "path is required");
        Objects.requireNonNull(segment, "segment is required");
        String normalizedSegment = removeTrailingSlash(segment);
        if (normalizedSegment.isEmpty()) {
            return path;
        }
        String segmentSlash = addTrailingSlash(normalizedSegment);
        String slashSegmentSlash = prefixWithSlash(segmentSlash);

        // Check at start of path: "content/..."
        if (path.startsWith(segmentSlash)) {
            return stripPrefix(path, normalizedSegment);
        }
        // Check exact match: entire path equals segment
        if (path.equals(normalizedSegment)) {
            return "";
        }

        // Check in middle of path: ".../content/..."
        int idx = path.indexOf(slashSegmentSlash);
        if (idx >= 0) {
            return path.substring(idx + slashSegmentSlash.length());
        }

        // Check at end of path: ".../content"
        if (path.endsWith(prefixWithSlash(normalizedSegment))) {
            return "";
        }

        return path;
    }

    /**
     * Removes the file extension (everything after and including the last {@code .}).
     */
    public static String removeExtension(String path) {
        Objects.requireNonNull(path, "path is required");
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(0, i) : path;
    }

    /**
     * Returns the file extension (without the {@code .}), or {@code null} if none.
     */
    public static String fileExtension(String path) {
        Objects.requireNonNull(path, "path is required");
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(i + 1) : null;
    }

    /**
     * Returns the file name (the part after the last {@code /}), or the whole path if no {@code /}.
     */
    public static String fileName(String path) {
        Objects.requireNonNull(path, "path is required");
        final int i = path.lastIndexOf("/");
        if (i == -1) {
            return path;
        }
        return path.substring(i + 1);
    }

    /**
     * Converts a string to a URL-friendly slug, replacing non-alphanumeric characters with hyphens.
     *
     * @param value the string to slugify
     * @param allowSlashes when {@code true}, forward slashes are preserved
     * @param allowDots when {@code true}, dots are preserved
     */
    public static String slugify(String value, boolean allowSlashes, boolean allowDots) {
        Objects.requireNonNull(value, "value is required");
        return value
                .replaceAll("[^a-zA-Z0-9_\\-" + (allowDots ? "\\." : "") + (allowSlashes ? "/" : "") + "]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
