package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectResourceWebAsset extends LocalFileWebAsset {

    private final String resourcePath;

    public ProjectResourceWebAsset(String webPath, Path path, String resourcePath, Type type, Charset charset) {
        super(webPath, path, type, charset);
        this.resourcePath = resourcePath;
    }

    public String resourcePath() {
        return resourcePath;
    }

    @Override
    public String watchPath() {
        return resourcePath;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ProjectResourceWebAsset that = (ProjectResourceWebAsset) o;
        return Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourcePath);
    }
}
