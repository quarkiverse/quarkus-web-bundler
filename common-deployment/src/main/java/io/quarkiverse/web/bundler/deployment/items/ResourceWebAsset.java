package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;

public record ResourceWebAsset(String webPath, Path path, String resourcePath, byte[] content,
        Charset charset) implements WebAsset {
    @Override
    public Type type() {
        return Type.RESOURCE;
    }
}
