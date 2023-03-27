package io.quarkiverse.web.assets.deployment.items;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

public final class BundleBuildItem extends MultiBuildItem {

    private final String key;

    private final List<WebAsset> webAssets;

    public BundleBuildItem(String key, List<WebAsset> webAssets) {
        this.key = requireNonNull(key, "key is required");
        this.webAssets = requireNonNull(webAssets, "webAssets is required");
    }

    public String getKey() {
        return key;
    }

    public List<WebAsset> getWebAssets() {
        return webAssets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BundleBuildItem that = (BundleBuildItem) o;
        return Objects.equals(key, that.key) && Objects.equals(webAssets, that.webAssets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, webAssets);
    }
}
