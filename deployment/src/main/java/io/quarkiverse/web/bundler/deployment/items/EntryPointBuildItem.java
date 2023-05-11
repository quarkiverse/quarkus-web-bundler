package io.quarkiverse.web.bundler.deployment.items;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

public final class EntryPointBuildItem extends MultiBuildItem {

    private final String entryPointKey;

    private final List<BundleWebAsset> webAssets;

    public EntryPointBuildItem(String entryPointKey, List<BundleWebAsset> webAssets) {
        this.entryPointKey = requireNonNull(entryPointKey, "key is required");
        this.webAssets = requireNonNull(webAssets, "webAssets is required");
    }

    public String getEntryPointKey() {
        return entryPointKey;
    }

    public List<BundleWebAsset> getWebAssets() {
        return webAssets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EntryPointBuildItem that = (EntryPointBuildItem) o;
        return Objects.equals(entryPointKey, that.entryPointKey) && Objects.equals(
                webAssets, that.webAssets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryPointKey, webAssets);
    }
}
