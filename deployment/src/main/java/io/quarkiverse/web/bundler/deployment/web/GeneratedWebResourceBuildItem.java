package io.quarkiverse.web.bundler.deployment.web;

import static io.quarkiverse.web.bundler.runtime.WebBundlerResourceHandler.META_INF_WEB;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.util.HashUtil;

public final class GeneratedWebResourceBuildItem extends MultiBuildItem {

    private final String publicPath;
    private final byte[] content;
    private final String contentHash;

    public GeneratedWebResourceBuildItem(String publicPath, byte[] content) {
        this.publicPath = publicPath;
        this.content = content;
        this.contentHash = HashUtil.sha512(content);
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
}
