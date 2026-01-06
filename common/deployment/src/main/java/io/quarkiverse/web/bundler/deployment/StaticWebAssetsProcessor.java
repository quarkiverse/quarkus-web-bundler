package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.BundlePrepareProcessor.createSymbolicLinkOrFallback;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.DevWatchedLinkBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedWebResourceBuildItem.SourceType;
import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
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
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.webPath().replace("public/", "");
            final Path targetPath = targetDir.dist().resolve(publicPath);
            try {
                if (webAsset.type() != WebAsset.Type.JAR_RESOURCE) {
                    if (launchMode.getLaunchMode().isDev() && config.browserLiveReload()) {
                        Files.createDirectories(targetPath.getParent());
                        createSymbolicLinkOrFallback(watchedLinks, watchedFiles, webAsset, targetPath);
                        staticResourceProducer.produce(GeneratedWebResourceBuildItem.fromContent(
                                prefixWithSlash(publicPath),
                                webAsset.content(), SourceType.STATIC_ASSET));
                    } else {
                        // We can read the file
                        staticResourceProducer.produce(GeneratedWebResourceBuildItem.fromContent(
                                prefixWithSlash(publicPath),
                                webAsset.content(), SourceType.STATIC_ASSET));
                    }
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
