package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

public record BundleWebAsset(WebAsset webAsset, BundleType type) implements WebAsset {

    public enum BundleType {
        ENTRYPOINT("entry-point"), // index.js, index.ts, index.jsx, index.tsx
        MANUAL("available for import"), // Add this to the working directory but do not bundle it (the entrypoint may import it)
        AUTO("auto-imported") // Add this to the working directory and index it automatically as part of the bundle
        ;

        private String label;

        BundleType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
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
