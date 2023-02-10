package io.quarkiverse.web.assets.deployment;

import java.nio.charset.Charset;
import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebAssetBuildItem extends MultiBuildItem {

    private final String bundleName;

    private final String path;
    private final Path fullPath;
    private final byte[] content;

    private final Charset charset;

    public WebAssetBuildItem(String bundleName, String path, Path fullPath, byte[] content, Charset charset) {
        this.bundleName = bundleName;
        this.path = path;
        this.fullPath = fullPath;
        this.content = content;
        this.charset = charset;
    }

    public String getBundleName() {
        return bundleName;
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

    public byte[] getContent() {
        return content;
    }

    public Charset getCharset() {
        return charset;
    }
}
