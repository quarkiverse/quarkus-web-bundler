package io.quarkiverse.web.bundler.qute.components.deployment;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.QuteTagsBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;

class WebBundlerQuteComponentsProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerQuteComponentsProcessor.class);

    @BuildStep
    QuteTagsBuildItem scan(ProjectScannerBuildItem scanner,
            WebBundlerConfig config,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer)
            throws IOException {

        LOGGER.debug("Web Bundler scan - Qute Web Components: start");
        List<String> dirs = config.bundleWithDefault(true).entrySet().stream()
                .filter(e -> e.getValue().quteTags())
                .map(e -> config.prefixWithWebRoot(e.getValue().effectiveDir(e.getKey())))
                .toList();
        if (dirs.isEmpty()) {
            return new QuteTagsBuildItem(List.of());
        }
        final List<ProjectFile> assets = scanner.query()
                .scopeDirs(dirs)
                .matchingGlob("**.html")
                .addExcluded(config.ignoredFilesOrEmpty())
                .list();
        LOGGER.debugf(
                "Web Bundler scan - Qute Web Components: %d found.", assets.size());
        for (ProjectFile asset : assets) {
            final String tag = Path.of(asset.scopedPath()).getFileName().toString();
            final String templateId = "tags/%s".formatted(tag);
            final String templateResource = "templates/" + templateId;
            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(
                            templateResource,
                            asset.content()));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(templateResource));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .fullPath(requireNonNull(asset.file(),
                            "Qute component template must have a local file: " + asset.scopedPath()))
                    .content(new String(asset.content(), asset.charset()))
                    .path(templateId)
                    .extensionInfo("web-bundler")
                    .build());
        }
        return new QuteTagsBuildItem(assets);
    }

}
