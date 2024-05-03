package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem.readTemplateContent;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.web.bundler.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem.SourceType;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class StaticWebAssetsProcessor {

    @BuildStep
    void processStaticWebAssets(WebBundlerConfig config,
            StaticAssetsBuildItem staticAssets,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.pathFromWebRoot(config.webRoot());
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(publicPath), webAsset, SourceType.STATIC_ASSET);
        }
    }

    static void makeWebAssetPublic(
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            String publicPath,
            WebAsset webAsset,
            SourceType sourceType) {
        handleStaticResource(
                staticResourceProducer,
                publicPath,
                webAsset.contentOrReadFromFile(),
                sourceType);
    }

    static void makePublic(BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer, String publicPath,
            Path file, SourceType sourceType) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, publicPath, readTemplateContent(file), sourceType);
    }

    private static void handleStaticResource(
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            String publicPath,
            byte[] content,
            SourceType sourceType) {
        staticResourceProducer.produce(new GeneratedWebResourceBuildItem(
                publicPath,
                content, sourceType));
    }

}
