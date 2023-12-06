package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

public record BundleWebAsset(WebAsset webAsset, BundleType type) implements WebAsset {

    public enum BundleType {
        ENTRYPOINT, // index.js, index.ts, index.jsx, index.tsx
        MANUAL, // Add this to the working directory but do not bundle it (the entrypoint may import it)
        AUTO // Add this to the working directory and index it automatically as part of the bundle
    }

    @Override
    public String resourceName() {
        return webAsset.resourceName();
    }

    @Override
    public Optional<Path> filePath() {
        return webAsset.filePath();
    }

    @Override
    public byte[] content() {
        return webAsset.content();
    }

    @Override
    public Charset charset() {
        return webAsset.charset();
    }
}
