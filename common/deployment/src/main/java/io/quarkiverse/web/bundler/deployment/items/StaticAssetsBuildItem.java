package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

import io.quarkiverse.tools.projectscanner.ProjectFile;

public final class StaticAssetsBuildItem extends WebAssetsBuildItem {

    public StaticAssetsBuildItem(List<ProjectFile> webAssets) {
        super(webAssets);
    }
}
