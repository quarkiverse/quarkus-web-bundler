package io.quarkiverse.web.bundler.deployment.items;

import io.quarkiverse.web.bundler.common.runtime.Images;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item to pass the Images instance from build-time templates to run-time template analysis.
 */
public final class ImagesBuildItem extends SimpleBuildItem {
    public final Images images;

    public ImagesBuildItem(Images images) {
        this.images = images;
    }
}
