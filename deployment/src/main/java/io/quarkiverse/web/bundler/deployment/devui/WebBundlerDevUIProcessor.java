package io.quarkiverse.web.bundler.deployment.devui;

import static io.quarkiverse.web.bundler.deployment.devui.WebDependencyLibrariesBuildItem.WebDependencyLibrary;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.web.bundler.deployment.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.*;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class WebBundlerDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void createPages(WebBundlerConfig config,
            BuildProducer<CardPageBuildItem> cardPageProducer,
            List<EntryPointBuildItem> entryPoints,
            WebDependenciesBuildItem webDependencies,
            StaticAssetsBuildItem staticAssets,
            QuteTemplatesBuildItem htmlAssets,
            List<WebDependencyLibrariesBuildItem> webDependencyLibrariesBuildItems) {

        List<WebDependencyLibrary> webDependencyLibraries = new ArrayList<>();
        for (WebDependencyLibrariesBuildItem webDependencyLibrariesBuildItem : webDependencyLibrariesBuildItems) {
            webDependencyLibraries.addAll(webDependencyLibrariesBuildItem.getWebDependencyLibraries());
        }

        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        if (!webDependencies.isEmpty()) {
            // Web Dependency Libraries
            cardPageBuildItem.addBuildTimeData("webDependencyLibraries", webDependencyLibraries);

            cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                    .componentLink("qwc-web-bundler-web-dependencies.js")
                    .title("Web Dependencies")
                    .icon("font-awesome-brands:square-js")
                    .staticLabel(String.valueOf(webDependencies.list().size())));

        }

        if (!entryPoints.isEmpty()) {
            cardPageBuildItem.addBuildTimeData("entryPoints",
                    entryPoints.stream().map(e -> new EntryPoint(e.getEntryPointKey(), e.getWebAssets().stream()
                            .map(a -> new EntryPointItem(a.webAsset().pathFromWebRoot(config.webRoot()), a.type().label(),
                                    new String(a.webAsset().contentOrReadFromFile())))
                            .toList()))
                            .toList());

            cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                    .componentLink("qwc-web-bundler-entry-points.js")
                    .title("Entry Points")
                    .icon("font-awesome-solid:folder-tree")
                    .staticLabel(String.valueOf(webDependencies.list().size())));

        }

        if (!htmlAssets.getWebAssets().isEmpty()) {
            cardPageBuildItem.addBuildTimeData("htmlAssets", htmlAssets.getWebAssets().stream()
                    .map(s -> new WebAsset(s.pathFromWebRoot(config.webRoot()))).toList());
            cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                    .componentLink("qwc-web-bundler-html-templates.js")
                    .title("Html templates")
                    .icon("font-awesome-brands:html5")
                    .staticLabel(String.valueOf(htmlAssets.getWebAssets().size())));
        }

        if (!staticAssets.getWebAssets().isEmpty()) {
            cardPageBuildItem.addBuildTimeData("staticAssets", staticAssets.getWebAssets().stream()
                    .map(s -> new WebAsset(s.pathFromWebRoot(config.webRoot()))).toList());
            cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                    .componentLink("qwc-web-bundler-static-assets.js")
                    .title("Static Assets")
                    .icon("font-awesome-solid:image")
                    .staticLabel(String.valueOf(staticAssets.getWebAssets().size())));
        }

        cardPageProducer.produce(cardPageBuildItem);
    }

    record WebAsset(String path) {
    }

    record EntryPoint(String key, List<EntryPointItem> items) {
    }

    record EntryPointItem(String path, String type, String content) {
    }

}
