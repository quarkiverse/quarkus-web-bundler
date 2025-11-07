package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebDirBuildItem extends MultiBuildItem {
    private final Path path;

    public WebDirBuildItem(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

}
