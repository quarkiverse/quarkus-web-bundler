package io.quarkiverse.web.bundler.deployment.web;

import static io.quarkiverse.web.bundler.runtime.WebBundlerResourceHandler.META_INF_WEB;

import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkus.builder.item.MultiBuildItem;

public final class GeneratedWebResourceBuildItem extends MultiBuildItem {

    public enum SourceType {
        BUILD_TIME_TEMPLATE("build-time template", 1),
        STATIC_ASSET("static asset", 2),
        BUNDLED_ASSET("bundled asset", 3),
        ;

        private String label;
        private final int order;

        SourceType(String label, int order) {
            this.label = label;
            this.order = order;
        }

        public String label() {
            return label;
        }

        public int order() {
            return order;
        }
    }

    private final String publicPath;
    private final WebAsset.Resource resource;
    private final SourceType type;

    public GeneratedWebResourceBuildItem(String publicPath, WebAsset.Resource resource, SourceType type) {
        this.publicPath = publicPath;
        this.resource = resource;
        this.type = type;
    }

    public String resourceName() {
        return META_INF_WEB + "/" + publicPath.replaceAll("^/", "");
    }

    public String publicPath() {
        return publicPath;
    }

    public WebAsset.Resource resource() {
        return resource;
    }

    public SourceType type() {
        return type;
    }
}
