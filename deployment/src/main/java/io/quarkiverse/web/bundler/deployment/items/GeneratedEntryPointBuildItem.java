package io.quarkiverse.web.bundler.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class GeneratedEntryPointBuildItem extends MultiBuildItem {

    private final String key;
    private final BundleWebAsset webAsset;

    public GeneratedEntryPointBuildItem(String key, BundleWebAsset webAsset) {
        this.key = key;
        this.webAsset = webAsset;
    }

    public String key() {
        return key;
    }

    public BundleWebAsset webAsset() {
        return webAsset;
    }
}
