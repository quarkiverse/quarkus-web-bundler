package io.quarkiverse.web.bundler.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebBundlerWatchedDirBuildItem extends MultiBuildItem {
    private final Path path;
    private final boolean web;

    public WebBundlerWatchedDirBuildItem(Path path, boolean web) {
        this.path = path;
        this.web = web;
    }

    public Path path() {
        return path;
    }

    public boolean web() {
        return web;
    }
}
