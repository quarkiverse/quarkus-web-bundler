package io.quarkiverse.tools.projectscanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

public abstract class LazyContentProjectFile implements ProjectFile {
    private final String indexPath;
    private final String scopedPath;
    private final Path path;
    private final Charset charset;
    private final LazyValue<byte[]> lazyContent;
    private final Origin origin;

    public LazyContentProjectFile(String indexPath, String scopedPath, Path path, Origin origin, Charset charset) {
        this(indexPath, scopedPath, path, origin, new LazyValue<>(() -> {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }), charset);
    }

    public LazyContentProjectFile(String indexPath, String scopedPath, Path path, Origin origin,
            LazyValue<byte[]> lazyContent,
            Charset charset) {
        this.indexPath = indexPath;
        this.scopedPath = scopedPath;
        this.path = path;
        this.charset = charset;
        this.origin = origin;
        this.lazyContent = lazyContent;
    }

    @Override
    public Origin origin() {
        return origin;
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
    public String indexPath() {
        return indexPath;
    }

    @Override
    public String scopedPath() {
        return scopedPath;
    }

    @Override
    public Charset charset() {
        return charset;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        LazyContentProjectFile that = (LazyContentProjectFile) o;
        return Objects.equals(indexPath, that.indexPath) && Objects.equals(scopedPath, that.scopedPath)
                && Objects.equals(path, that.path)
                && Objects.equals(charset, that.charset) && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexPath, scopedPath, path, charset, origin);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", LazyContentProjectFile.class.getSimpleName() + "[", "]")
                .add("indexPath='" + indexPath + "'")
                .add("scopedPath='" + scopedPath + "'")
                .add("path=" + path)
                .add("charset=" + charset)
                .add("origin=" + origin)
                .toString();
    }
}
