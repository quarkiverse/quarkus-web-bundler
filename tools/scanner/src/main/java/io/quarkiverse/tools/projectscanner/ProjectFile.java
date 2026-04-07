package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;

public interface ProjectFile {

    /**
     * The file path pointing to the built output (target/classes, JAR entry).
     * For local project files, this is the file itself.
     * May be null for synthetic files (e.g. generated classpath resources with no backing file).
     * Use {@link #isLocalFile()} to check before accessing.
     */
    Path file();

    /**
     * The source file path, if available.
     * For local project files, this is the same as {@link #file()}.
     * For application resources with a source, this points to src/main/resources/...
     * May be null for dependency resources and application resources without a source.
     * Use {@link #hasSource()} to check before accessing.
     */
    Path source();

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

    /**
     * The raw content of the file as bytes.
     * Read lazily from the filesystem or classloader depending on the implementation.
     */
    byte[] content();

    /**
     * The charset used to decode this file's content into text.
     */
    Charset charset();

    /**
     * The origin of this file, indicating whether it comes from the local project,
     * application resources, or a dependency.
     */
    Origin origin();

    /**
     * Whether this file has a source path (can be symlinked in dev mode).
     */
    default boolean hasSource() {
        return source() != null;
    }

    /**
     * Whether this file is on the local filesystem (can be copied or symlinked).
     * Files inside JARs return false.
     */
    default boolean isLocalFile() {
        return file() != null && isLocalFileSystem(file());
    }

    /**
     * Returns the path to use for Quarkus live reload watching, or null if this file should not be watched.
     * Resources are watched using their relative resource path (indexPath),
     * local project files are watched via their absolute filesystem path.
     */
    default String liveReloadWatchPath() {
        return switch (origin()) {
            case LOCAL_PROJECT_FILE -> file() != null ? file().toString() : null;
            case DEPENDENCY_RESOURCE, APPLICATION_RESOURCE -> indexPath();
        };
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
        DEPENDENCY_RESOURCE // File is in a dependency (jar or local)
        ;

    }

}