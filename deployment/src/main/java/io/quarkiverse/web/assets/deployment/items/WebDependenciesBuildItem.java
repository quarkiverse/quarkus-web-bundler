package io.quarkiverse.web.assets.deployment.items;

import java.nio.file.Path;
import java.util.List;

import io.quarkiverse.web.assets.deployment.WebBundlerConfig.WebDependencyType;
import io.quarkus.builder.item.SimpleBuildItem;

public final class WebDependenciesBuildItem extends SimpleBuildItem {

    private final List<Path> dependencies;

    private final WebDependencyType type;

    public WebDependenciesBuildItem(WebDependencyType type, List<Path> dependencies) {
        this.dependencies = dependencies;
        this.type = type;
    }

    public List<Path> getDependencies() {
        return dependencies;
    }

    public WebDependencyType getType() {
        return type;
    }
}
