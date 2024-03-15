package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem.readTemplateContent;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.web.bundler.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class StaticWebAssetsProcessor {

    @BuildStep
    void processStaticWebAssets(WebBundlerConfig config,
            StaticAssetsBuildItem staticAssets,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.pathFromWebRoot(config.webRoot());
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(publicPath), webAsset);
        }
    }

    static void makeWebAssetPublic(
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            String publicPath,
            WebAsset webAsset) {
        handleStaticResource(
                staticResourceProducer,
                publicPath,
                webAsset.contentOrReadFromFile());
    }

    static void makePublic(BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer, String publicPath,
            Path file) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, publicPath, readTemplateContent(file));
    }

    private static void handleStaticResource(
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            String publicPath,
            byte[] content) {
        staticResourceProducer.produce(new GeneratedWebResourceBuildItem(
                publicPath,
                content));
    }

}
