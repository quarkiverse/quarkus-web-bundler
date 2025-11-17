package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;
import java.util.List;

import io.mvnpm.esbuild.model.WebDependency;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem.Dependency;
import io.quarkus.builder.item.SimpleBuildItem;

public final class InstalledWebDependenciesBuildItem extends SimpleBuildItem {

    private final Path nodeModulesDir;
    private final List<Dependency> list;

    public InstalledWebDependenciesBuildItem(Path nodeModulesDir, List<Dependency> list) {
        this.list = list;
        this.nodeModulesDir = nodeModulesDir;
    }

    public Path nodeModulesDir() {
        return nodeModulesDir;
    }

    public List<Dependency> list() {
        return list;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public List<WebDependency> toEsBuildWebDependencies() {
        return list().stream().map(Dependency::toEsBuildWebDependency).toList();
    }

}
