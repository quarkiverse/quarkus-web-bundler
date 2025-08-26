package io.quarkiverse.web.bundler.deployment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ProjectRootBuildItem;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.runtime.LaunchMode;

public class InitAssetsScannerProcessor {

    private static final Logger LOG = Logger.getLogger(InitAssetsScannerProcessor.class);

    @BuildStep
    ProjectRootBuildItem initProjectRoot(OutputTargetBuildItem outputTarget) {
        final Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
        return new ProjectRootBuildItem(projectRoot);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void startWatch(LiveReloadBuildItem liveReload,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            List<WebBundlerWatchedDirBuildItem> watchedDirs,
            CuratedApplicationShutdownBuildItem shutdown) {
        Watcher.start(true);
        Set<Path> uniquePaths = new HashSet<>();
        for (WebBundlerWatchedDirBuildItem watchedDir : watchedDirs) {
            if (uniquePaths.add(watchedDir.path())) {
                Watcher.watchDirectoryRecursively(watchedDir.path(), watchedDir.web());
            }
        }

        if (!liveReload.isLiveReload()) {
            // Only the first time
            shutdown.addCloseTask(Watcher::stop, true);
        }
    }

    @BuildStep
    ProjectResourcesScannerBuildItem initScanner(
            ProjectRootBuildItem projectRoot,
            WebBundlerConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<WebBundlerWatchedDirBuildItem> watchedDirs,
            LaunchModeBuildItem launchMode,
            ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome) {
        Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        List<ResolvedDependency> extensionArtifacts = curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(Dependency::isRuntimeExtensionArtifact).collect(Collectors.toList());
        final Collection<Path> srcResourcesDirs = launchMode.getLaunchMode().isDevOrTest() ? findSrcResourcesDirs(curateOutcome)
                : List.of();
        final List<Path> projectWebDirs = resolveProjectWebDirs(config, projectRoot);

        if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
            for (Path projectWebDir : projectWebDirs) {
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(projectWebDir, true));
            }
            for (Path srcDir : findSrcDirs(curateOutcome)) {
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(srcDir, false));
            }
            for (Path srcResourcesDir : srcResourcesDirs) {
                // TODO web should be true
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(srcResourcesDir, false));
            }
            watchedFiles.produce(
                    HotDeploymentWatchedFileBuildItem.builder().setLocationPredicate(p -> p.startsWith(config.webRoot()))
                            .setRestartNeeded(true).build());

            if (LOG.isDebugEnabled()) {
                LOG.debugf("Watching resources %s for changes", config.webRoot());
            }
        }

        return new ProjectResourcesScannerBuildItem(allApplicationArchives, extensionArtifacts, srcResourcesDirs,
                projectWebDirs, config.webRoot());
    }

    private static Collection<Path> findSrcDirs(CurateOutcomeBuildItem curateOutcome) {
        final Set<Path> paths = new HashSet<>();
        for (WorkspaceModule workspaceModule : curateOutcome.getApplicationModel().getWorkspaceModules()) {
            for (SourceDir resourceDir : workspaceModule.getMainSources().getSourceDirs()) {
                paths.add(resourceDir.getDir());
            }
        }
        return paths;
    }

    private static Collection<Path> findSrcResourcesDirs(CurateOutcomeBuildItem curateOutcome) {
        final Set<Path> paths = new HashSet<>();
        for (WorkspaceModule workspaceModule : curateOutcome.getApplicationModel().getWorkspaceModules()) {
            for (SourceDir resourceDir : workspaceModule.getMainSources().getResourceDirs()) {
                paths.add(resourceDir.getDir());
            }
        }
        return paths;
    }

    private static List<Path> resolveProjectWebDirs(WebBundlerConfig config,
            ProjectRootBuildItem projectRoot) {
        if (config.projectWebDir().isEmpty()) {
            return List.of();
        }
        Path configuredSiteDirPath = Paths.get(config.projectWebDir().get().trim());
        if (!projectRoot.exists() || !Files.isDirectory(projectRoot.path())) {
            if (configuredSiteDirPath.isAbsolute() && Files.isDirectory(configuredSiteDirPath)) {
                configuredSiteDirPath = configuredSiteDirPath.normalize();
            } else {
                LOG.warn(
                        "If not absolute, the project web directory is resolved relative to the project root, but the Web Bundler was not able to find the project root.");
                return List.of();
            }
        }

        final Path webRoot = Objects.requireNonNull(projectRoot.path()).resolve(configuredSiteDirPath).normalize();

        if (!Files.isDirectory(webRoot)) {
            return List.of();
        }

        return List.of(webRoot);
    }

    private static Path findProjectRoot(Path outputDirectory) {
        Path currentPath = outputDirectory;
        do {
            if (Files.exists(currentPath.resolve(Paths.get("src", "main")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.properties")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yaml")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yml")))) {
                return currentPath.normalize();
            }
            if (currentPath.getParent() != null && Files.exists(currentPath.getParent())) {
                currentPath = currentPath.getParent();
            } else {
                return null;
            }
        } while (true);
    }

    public static void watch(BuildProducer<HotDeploymentWatchedFileBuildItem> watch, String dir) {

    }

}
