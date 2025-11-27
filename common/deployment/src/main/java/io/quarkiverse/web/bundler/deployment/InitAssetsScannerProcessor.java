package io.quarkiverse.web.bundler.deployment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.DevWatcherBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ProjectRootBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDirBuildItem;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeEvent;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.runtime.LaunchMode;

public class InitAssetsScannerProcessor {

    private static final Logger LOG = Logger.getLogger(InitAssetsScannerProcessor.class);
    private static volatile DevWatcher watcher;

    @BuildStep
    ProjectRootBuildItem initProjectRoot(OutputTargetBuildItem outputTarget) {
        final Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
        return new ProjectRootBuildItem(projectRoot);
    }

    @BuildStep
    DevWatcherBuildItem startWatch(WebBundlerConfig config,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchMode,
            List<WebBundlerWatchedDirBuildItem> watchedDirs,
            List<WebDirBuildItem> webDirs,
            CuratedApplicationShutdownBuildItem shutdown) {
        if (launchMode.getLaunchMode() != LaunchMode.DEVELOPMENT || !config.browserLiveReload() || watchedDirs.isEmpty()) {
            return null;
        }
        Collection<FileChangeEvent> previousChanges = null;
        if (watcher != null) {
            previousChanges = watcher.changesHistory();
            watcher.close();
        }
        watcher = new DevWatcher();
        Set<Path> uniquePaths = new HashSet<>();
        for (WebBundlerWatchedDirBuildItem watchedDir : watchedDirs) {
            if (uniquePaths.add(watchedDir.path())) {
                watcher.watchDirectoryRecursively(watchedDir.path());
            }
        }
        watcher.setWebDirs(webDirs.stream().map(WebDirBuildItem::path).toList());
        if (!liveReload.isLiveReload()) {
            shutdown.addCloseTask(() -> {
                if (watcher != null) {
                    watcher.close();
                    watcher = null;
                }
            }, true);
        }
        return new DevWatcherBuildItem(watcher, previousChanges);
    }

    @BuildStep
    ProjectResourcesScannerBuildItem initScanner(
            ProjectRootBuildItem projectRoot,
            WebBundlerConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<WebBundlerWatchedDirBuildItem> watchedDirs,
            BuildProducer<WebDirBuildItem> webDirs,
            LaunchModeBuildItem launchMode,
            ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome) {
        Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        List<ResolvedDependency> extensionArtifacts = curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(Dependency::isRuntimeExtensionArtifact).collect(Collectors.toList());
        final Collection<Path> srcResourcesDirs = launchMode.getLaunchMode().isDevOrTest() ? findSrcResourcesDirs(curateOutcome)
                : List.of();
        final Path projectWebDir = resolveProjectWebDir(config, projectRoot);
        final List<Path> localDirs = new ArrayList<>();
        if (projectWebDir != null) {
            localDirs.add(projectWebDir);
        }
        if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
            for (Path localDir : localDirs) {
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(localDir));
                webDirs.produce(new WebDirBuildItem(localDir));
            }
            for (Path srcDir : findSrcDirs(curateOutcome)) {
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(srcDir));
            }
            for (Path srcResourcesDir : srcResourcesDirs) {
                final Path resourceWebDir = srcResourcesDir.resolve(config.webRoot());
                if (Files.isDirectory(resourceWebDir)) {
                    webDirs.produce(new WebDirBuildItem(resourceWebDir));
                }
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(srcResourcesDir));
            }
            watchedFiles.produce(
                    HotDeploymentWatchedFileBuildItem.builder().setLocationPredicate(p -> p.startsWith(config.webRoot()))
                            .setRestartNeeded(true).build());
            final Path rootConfigDir = projectRoot.path().resolve("config/");
            if (Files.isDirectory(rootConfigDir)) {
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(rootConfigDir));
            }

            if (LOG.isDebugEnabled()) {
                LOG.debugf("Watching resources %s for changes", config.webRoot());
            }
        }

        return new ProjectResourcesScannerBuildItem(allApplicationArchives, extensionArtifacts, srcResourcesDirs,
                localDirs, config.webRoot());
    }

    private static Collection<Path> findSrcDirs(CurateOutcomeBuildItem curateOutcome) {
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

    private static Collection<Path> findSrcResourcesDirs(CurateOutcomeBuildItem curateOutcome) {
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

    private static Path resolveProjectWebDir(WebBundlerConfig config,
            ProjectRootBuildItem projectRoot) {
        if (config.projectWebDir().isEmpty()) {
            return null;
        }
        Path configuredWebDir = Paths.get(config.projectWebDir().get().trim());
        if (!projectRoot.exists() || !Files.isDirectory(projectRoot.path())) {
            if (configuredWebDir.isAbsolute() && Files.isDirectory(configuredWebDir)) {
                configuredWebDir = configuredWebDir.normalize();
            } else {
                LOG.warn(
                        "If not absolute, the project web directory is resolved relative to the project root, but the Web Bundler was not able to find the project root.");
                return null;
            }
        }

        final Path webRoot = Objects.requireNonNull(projectRoot.path()).resolve(configuredWebDir).normalize();

        if (!Files.isDirectory(webRoot)) {
            return null;
        }

        return webRoot;
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
