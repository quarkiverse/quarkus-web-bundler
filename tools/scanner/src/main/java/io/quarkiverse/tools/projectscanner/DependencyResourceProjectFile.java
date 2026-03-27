package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public final class DependencyResourceProjectFile extends LazyContentProjectFile {

    private final String resourcePath;

    public DependencyResourceProjectFile(String indexPath, String scopedPath, Path path, String resourcePath,
            Charset charset) {
        super(indexPath, scopedPath, path, Origin.DEPENDENCY_RESOURCE,
                new LazyValue<>(() -> ProjectScanner.readRuntimeResourceContent(resourcePath)), charset);
        this.resourcePath = resourcePath;
    }

    public DependencyResourceProjectFile(String indexPath, String scopedPath, Path path, String resourcePath,
            byte[] content, Charset charset) {
        super(indexPath, scopedPath, path, Origin.DEPENDENCY_RESOURCE, new LazyValue<>(() -> content), charset);
        this.resourcePath = resourcePath;
    }

    public String resourcePath() {
        return resourcePath;
    }

    @Override
    public String watchPath() {
        // watchPath not available as they are not source files
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        DependencyResourceProjectFile that = (DependencyResourceProjectFile) o;
        return Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourcePath);
    }
}
