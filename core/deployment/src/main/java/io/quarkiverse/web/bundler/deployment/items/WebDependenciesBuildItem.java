package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;
import java.util.List;

import io.mvnpm.esbuild.model.WebDependency;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.maven.dependency.ResolvedDependency;

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

    public List<WebDependency> toEsBuildWebDependencies() {
        return list().stream().map(Dependency::toEsBuildWebDependency).toList();
    }

    public record Dependency(ResolvedDependency resolvedDependency, String id, Path path, WebDependency.WebDependencyType type,
            boolean direct) {

        public WebDependency toEsBuildWebDependency() {
            return WebDependency.of(id, path, type);
        }
    }

}
