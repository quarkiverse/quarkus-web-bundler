package io.quarkiverse.tools.projectscanner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;

/**
 * Build item that provides an indexed scanner for project resources.
 * All resources are indexed once during creation; subsequent queries
 * filter against the pre-built index.
 */
public final class ProjectScannerBuildItem extends SimpleBuildItem {

    private final ProjectScanner scanner;

    private ProjectScannerBuildItem(ProjectScanner scanner) {
        this.scanner = scanner;
    }

    public static ProjectScannerBuildItem create(LaunchModeBuildItem launchMode,
            ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            ProjectRootBuildItem projectDir,
            List<ScanLocalDirBuildItem> localProjectDirs,
            List<ScanDeclarationBuildItem> declarations,
            List<String> defaultIgnoredFiles,
            Charset charset,
            int warningThreshold) throws IOException {
        return new ProjectScannerBuildItem(
                ProjectScanner.create(launchMode, applicationArchives, curateOutcome,
                        projectDir, localProjectDirs, declarations, defaultIgnoredFiles, charset,
                        warningThreshold));
    }

    public Map<String, Path> localProjectDirsByName() {
        return scanner.localDirsByName();
    }

    public Collection<Path> localProjectDirs() {
        return scanner.localDirsByName().values();
    }

    /**
     * Returns a builder for configuring and executing scans against the index.
     *
     * @return a new scan config builder
     */
    public ScanQueryBuilder query() {
        return scanner.query();
    }

}
