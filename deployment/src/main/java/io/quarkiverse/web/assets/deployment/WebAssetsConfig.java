package io.quarkiverse.web.assets.deployment;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.web-assets")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface WebAssetsConfig {

    /**
     * Bundles of assets
     */
    Map<String, BundleConfig> bundles();

    /**
     * Configuration preset to allow defining static assets.
     * - assets/styles for css,scss
     * - assets/scripts for js
     * - assets/images for images
     */
    @WithDefault("true")
    boolean presetAssets();

    /**
     * Configuration preset to allow defining components (js + style + html).
     * Convention is to use /components/[name]/[name].{js,css,html}
     */
    // @formatter:on
    @WithDefault("true")
    boolean presetComponents();

    interface BundleConfig {
        /**
         * The directory containing the web-assets for this bundle
         */
        String dir();

        /**
         * The web-asset type
         */
        WebAssetType type();

        /**
         * The glob to find the assets of this bundle
         */
        Optional<String> pathMatcher();

        /**
         * The default charset for this bundle
         */
        @WithDefault("UTF-8")
        Charset defaultCharset();

    }

    enum WebAssetType {
        SCRIPT("*.js"),
        STYLE("*.{css,scss}"),
        QUTE_TAG("*.html"),
        STATIC("*.*");

        private String pathMatcher;

        WebAssetType(String pathMatcher) {
            this.pathMatcher = pathMatcher;
        }

        public String pathMatcher() {
            return pathMatcher;
        }
    }
}
