package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;

public interface WebAsset {

    Path path();

    String webPath();

    byte[] content();

    Charset charset();

    String watchPath();

    Type type();

    static boolean isLocalFileSystem(Path path) {
        try {
            return "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    enum Type {
        EXTERNAL, // File is in an external web directory
        PROJECT_SOURCE, // File is in the project root resources and src is detected
        PROJECT_RESOURCE, // File is in the project root resources and src is not detected
        JAR_RESOURCE // File is in a jar
        ;

        public boolean canLink() {
            return this == EXTERNAL || this == PROJECT_SOURCE;
        }
    }

}
