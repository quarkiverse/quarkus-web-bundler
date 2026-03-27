package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

import io.quarkiverse.tools.projectscanner.ProjectFile;

public final class StylesAssetsBuildItem extends WebAssetsBuildItem {

    public StylesAssetsBuildItem(List<ProjectFile> webAssets) {
        super(webAssets);
    }
}
