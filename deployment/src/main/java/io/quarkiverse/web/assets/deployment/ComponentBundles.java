package io.quarkiverse.web.assets.deployment;

import static io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType.QUTE_TAG;
import static io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType.SCRIPT;
import static io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType.STYLE;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.web.assets.deployment.WebAssetsConfig.WebAssetType;

public enum ComponentBundles implements WebAssetsConfig.BundleConfig {
    SCRIPTS(SCRIPT),
    STYLES(STYLE),
    TAGS(QUTE_TAG);

    public static final Map<String, WebAssetsConfig.BundleConfig> COMPONENT_BUNDLES = Arrays.stream(ComponentBundles.values())
            .collect(Collectors.toMap(ComponentBundles::getKey, Function.identity()));
    private final WebAssetType type;

    ComponentBundles(WebAssetType type) {
        this.type = type;
    }

    @Override
    public String dir() {
        return "components";
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
        return this.name().toLowerCase();
    }
}
