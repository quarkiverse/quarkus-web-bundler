package io.quarkiverse.web.bundler.spi.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * This build item allows to add a directory to be watched by the Web Bundler fs watcher (for live-reload)
 * When a change is detected, it will trigger a new Quarkus scan.
 */
public final class WebBundlerWatchedDirBuildItem extends MultiBuildItem {
    private final Path path;

    public WebBundlerWatchedDirBuildItem(Path path) {
        this.path = path.normalize();
    }

    public Path path() {
        return path;
    }

}
