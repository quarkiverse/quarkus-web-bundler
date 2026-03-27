package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

import io.quarkiverse.tools.projectscanner.ProjectFile;

public final class QuteTemplatesBuildItem extends WebAssetsBuildItem {

    public QuteTemplatesBuildItem(List<ProjectFile> webAssets) {
        super(webAssets);
    }
}
