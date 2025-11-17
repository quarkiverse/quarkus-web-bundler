package io.quarkiverse.web.bundler.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebBundlerWatchedDirBuildItem extends MultiBuildItem {
    private final Path path;

    public WebBundlerWatchedDirBuildItem(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

}
