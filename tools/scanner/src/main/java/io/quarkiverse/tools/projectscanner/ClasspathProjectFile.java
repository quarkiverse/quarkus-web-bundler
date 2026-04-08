package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A project file backed by a non-local resource whose content is read via the classloader.
 * Covers classpath-readable resources, including JAR-based dependency resources and other
 * non-local application resources.
 */
public final class ClasspathProjectFile extends AbstractProjectFile {

    private final String resourcePath;

    public ClasspathProjectFile(String indexPath, String scopedPath, Path file, Origin origin, String resourcePath,
            Charset charset) {
        super(indexPath, scopedPath, file, origin,
                new LazyValue<>(() -> ProjectScanner.readRuntimeResourceContent(resourcePath)), charset);
        if (file != null && ProjectFile.isLocalFileSystem(file)) {
            throw new IllegalArgumentException("file must not be on a local filesystem for a classpath project file: " + file);
        }
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath must not be null");
    }

    public ClasspathProjectFile(String indexPath, String scopedPath, Path file, Origin origin, String resourcePath,
            byte[] content, Charset charset) {
        super(indexPath, scopedPath, file, origin, new LazyValue<>(() -> content), charset);
        if (file != null && ProjectFile.isLocalFileSystem(file)) {
            throw new IllegalArgumentException("file must not be on a local filesystem for a classpath project file: " + file);
        }
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath must not be null");
    }

    @Override
    public Path source() {
        return null;
    }

    public String resourcePath() {
        return resourcePath;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ClasspathProjectFile that = (ClasspathProjectFile) o;
        return Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourcePath);
    }
}