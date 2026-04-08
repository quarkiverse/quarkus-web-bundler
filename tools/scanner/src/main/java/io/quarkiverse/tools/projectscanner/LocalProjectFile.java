package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A project file on the local filesystem.
 * Covers LOCAL_PROJECT_FILE, ROOT_APPLICATION_RESOURCE, and local DEPENDENCY_RESOURCE origins.
 * Content is read via Files.readAllBytes.
 */
public final class LocalProjectFile extends AbstractProjectFile {

    private final Path source;
    private final String resourcePath;

    public LocalProjectFile(String indexPath, String scopedPath, Path file, Path source, Origin origin,
            String resourcePath, Charset charset) {
        super(indexPath, scopedPath, Objects.requireNonNull(file, "file must not be null for a local project file"),
                origin, charset);
        if (!ProjectFile.isLocalFileSystem(file)) {
            throw new IllegalArgumentException("file must be on a local filesystem: " + file);
        }
        if (source != null && !ProjectFile.isLocalFileSystem(source)) {
            throw new IllegalArgumentException("source must be on a local filesystem: " + source);
        }
        this.source = source;
        this.resourcePath = resourcePath;
    }

    @Override
    public Path source() {
        return source;
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
        LocalProjectFile that = (LocalProjectFile) o;
        return Objects.equals(source, that.source) && Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, resourcePath);
    }

    @Override
    public String toString() {
        return "%s{indexPath='%s', scopedPath='%s', file=%s, source=%s, origin=%s, resourcePath='%s'}".formatted(
                getClass().getSimpleName(), indexPath(), scopedPath(), file(), source, origin(), resourcePath);
    }
}