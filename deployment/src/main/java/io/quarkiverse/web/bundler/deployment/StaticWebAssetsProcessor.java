package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.BundleWebAssetsScannerProcessor.SYMLINK_AVAILABLE;
import static io.quarkiverse.web.bundler.deployment.PrepareForBundlingProcessor.createSymbolicLinkOrFallback;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem.SourceType;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class StaticWebAssetsProcessor {

    @BuildStep
    void processPublicWebAssets(WebBundlerConfig config,
            WebBundlerTargetDirBuildItem targetDir,
            PublicAssetsBuildItem staticAssets,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            LaunchModeBuildItem launchMode,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer) {
        final boolean browserLiveReload = launchMode.getLaunchMode().equals(LaunchMode.DEVELOPMENT)
                && config.browserLiveReload();
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.webPath().replace("public/", "");
            final Path targetPath = targetDir.dist().resolve(publicPath);
            try {
                if (webAsset.type() != WebAsset.Type.RESOURCE) {
                    if (browserLiveReload && SYMLINK_AVAILABLE.get()) {
                        Files.createDirectories(targetPath.getParent());
                        createSymbolicLinkOrFallback(watchedFiles, webAsset, targetPath);
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
