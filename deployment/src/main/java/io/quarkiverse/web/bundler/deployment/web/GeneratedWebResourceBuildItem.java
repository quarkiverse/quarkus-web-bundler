package io.quarkiverse.web.bundler.deployment.web;

import static io.quarkiverse.web.bundler.runtime.WebBundlerResourceHandler.META_INF_WEB;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.util.HashUtil;

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
    private final byte[] content;
    private final String contentHash;

    private final SourceType type;

    public GeneratedWebResourceBuildItem(String publicPath, byte[] content, SourceType type) {
        this.publicPath = publicPath;
        this.content = content;
        this.contentHash = HashUtil.sha512(content);
        this.type = type;
    }

    public String resourceName() {
        return META_INF_WEB + "/" + publicPath.replaceAll("^/", "");
    }

    public String publicPath() {
        return publicPath;
    }

    public byte[] content() {
        return content;
    }

    public String contentHash() {
        return contentHash;
    }

    public SourceType type() {
        return type;
    }
}
