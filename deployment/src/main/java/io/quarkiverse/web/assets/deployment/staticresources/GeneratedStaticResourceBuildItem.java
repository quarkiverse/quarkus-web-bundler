package io.quarkiverse.web.assets.deployment.staticresources;

import static io.quarkus.vertx.http.runtime.StaticResourcesRecorder.META_INF_RESOURCES;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import io.quarkus.builder.item.MultiBuildItem;

public final class GeneratedStaticResourceBuildItem extends MultiBuildItem {

    public enum WatchMode {
        DISABLED,
        RUNTIME,
        RESTART
    }

    private final Set<Source> origins;

    private final String publicPath;
    private final byte[] content;
    private final boolean nativeEnabled;
    private final WatchMode watchMode;

    private final boolean changed;

    public GeneratedStaticResourceBuildItem(Set<Source> origins, String publicPath, byte[] content,
            boolean nativeEnabled, WatchMode watchMode, boolean changed) {
        this.origins = origins;
        this.publicPath = publicPath;
        this.content = content;
        this.nativeEnabled = nativeEnabled;
        this.watchMode = watchMode;
        this.changed = changed;
    }

    public boolean isChanged() {
        return changed;
    }

    public Set<Source> getOrigins() {
        return origins;
    }

    public String getResourceName() {
        return META_INF_RESOURCES + "/" + publicPath.replaceAll("^/", "");
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

    public WatchMode getWatchMode() {
        return watchMode;
    }

    public static class Source {
        private final String resourceName;
        private final Optional<Path> path;

        public Source(String resourceName, Optional<Path> path) {
            this.resourceName = resourceName;
            this.path = path;
        }

        public String getResourceName() {
            return resourceName;
        }

        public Optional<Path> getPath() {
            return path;
        }
    }
}
