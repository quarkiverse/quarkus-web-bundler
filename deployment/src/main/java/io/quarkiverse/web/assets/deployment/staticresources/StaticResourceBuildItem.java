package io.quarkiverse.web.assets.deployment.staticresources;

import static io.quarkus.vertx.http.runtime.StaticResourcesRecorder.META_INF_RESOURCES;

import java.nio.file.Path;
import java.util.Set;

import io.quarkus.builder.item.MultiBuildItem;

public final class StaticResourceBuildItem extends MultiBuildItem {

    private final Set<Source> origins;

    private final String publicPath;
    private final byte[] content;
    private final boolean nativeEnabled;
    private final boolean watchEnabled;

    public StaticResourceBuildItem(Set<Source> origins, String publicPath, byte[] content,
            boolean nativeEnabled, boolean watchEnabled) {
        this.origins = origins;
        this.publicPath = publicPath;
        this.content = content;
        this.nativeEnabled = nativeEnabled;
        this.watchEnabled = watchEnabled;
    }

    public Set<Source> getOrigins() {
        return origins;
    }

    public String getResourceName() {
        return META_INF_RESOURCES + "/" + publicPath;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public byte[] getContent() {
        return content;
    }

    public boolean isNativeEnabled() {
        return nativeEnabled;
    }

    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    public static class Source {
        private final String resourceName;
        private final Path path;

        public Source(String resourceName, Path path) {
            this.resourceName = resourceName;
            this.path = path;
        }

        public String getResourceName() {
            return resourceName;
        }

        public Path getPath() {
            return path;
        }
    }
}
