package io.quarkiverse.web.bundler.deployment.items;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public interface WebAsset {
    default String pathFromWebRoot(String root) {
        if (!resourceName().startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return resourceName().substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

    default boolean isFile() {
        return this.resource().isFile();
    }

    String resourceName();

    Optional<Path> srcFilePath();

    Resource resource();

    Charset charset();

    record Resource(byte[] content, Path path) {

        public Resource(byte[] content) {
            this(content, null);
        }

        public Resource(Path path) {
            this(null, path);
        }

        public Resource(byte[] content, Path path) {
            if (content != null && path != null) {
                throw new IllegalArgumentException(
                        "if a resource has content, it means the Path should be null has it is only meant for content which can't be read anymore");
            }
            this.content = content;
            this.path = path;
        }

        public byte[] contentOrReadFromFile() {
            try {
                return isFile() ? Files.readAllBytes(path()) : content();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isFile() {
            return path != null;
        }
    }
}
