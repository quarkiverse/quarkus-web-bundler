package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

/**
 * This contains config for bundling such as tsconfig.json
 */
public final class BundleConfigAssetsBuildItem extends WebAssetsBuildItem {

    public BundleConfigAssetsBuildItem(List<WebAsset> webAssets) {
        super(webAssets);
    }
}
