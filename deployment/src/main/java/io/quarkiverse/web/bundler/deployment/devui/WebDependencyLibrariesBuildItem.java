package io.quarkiverse.web.bundler.deployment.devui;

import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebDependencyLibrariesBuildItem extends MultiBuildItem {
    private final String provider;
    private final List<WebDependencyLibrary> webDependencyLibraries;

    public WebDependencyLibrariesBuildItem(String provider, List<WebDependencyLibrary> webDependencyLibraries) {
        this.provider = provider;
        this.webDependencyLibraries = webDependencyLibraries;
    }

    public List<WebDependencyLibrary> getWebDependencyLibraries() {
        return this.webDependencyLibraries;
    }

    public String getProvider() {
        return this.provider;
    }

    record WebDependencyAsset(String name,
            List<WebDependencyAsset> children,
            boolean fileAsset,
            String urlPart) {
    }

    record WebDependencyLibrary(String webDependencyName,
            String version,
            WebDependencyAsset rootAsset) {
    }
}
