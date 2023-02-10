package io.quarkiverse.web.assets.deployment;

import static io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType.SCRIPT;
import static io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType.STATIC;
import static io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType.STYLE;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType;

public enum AssetsBundles implements WebAssetsConfig.BundleConfig {
    SCRIPTS(SCRIPT, "scripts"),
    STYLES(STYLE, "styles"),
    IMAGES(STATIC, "images");

    public static final Map<String, WebAssetsConfig.BundleConfig> ASSETS_BUNDLES = Arrays.stream(AssetsBundles.values())
            .collect(Collectors.toMap(AssetsBundles::getKey, Function.identity()));
    private final WebAssetType type;
    private String dir;

    AssetsBundles(WebAssetType type, String dir) {
        this.type = type;
        this.dir = dir;
    }

    @Override
    public String dir() {
        return "assets/" + dir;
    }

    @Override
    public WebAssetType type() {
        return type;
    }

    @Override
    public Optional<String> pathMatcher() {
        return Optional.empty();
    }

    @Override
    public Charset defaultCharset() {
        return StandardCharsets.UTF_8;
    }

    public String getKey() {
        return "assets-" + this.name().toLowerCase();
    }
}
