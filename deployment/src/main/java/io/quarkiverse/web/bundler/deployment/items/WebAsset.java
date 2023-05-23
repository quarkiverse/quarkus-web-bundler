package io.quarkiverse.web.bundler.deployment.items;

import static io.quarkiverse.web.bundler.deployment.ProjectResourcesScanner.readTemplateContent;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

public interface WebAsset {
    default String pathFromWebRoot(String root) {
        if (!resourceName().startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return resourceName().substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

    default byte[] readContentFromFile() {
        return readTemplateContent(filePath().orElseThrow());
    }

    default boolean hasContent() {
        return this.content() != null;
    }

    default boolean matches(String glob) {
        if (!filePath().isPresent()) {
            return false;
        }
        return filePath().get().getFileSystem().getPathMatcher(glob).matches(filePath().get());
    }

    String resourceName();

    Optional<Path> filePath();

    byte[] content();

    Charset charset();
}
