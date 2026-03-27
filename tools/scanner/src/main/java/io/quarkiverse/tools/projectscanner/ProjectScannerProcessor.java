package io.quarkiverse.tools.projectscanner;

import static io.quarkiverse.tools.projectscanner.util.ProjectUtils.findProjectRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class ProjectScannerProcessor {

    @BuildStep
    ProjectRootBuildItem initProjectRoot(OutputTargetBuildItem outputTarget) {
        final Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
        return new ProjectRootBuildItem(projectRoot);
    }

    @BuildStep
    ProjectScannerBuildItem initScanner(
            ProjectRootBuildItem projectRoot,
            List<ScanLocalDirBuildItem> contributedDirs,
            List<ScanDeclarationBuildItem> declarations,
            LaunchModeBuildItem launchMode,
            ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            ProjectScannerConfig scannerConfig) throws IOException {

        return ProjectScannerBuildItem.create(launchMode,
                applicationArchives, curateOutcome, projectRoot, contributedDirs, declarations,
                scannerConfig.defaultIgnoredFiles(), scannerConfig.charset(),
                scannerConfig.indexedFilesWarningThreshold());
    }

}
