package io.quarkiverse.web.bundler.deployment.items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.quarkus.arc.impl.LazyValue;

public final class FileWebAsset implements WebAsset {
    private final String webPath;
    private final Path path;
    private final Charset charset;
    private final Type type;
    private final LazyValue<byte[]> lazyContent;

    public FileWebAsset(String webPath, Path path, Type type,
            Charset charset) {
        this.webPath = webPath;
        this.path = path;
        this.type = type;
        this.charset = charset;
        lazyContent = new LazyValue<>(() -> {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public byte[] content() {
        return lazyContent.get();
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public String webPath() {
        return webPath;
    }

    @Override
    public Charset charset() {
        return charset;
    }

    @Override
    public boolean equals(Object o) {

        if (o == null || getClass() != o.getClass())
            return false;
        FileWebAsset that = (FileWebAsset) o;
        return Objects.equals(webPath, that.webPath) && Objects.equals(path, that.path)
                && Objects.equals(charset, that.charset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webPath, path, charset);
    }
}
