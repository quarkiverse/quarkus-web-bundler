package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public class ResponsiveImageBuildItem extends SimpleBuildItem {
    public final Path path;
    public final String image;

    public ResponsiveImageBuildItem(String image, Path path) {
        this.image = image;
        this.path = path;
    }
}
