package io.quarkiverse.web.assets.deployment;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebAssetPathBuildItem extends MultiBuildItem {

    private final String path;
    private final Path fullPath;
    private final String content;

    public WebAssetPathBuildItem(String path, Path fullPath, String content) {
        this.path = path;
        this.fullPath = fullPath;
        this.content = content;
    }

    /**
     * Uses the {@code /} path separator.
     *
     * @return the path relative to the asset root
     */
    public String getPath() {
        return path;
    }

    /**
     * Uses the system-dependent path separator.
     *
     * @return the full path of the asset
     */
    public Path getFullPath() {
        return fullPath;
    }

    public String getContent() {
        return content;
    }

}
