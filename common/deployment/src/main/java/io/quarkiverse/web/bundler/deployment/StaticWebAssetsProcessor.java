package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.prefixWithSlash;
import static io.quarkiverse.web.bundler.deployment.BundlePrepareProcessor.createLinkOrCopy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.DevWatchedLinkBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedWebResourceBuildItem.SourceType;
import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;

public class StaticWebAssetsProcessor {

    @BuildStep
    void processPublicWebAssets(
            LaunchModeBuildItem launchMode,
            WebBundlerConfig config,
            WebBundlerTargetDirBuildItem targetDir,
            PublicAssetsBuildItem staticAssets,
            BuildProducer<DevWatchedLinkBuildItem> watchedLinks,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer) {
        for (ProjectFile webAsset : staticAssets.getWebAssets()) {
            final String publicPath = config.stripWebRootPrefix(webAsset.indexPath()).replace("public/", "");
            final Path targetPath = targetDir.dist().resolve(publicPath);
            try {
                if (launchMode.getLaunchMode().isDev()) {
                    Files.createDirectories(targetPath.getParent());
                    createLinkOrCopy(config.browserLiveReload(), watchedLinks, watchedFiles, webAsset, targetPath);
                    staticResourceProducer.produce(GeneratedWebResourceBuildItem.fromFile(
                            prefixWithSlash(publicPath),
                            targetPath, SourceType.STATIC_ASSET));
                } else {
                    staticResourceProducer.produce(GeneratedWebResourceBuildItem.fromContent(
                            prefixWithSlash(publicPath),
                            webAsset.content(), SourceType.STATIC_ASSET));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
