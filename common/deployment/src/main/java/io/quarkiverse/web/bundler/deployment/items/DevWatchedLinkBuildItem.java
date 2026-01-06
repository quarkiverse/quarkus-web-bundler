package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Web Bundler is using links (symbolic or copy if not possible) of the source file.
 * When a source file change is detected, we need to copy the file to the target when it is not a symbolic link.
 */
public final class DevWatchedLinkBuildItem extends MultiBuildItem {
    private final Path source;
    private final Path target;
    private final boolean symbolic;

    public DevWatchedLinkBuildItem(Path source, Path target, boolean symbolic) {
        this.source = source;
        this.target = target;
        this.symbolic = symbolic;
    }

    public Path source() {
        return source;
    }

    public Path target() {
        return target;
    }

    public boolean symbolic() {
        return symbolic;
    }
}
