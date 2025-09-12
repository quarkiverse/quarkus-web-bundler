package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allows extensions to define a list of extra resource folders to look up images
 * by absolute paths
 */
public final class ImageSourcePathBuildItem extends MultiBuildItem {

    public final Path path;

    public ImageSourcePathBuildItem(Path path) {
        this.path = path;
    }
}
