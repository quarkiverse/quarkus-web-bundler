package io.quarkiverse.web.bundler.deployment.items;

import static java.util.Objects.requireNonNull;

import java.util.List;

import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkus.builder.item.SimpleBuildItem;

public abstract class WebAssetsBuildItem extends SimpleBuildItem {

    private final List<ProjectFile> webAssets;

    public WebAssetsBuildItem(List<ProjectFile> webAssets) {
        this.webAssets = requireNonNull(webAssets, "webAssets is required");
    }

    public List<ProjectFile> getWebAssets() {
        return webAssets;
    }

}
