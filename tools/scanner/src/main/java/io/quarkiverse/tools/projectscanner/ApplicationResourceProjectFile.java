package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public final class ApplicationResourceProjectFile extends LazyContentProjectFile {

    private final String resourcePath;
    private final boolean isSrcFile;

    public ApplicationResourceProjectFile(String indexPath, String scopedPath, Path path, String resourcePath,
            boolean isSrcFile,
            Charset charset) {
        super(indexPath, scopedPath, path, Origin.APPLICATION_RESOURCE, charset);
        this.resourcePath = resourcePath;
        // the path is to the src file in case of linkable resources
        this.isSrcFile = isSrcFile;
    }

    @Override
    public boolean isSrcFile() {
        return isSrcFile;
    }

    public String resourcePath() {
        return resourcePath;
    }

    @Override
    public String watchPath() {
        // Quarkus HotDeploymentWatchedFileBuildItem needs the relative resource path in the case of resources
        return resourcePath;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ApplicationResourceProjectFile that = (ApplicationResourceProjectFile) o;
        return Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourcePath);
    }

    @Override
    public String toString() {
        return "%s{indexPath='%s', scopedPath='%s', path=%s, resourcePath='%s', isSrcFile=%s}".formatted(
                getClass().getSimpleName(), indexPath(), scopedPath(), path(), resourcePath, isSrcFile);
    }
}
