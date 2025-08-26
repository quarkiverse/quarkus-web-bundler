package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;

public interface WebAsset {

    Path path();

    String webPath();

    byte[] content();

    Charset charset();

    Type type();

    static boolean isLocalFileSystem(Path path) {
        try {
            return "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    enum Type {
        SOURCE_FILE,
        FILE,
        RESOURCE
    }

}
