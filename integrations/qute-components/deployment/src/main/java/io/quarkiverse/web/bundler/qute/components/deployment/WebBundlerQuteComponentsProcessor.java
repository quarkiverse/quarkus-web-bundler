package io.quarkiverse.web.bundler.qute.components.deployment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTagsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;

class WebBundlerQuteComponentsProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerQuteComponentsProcessor.class);

    @BuildStep
    QuteTagsBuildItem scan(ProjectResourcesScannerBuildItem scanner,
            WebBundlerConfig config,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer)
            throws IOException {

        LOGGER.debug("Web Bundler scan - Qute Web Components: start");
        List<ProjectResourcesScannerBuildItem.Scanner> quteTagsAssetsScanners = new ArrayList<>();
        for (Map.Entry<String, WebBundlerConfig.EntryPointConfig> entryPoint : config.bundleWithDefault(true).entrySet()
                .stream()
                .filter(e -> e.getValue().quteTags()).toList()) {
            final String dir = entryPoint.getValue().effectiveDir(entryPoint.getKey());
            quteTagsAssetsScanners.add(new ProjectResourcesScannerBuildItem.Scanner(dir,
                    "glob:**.html", config.getEffectiveIgnoredFiles(), config.charset()));
        }

        final List<WebAsset> assets = scanner
                .scan(quteTagsAssetsScanners);
        LOGGER.debugf(
                "Web Bundler scan - Qute Web Components: %d found.", assets.size());
        for (WebAsset asset : assets) {
            final String tag = Path.of(asset.webPath()).getFileName().toString();
            final String templateId = "tags/%s".formatted(tag);
            final String templateResource = "templates/" + templateId;
            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(
                            templateResource,
                            asset.content()));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(templateResource));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .fullPath(asset.path())
                    .content(new String(asset.content(), asset.charset()))
                    .path(templateId)
                    .extensionInfo("web-bundler")
                    .build());
        }
        return new QuteTagsBuildItem(assets);
    }

}
