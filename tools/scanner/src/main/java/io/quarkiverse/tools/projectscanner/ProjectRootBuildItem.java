package io.quarkiverse.tools.projectscanner;

import java.nio.file.Path;

import io.quarkiverse.tools.projectscanner.exception.DirOutsideRootException;
import io.quarkiverse.tools.projectscanner.util.ProjectUtils;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ProjectRootBuildItem extends SimpleBuildItem {

    private final Path path;

    public ProjectRootBuildItem(Path path) {
        this.path = path;
    }

    /**
     * Path is null if the project root directory has not been found or doesn't exist on disk.
     */
    public Path path() {
        return path;
    }

    /**
     * @return true if the project root directory is found and exists on disk.
     */
    public boolean exists() {
        return path != null;
    }

    /**
     * Resolves a sub-directory relative to the project root.
     *
     * @param relativeDir the relative directory path (must not be absolute or escape the root)
     * @return the resolved path, or {@code null} if the directory doesn't exist on disk
     * @throws DirOutsideRootException if the resolved path escapes the project root
     * @throws IllegalArgumentException if relativeDir is null, empty, or absolute
     * @throws IllegalStateException if the project root doesn't exist
     */
    public Path resolveSubDir(String relativeDir) {
        if (!exists()) {
            throw new IllegalStateException("Project root does not exist");
        }
        return ProjectUtils.resolveSubDir(path, relativeDir);
    }
}
