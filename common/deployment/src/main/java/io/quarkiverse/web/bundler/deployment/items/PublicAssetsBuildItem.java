package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

import io.quarkiverse.tools.projectscanner.ProjectFile;

public final class PublicAssetsBuildItem extends WebAssetsBuildItem {

    public PublicAssetsBuildItem(List<ProjectFile> webAssets) {
        super(webAssets);
    }
}
