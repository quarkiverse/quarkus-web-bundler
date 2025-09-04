package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Provides a mapping of template path to source path of the template
 */
public final class QuteTemplateSourcePathBuildItem extends MultiBuildItem {
    public final String templatePath;
    public final Path path;

    public QuteTemplateSourcePathBuildItem(String templatePath, Path path) {
        this.templatePath = templatePath;
        this.path = path;
    }
}
