package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Provides a mapping of template path to source path of the template (collected paths)
 */
public final class QuteTemplateSourcePathsBuildItem extends SimpleBuildItem {
    public final Map<String, Path> templatePathsToSourcePaths = new HashMap<>();

    public QuteTemplateSourcePathsBuildItem(List<QuteTemplateSourcePathBuildItem> paths) {
        for (QuteTemplateSourcePathBuildItem path : paths) {
            templatePathsToSourcePaths.put(path.templatePath, path.path);
        }
    }
}
