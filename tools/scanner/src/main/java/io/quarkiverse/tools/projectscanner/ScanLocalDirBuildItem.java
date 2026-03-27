package io.quarkiverse.tools.projectscanner;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Build item that allows processors to contribute local project directories to be scanned
 * by the ProjectScanner. These directories are treated as external file sources
 * (Type.LOCAL_PROJECT_FILE) in the scanner index.
 * <p>
 * The directory can be relative or absolute but must resolve under the project root.
 */
public final class ScanLocalDirBuildItem extends MultiBuildItem {
    private final Path dir;
    private final String indexBase;

    /**
     * Indexes from the project root (paths like {@code web/app/index.js}).
     *
     * @param dir the directory, relative to project root or absolute (must be under project root)
     */
    public ScanLocalDirBuildItem(Path dir) {
        this(dir, null);
    }

    /**
     * @param dir the directory, relative to project root or absolute (must be under project root)
     * @param indexBase relative directory to relativize indexed paths from (must be under project root).
     *        {@code null} means project root (paths like {@code web/app/index.js}).
     */
    public ScanLocalDirBuildItem(Path dir, String indexBase) {
        this.dir = dir;
        this.indexBase = indexBase;
    }

    public Path dir() {
        return dir;
    }

    /**
     * Relative directory to relativize indexed paths from (must be under project root),
     * or {@code null} for project root.
     */
    public String indexBase() {
        return indexBase;
    }
}
