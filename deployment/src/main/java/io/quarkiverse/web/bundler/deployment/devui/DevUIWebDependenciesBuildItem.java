package io.quarkiverse.web.bundler.deployment.devui;

import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

public final class DevUIWebDependenciesBuildItem extends SimpleBuildItem {
    private final List<DevUIWebDependency> webDependencies;

    public DevUIWebDependenciesBuildItem(List<DevUIWebDependency> webDependencies) {

        this.webDependencies = webDependencies;
    }

    public List<DevUIWebDependency> getWebDependencies() {
        return this.webDependencies;
    }

    record WebDependencyAsset(String name,
            List<WebDependencyAsset> children,
            boolean fileAsset,
            String urlPart) {
    }

    public record DevUIWebDependency(String type,
            String webDependencyName,
            String version,
            WebDependencyAsset rootAsset) {
    }
}
