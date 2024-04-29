package io.quarkiverse.web.bundler.deployment.devui;

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
            QuteTemplatesBuildItem htmlAssets) {

        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        if (!webDependencies.isEmpty()) {
            cardPageBuildItem.addBuildTimeData("webDependencies", webDependencies.list());
            cardPageBuildItem.addPage(Page.tableDataPageBuilder("Web Dependencies")
                    .icon("font-awesome-solid:table")
                    .showColumn("id")
                    .showColumn("type")
                    .showColumn("direct")
                    .staticLabel(String.valueOf(webDependencies.list().size()))
                    .buildTimeDataKey("webDependencies"));

        }

        if (!entryPoints.isEmpty()) {
            cardPageBuildItem.addBuildTimeData("entryPoints",
                    entryPoints.stream().map(e -> new EntryPoint(e.getEntryPointKey(), e.getWebAssets().stream()
                            .map(a -> new EntryPointItem(a.webAsset().pathFromWebRoot(config.webRoot()), a.type().label()))
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
            cardPageBuildItem.addPage(Page.tableDataPageBuilder("Html templates")
                    .icon("font-awesome-solid:table")
                    .showColumn("path")
                    .staticLabel(String.valueOf(htmlAssets.getWebAssets().size()))
                    .buildTimeDataKey("htmlAssets"));

        }

        if (!staticAssets.getWebAssets().isEmpty()) {
            cardPageBuildItem.addBuildTimeData("staticAssets", staticAssets.getWebAssets().stream()
                    .map(s -> new WebAsset(s.pathFromWebRoot(config.webRoot()))).toList());
            cardPageBuildItem.addPage(Page.tableDataPageBuilder("Static Assets")
                    .icon("font-awesome-solid:table")
                    .showColumn("path")
                    .staticLabel(String.valueOf(staticAssets.getWebAssets().size()))
                    .buildTimeDataKey("staticAssets"));

        }

        cardPageProducer.produce(cardPageBuildItem);
    }

    record WebAsset(String path) {
    }

    record EntryPoint(String key, List<EntryPointItem> items) {
    }

    record EntryPointItem(String path, String type) {
    }

}
