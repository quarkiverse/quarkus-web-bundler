package io.quarkiverse.tools.projectscanner.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import io.quarkiverse.tools.projectscanner.exception.DirOutsideRootException;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;

public final class ProjectUtils {

    /**
     * Resolves a relative directory against a root path and validates that
     * the result is under the root (prevents {@code ".."} escaping).
     *
     * @param root the root directory to resolve against
     * @param relativeDir the relative directory path (must not be absolute)
     * @return the resolved and normalized path, or {@code null} if the directory doesn't exist
     * @throws DirOutsideRootException if the resolved path escapes the root
     * @throws IllegalArgumentException if relativeDir is null, empty, or absolute
     */
    public static Path resolveSubDir(Path root, String relativeDir) {
        if (relativeDir == null || relativeDir.isBlank()) {
            throw new IllegalArgumentException("relativeDir must not be null or empty");
        }

        String trimmed = relativeDir.trim();

        if (StringPaths.isRooted(trimmed)) {
            throw new IllegalArgumentException(
                    "relativeDir must not be absolute: " + trimmed);
        }

        Path dir = Paths.get(trimmed);

        return resolveSubDir(root, dir);
    }

    /**
     * Resolves a directory against a root path. If the directory is absolute, it is validated
     * to be under the root. If relative, it is resolved against the root.
     *
     * @param root the root directory
     * @param dir the directory path (relative or absolute)
     * @return the resolved and normalized path, or {@code null} if the directory doesn't exist
     * @throws DirOutsideRootException if the resolved path escapes the root
     * @throws IllegalArgumentException if dir is null
     */
    public static Path resolveSubDir(Path root, Path dir) {
        if (dir == null) {
            throw new IllegalArgumentException("dir must not be null");
        }

        boolean absolute = StringPaths.isRooted(dir.toString());
        Path resolved = absolute ? dir.normalize() : root.resolve(dir).normalize();

        if (!resolved.startsWith(root)) {
            throw new DirOutsideRootException(
                    "Directory '%s' resolves outside root '%s'".formatted(dir, root));
        }

        if (!Files.isDirectory(resolved)) {
            return null;
        }

        return resolved;
    }

    public static Collection<Path> findSrcDirs(CurateOutcomeBuildItem curateOutcome) {
        final Set<Path> paths = new HashSet<>();
        for (WorkspaceModule workspaceModule : curateOutcome.getApplicationModel().getWorkspaceModules()) {
            if (workspaceModule.getMainSources() != null) {
                for (SourceDir resourceDir : workspaceModule.getMainSources().getSourceDirs()) {
                    paths.add(resourceDir.getDir());
                }
            }
        }
        return paths;
    }

    public static Collection<Path> findSrcResourcesDirs(CurateOutcomeBuildItem curateOutcome) {
        final Set<Path> paths = new HashSet<>();
        for (WorkspaceModule workspaceModule : curateOutcome.getApplicationModel().getWorkspaceModules()) {
            if (workspaceModule.getMainSources() != null) {
                for (SourceDir resourceDir : workspaceModule.getMainSources().getResourceDirs()) {
                    paths.add(resourceDir.getDir());
                }
            }
        }
        return paths;
    }

    public static Path findProjectRoot(Path outputDirectory) {
        Path currentPath = outputDirectory;
        do {
            if (Files.exists(currentPath.resolve(Paths.get("src", "main")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.properties")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yaml")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yml")))) {
                return currentPath.normalize();
            }
            if (currentPath.getParent() != null) {
                currentPath = currentPath.getParent();
            } else {
                return null;
            }
        } while (true);
    }

}
