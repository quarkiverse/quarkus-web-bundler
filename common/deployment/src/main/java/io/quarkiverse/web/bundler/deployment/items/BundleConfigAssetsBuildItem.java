package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

import io.quarkiverse.tools.projectscanner.ProjectFile;

/**
 * This contains config for bundling such as tsconfig.json
 */
public final class BundleConfigAssetsBuildItem extends WebAssetsBuildItem {

    public BundleConfigAssetsBuildItem(List<ProjectFile> webAssets) {
        super(webAssets);
    }
}
