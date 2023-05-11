package io.quarkiverse.web.assets.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.quarkiverse.web.assets.sass.SassBuildTimeCompiler;

public final class ScssConverter {

    public static void convertToScss(Path file, Path root) {
        String content = SassBuildTimeCompiler.convertScss(file, root, (s, s2) -> {
        });
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
