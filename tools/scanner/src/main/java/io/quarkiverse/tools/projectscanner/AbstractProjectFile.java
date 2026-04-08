package io.quarkiverse.tools.projectscanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

public abstract class AbstractProjectFile implements ProjectFile {
    private final String indexPath;
    private final String scopedPath;
    private final Path file;
    private final Charset charset;
    private final LazyValue<byte[]> lazyContent;
    private final Origin origin;

    public AbstractProjectFile(String indexPath, String scopedPath, Path file, Origin origin, Charset charset) {
        this(indexPath, scopedPath, file, origin, new LazyValue<>(() -> {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }), charset);
    }

    public AbstractProjectFile(String indexPath, String scopedPath, Path file, Origin origin,
            LazyValue<byte[]> lazyContent,
            Charset charset) {
        this.indexPath = Objects.requireNonNull(indexPath, "indexPath must not be null");
        this.scopedPath = Objects.requireNonNull(scopedPath, "scopedPath must not be null");
        this.file = file;
        this.charset = Objects.requireNonNull(charset, "charset must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        this.lazyContent = Objects.requireNonNull(lazyContent, "lazyContent must not be null");
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
    public Path file() {
        return file;
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
        AbstractProjectFile that = (AbstractProjectFile) o;
        return Objects.equals(indexPath, that.indexPath) && Objects.equals(scopedPath, that.scopedPath)
                && Objects.equals(file, that.file)
                && Objects.equals(charset, that.charset) && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexPath, scopedPath, file, charset, origin);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AbstractProjectFile.class.getSimpleName() + "[", "]")
                .add("indexPath='" + indexPath + "'")
                .add("scopedPath='" + scopedPath + "'")
                .add("file=" + file)
                .add("charset=" + charset)
                .add("origin=" + origin)
                .toString();
    }
}
