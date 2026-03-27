package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;

public interface ProjectFile {

    Path path();

    /**
     * The full path relative to the index base directory.
     * Always uses unix-style forward slashes as separators.
     */
    String indexPath();

    /**
     * The path relative to the query's scope directory.
     * Equals {@link #indexPath()} when no scope is set.
     * Always uses unix-style forward slashes as separators.
     * <p>
     * Note: when querying multiple scope dirs, different files may share the same scopedPath
     * (e.g. {@code app/style.css} and {@code public/style.css} both have scopedPath {@code style.css}).
     * Use {@link #indexPath()} for unique identification.
     */
    String scopedPath();

    byte[] content();

    Charset charset();

    String watchPath();

    Origin origin();

    default boolean isSrcFile() {
        return false;
    }

    static boolean isLocalFileSystem(Path path) {
        try {
            return "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    enum Origin {
        LOCAL_PROJECT_FILE, // File is in a project root subdirectory (not a Java resource)
        APPLICATION_RESOURCE, // File is in the project root resources
        DEPENDENCY_RESOURCE // File is in a dependency jar
        ;

    }

}