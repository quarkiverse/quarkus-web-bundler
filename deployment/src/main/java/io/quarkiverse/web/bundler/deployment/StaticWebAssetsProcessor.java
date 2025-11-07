package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.PrepareForBundlingProcessor.createSymbolicLinkOrFallback;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.web.bundler.deployment.items.DevWatcherBuildItem;
import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem.SourceType;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public class StaticWebAssetsProcessor {

    @BuildStep
    void processPublicWebAssets(WebBundlerConfig config,
            DevWatcherBuildItem watcher,
            WebBundlerTargetDirBuildItem targetDir,
            PublicAssetsBuildItem staticAssets,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.webPath().replace("public/", "");
            final Path targetPath = targetDir.dist().resolve(publicPath);
            try {
                if (webAsset.type() != WebAsset.Type.JAR_RESOURCE) {
                    if (watcher != null) {
                        Files.createDirectories(targetPath.getParent());
                        createSymbolicLinkOrFallback(watcher, watchedFiles, webAsset, targetPath);
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
