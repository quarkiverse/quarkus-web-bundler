package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;

import io.mvnpm.esbuild.model.WebDependency;
import io.quarkus.builder.item.SimpleBuildItem;

public final class WebDependenciesBuildItem extends SimpleBuildItem {

    private final List<WebDependency> dependencies;

    public WebDependenciesBuildItem(List<WebDependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<WebDependency> getDependencies() {
        return dependencies;
    }

}
