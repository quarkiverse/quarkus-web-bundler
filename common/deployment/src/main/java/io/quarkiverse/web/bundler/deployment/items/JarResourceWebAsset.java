package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;

public record JarResourceWebAsset(String webPath, Path path, String resourcePath, byte[] content,
        Charset charset) implements WebAsset {
    @Override
    public String watchPath() {
        throw new IllegalStateException("watchPath should not be used for JarResourceWebAsset");
    }

    @Override
    public Type type() {
        return Type.JAR_RESOURCE;
    }
}
