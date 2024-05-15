package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

public record DefaultWebAsset(String resourceName, Optional<Path> filePath, Optional<Path> srcFilePath, byte[] content,
        Charset charset) implements WebAsset {

    public DefaultWebAsset(String resourceName, Path filePath, Charset charset) {
        this(resourceName, Optional.of(filePath), Optional.empty(), null, charset);
    }

}
