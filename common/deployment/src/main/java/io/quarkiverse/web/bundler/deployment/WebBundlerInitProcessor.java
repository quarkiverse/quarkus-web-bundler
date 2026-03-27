package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.tools.projectscanner.util.ProjectUtils.findSrcDirs;
import static io.quarkiverse.tools.projectscanner.util.ProjectUtils.findSrcResourcesDirs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ScanDeclarationBuildItem;
import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.WatchedWebDirsBuildItem;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class WebBundlerInitProcessor {

    private static final Logger LOG = Logger.getLogger(WebBundlerInitProcessor.class);

    @BuildStep
    ScanDeclarationBuildItem declareScannedResources(WebBundlerConfig config) {
        return ScanDeclarationBuildItem.of(config.webRoot(), config.ignoredFilesOrEmpty());
    }

    @BuildStep
    ScanLocalDirBuildItem contributeProjectWebDir(
            WebBundlerConfig config) {
        if (config.localWebDir()) {
            return new ScanLocalDirBuildItem(Path.of(config.webRoot()));
        }
        return null;
    }

    @BuildStep
    void setupWatching(
            ProjectRootBuildItem projectRoot,
            WebBundlerConfig config,
            LaunchModeBuildItem launchMode,
            ProjectScannerBuildItem projectScanner,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<WebBundlerWatchedDirBuildItem> watchedDirs,
            BuildProducer<WatchedWebDirsBuildItem> watchedWebDirs) {

        if (launchMode.getLaunchMode() != LaunchMode.DEVELOPMENT) {
            return;
        }

        final Collection<Path> srcResourcesDirs = launchMode.getLaunchMode().isDevOrTest()
                ? findSrcResourcesDirs(curateOutcome)
                : List.of();

        // Watch all local project directories
        for (Path localDir : projectScanner.localProjectDirs()) {
            watchedDirs.produce(new WebBundlerWatchedDirBuildItem(localDir));
        }
        for (Path srcDir : findSrcDirs(curateOutcome)) {
            watchedDirs.produce(new WebBundlerWatchedDirBuildItem(srcDir));
        }
        for (Path srcResourcesDir : srcResourcesDirs) {
            watchedDirs.produce(new WebBundlerWatchedDirBuildItem(srcResourcesDir));
        }

        // Only watch the configured projectWebDir as a web directory, not all local dirs
        final List<Path> localWebDirs = new ArrayList<>();
        if (config.localWebDir()) {
            final Path webDir = projectScanner.localProjectDirsByName().get(config.webRoot());
            if (webDir != null) {
                localWebDirs.add(webDir);
            }
        }

        watchedWebDirs.produce(new WatchedWebDirsBuildItem(localWebDirs, srcResourcesDirs, config.webRoot()));
        watchedFiles.produce(
                HotDeploymentWatchedFileBuildItem.builder().setLocationPredicate(p -> p.startsWith(config.webRoot()))
                        .setRestartNeeded(true).build());
        if (projectRoot.exists()) {
            final Path rootConfigDir = projectRoot.path().resolve("config/");
            if (Files.isDirectory(rootConfigDir)) {
                watchedDirs.produce(new WebBundlerWatchedDirBuildItem(rootConfigDir));
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debugf("Watching resources %s for changes", config.webRoot());
        }
    }

}
