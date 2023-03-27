package io.quarkiverse.web.assets.deployment.items;

import static java.util.Objects.requireNonNull;

import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

public class WebAssetsBuildItem extends SimpleBuildItem {

    private final List<WebAsset> webAssets;

    public WebAssetsBuildItem(List<WebAsset> webAssets) {
        this.webAssets = requireNonNull(webAssets, "webAssets is required");
    }

    public List<WebAsset> getWebAssets() {
        return webAssets;
    }

}
