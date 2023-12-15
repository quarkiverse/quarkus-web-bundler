package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem.readTemplateContent;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import io.quarkiverse.web.bundler.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;

public class StaticWebAssetsProcessor {

    @BuildStep
    void processStaticWebAssets(WebBundlerConfig config,
            StaticAssetsBuildItem staticAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.pathFromWebRoot(config.webRoot());
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(publicPath), liveReload, webAsset);
        }
    }

    static void makeWebAssetPublic(
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            String publicPath,
            LiveReloadBuildItem liveReload,
            WebAsset webAsset) {
        makeWebAssetPublic(staticResourceProducer, publicPath, webAsset,
                liveReload.isLiveReload() && liveReload.getChangedResources().contains(webAsset.resourceName()));
    }

    static void makeWebAssetPublic(
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            String publicPath,
            WebAsset webAsset,
            boolean changed) {
        handleStaticResource(
                staticResourceProducer,
                Set.of(new GeneratedStaticResourceBuildItem.Source(webAsset.resourceName(), webAsset.filePath())),
                publicPath,
                webAsset.contentOrReadFromFile(),
                changed,
                GeneratedStaticResourceBuildItem.WatchMode.RESTART);
    }

    static void makePublic(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, String publicPath,
            Path file, GeneratedStaticResourceBuildItem.WatchMode watchMode, boolean changed) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, Collections.emptySet(), publicPath, readTemplateContent(file), changed,
                watchMode);
    }

    private static void handleStaticResource(
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            Set<GeneratedStaticResourceBuildItem.Source> sources,
            String publicPath,
            byte[] content,
            boolean changed,
            GeneratedStaticResourceBuildItem.WatchMode watchMode) {
        staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                sources,
                publicPath,
                content,
                true,
                watchMode,
                changed));
    }

}
