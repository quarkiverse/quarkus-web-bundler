package io.quarkiverse.web.bundler.deployment.items;

import io.quarkiverse.web.bundler.common.runtime.Responsive;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item to pass the Responsive instance from build-time templates to run-time template analysis.
 */
public final class ResponsiveBuildItem extends SimpleBuildItem {
    public final Responsive responsive;

    public ResponsiveBuildItem(Responsive responsive) {
        this.responsive = responsive;
    }
}
