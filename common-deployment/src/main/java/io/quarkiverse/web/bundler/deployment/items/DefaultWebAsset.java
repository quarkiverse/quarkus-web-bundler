package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

public record DefaultWebAsset(String resourceName, Resource resource, Optional<Path> srcFilePath,
        Charset charset) implements WebAsset {

    public DefaultWebAsset(String resourceName, Path filePath, Charset charset) {
        this(resourceName, new Resource(filePath), null, charset);
    }

}
