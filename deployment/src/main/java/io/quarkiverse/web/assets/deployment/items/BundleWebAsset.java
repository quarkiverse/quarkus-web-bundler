package io.quarkiverse.web.assets.deployment.items;

import java.util.Objects;

public class BundleWebAsset extends WebAsset {

    public enum BundleType {
        ENTRYPOINT, // index.js, index.ts, index.jsx, index.tsx
        MANUAL, // Add this to the working directory but do not bundle it (the entrypoint may import it)
        AUTO // Add this to the working directory and index it automatically as part of the bundle
    }

    private final BundleType bundleType;

    public BundleWebAsset(WebAsset webAsset, BundleType bundleType) {
        super(webAsset.getResourceName(), webAsset.getFilePath(), webAsset.getContent(), webAsset.getCharset());
        this.bundleType = bundleType;
    }

    public BundleType type() {
        return bundleType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        BundleWebAsset that = (BundleWebAsset) o;
        return bundleType == that.bundleType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), bundleType);
    }
}
