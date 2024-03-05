package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;
import java.util.List;

import io.mvnpm.esbuild.model.WebDependency;
import io.quarkus.builder.item.SimpleBuildItem;

public final class WebDependenciesBuildItem extends SimpleBuildItem {

    private final List<Dependency> list;

    public WebDependenciesBuildItem(List<Dependency> list) {
        this.list = list;
    }

    public List<Dependency> list() {
        return list;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public record Dependency(String id, Path path, WebDependency.WebDependencyType type, boolean direct) {

        public WebDependency toEsBuildWebDependency() {
            return WebDependency.of(id, path, type);
        }
    }

}
