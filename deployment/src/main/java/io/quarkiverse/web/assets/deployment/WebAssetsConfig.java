package io.quarkiverse.web.assets.deployment;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigMapping(prefix = "quarkus.web-assets")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface WebAssetsConfig {

    /**
     * Bundles of scripts and styles
     */
    Map<String, BundleConfig> bundles();

    /**
     * The config for resources to process with the SCSS compiler if needed and serve statically (non bundled).
     * Default to all files in the "/web/styles" directory
     */
    AssetsConfig styles();

    /**
     * The static resources.
     * Default to all files in the "/web/static" directory.
     */
    @WithName("static")
    AssetsConfig staticAssets();

    /**
     * The config for presets
     */
    PresetsConfig presets();

    /**
     * Configure how dependencies are collected
     */
    WebDependenciesConfig dependencies();

    /**
     * The default charset
     */
    @WithDefault("UTF-8")
    Charset charset();

    interface AssetsConfig {

        /**
         * Enable this option
         */
        @WithParentName
        @WithDefault("true")
        boolean enabled();

        /**
         * The directory containing those assets
         */
        Optional<String> dir();

        /**
         * The glob path matcher for this
         * All is matched when empty
         */
        Optional<String> glob();
    }

    interface PresetsConfig {

        /**
         * Configuration preset to allow defining the web app to bundle.
         * - web/app/**\/*.{css,scss} for css,scss
         * - web/app/**\/*.js for scripts
         *
         * => processed and added to bundled/[key].js and bundled/[key].css (key is "main" by default)
         */
        PresetConfig app();

        /**
         * Configuration preset to allow defining web components (js + style + html) as a bundle.
         * Convention is to use:
         * - /web/components/[name]/[name].js/ts
         * - /web/components/[name]/[name].scss/css
         * - /web/components/[name]/[name].html (Qute tag)
         *
         * => processed and added to bundled/[key].js and bundled/[key].css (key is "main" by default)
         */
        PresetConfig components();

    }

    interface PresetConfig {

        /**
         * Enable or disable this preset
         *
         * @return
         */
        @WithParentName
        @WithDefault("true")
        boolean enabled();

        /**
         * The bundle key used for this preset
         */
        @WithDefault("main")
        Optional<String> bundleKey();
    }

    interface WebDependenciesConfig {

        /**
         * The type used to collect web dependencies:
         * web-jar or mvnpm
         */
        @WithDefault("mvnpm")
        WebDependencyType type();

    }

    interface BundleConfig {

        /**
         * The directory for this bundle
         * By default, it will use the bundles map key.
         */
        Optional<String> dir();

        /**
         * The key for this bundle
         * By default, it will use the bundles map key.
         */
        Optional<String> key();

        /**
         * The glob to find the assets of this bundle
         */
        @WithDefault("**/*.{js\\,ts\\,css\\,scss\\,sass}")
        String glob();

    }

    enum WebDependencyType {
        WEBJARS("org.webjars"::equals),
        MVNPM(s -> s.startsWith("org.mvnpm"));

        private final Predicate<String> groupMatcher;

        WebDependencyType(Predicate<String> groupMatcher) {
            this.groupMatcher = groupMatcher;
        }

        public boolean matches(Dependency dep) {
            return this.groupMatcher.test(dep.getGroupId());
        }
    }

}
