package io.quarkiverse.web.assets.deployment;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.quarkiverse.web.assets.deployment.util.ResourcePaths;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigMapping(prefix = "quarkus.web-bundler")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface WebBundlerConfig {

    /**
     * The directory in the resources which serves as root for the web assets
     */
    @WithDefault("web")
    String webRoot();

    default String fromWebRoot(String dir) {
        return ResourcePaths.join(webRoot(), dir);
    }

    /**
     * Bundles of scripts and styles
     */
    Map<String, EntryPointConfig> bundle();

    /**
     * The config for resources to process with the SCSS compiler if needed and serve statically (non bundled).
     * Default to all files in the "/{web-root}/styles" directory
     */
    AssetsConfig styles();

    /**
     * The static resources.
     * Default to all files in the "/{web-root}/static" directory.
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
         * The directory containing those assets under the root.
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
         * Configuration preset to allow defining the web app with scripts and styles to bundle.
         * - {web-root}/app/**\/*
         *
         * If an index.js/ts is detected, it will be used as entrypoint for your app.
         * If not found the entrypoint will be auto-generated with all the files in the app directory.
         *
         * => processed and added to bundled/[key].js and bundled/[key].css (key is "main" by default)
         */
        PresetConfig app();

        /**
         * Configuration preset to allow defining web components (js + style + html) as a bundle.
         * Convention is to use:
         * - /{web-root}/components/[name]/[name].js/ts
         * - /{web-root}/components/[name]/[name].scss/css
         * - /{web-root}/components/[name]/[name].html (Qute tag)
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
         * The entry point key used for this preset (used in the output)
         */
        @WithDefault("main")
        Optional<String> entryPointKey();
    }

    interface WebDependenciesConfig {

        /**
         * The type used to collect web dependencies:
         * web-jar or mvnpm
         */
        @WithDefault("mvnpm")
        WebDependencyType type();

    }

    interface EntryPointConfig {
        /**
         * The directory for this entrypoint under the web root.
         * By default, it will use the bundle map key.
         */
        Optional<String> dir();

        /**
         * The key for this entrypoint
         * By default, it will use the bundle map key.
         */
        Optional<String> entryPointKey();

        default String effectiveDir(String mapKey) {
            return dir().orElse(effectiveEntryPointKey(mapKey));
        }

        default String effectiveEntryPointKey(String mapKey) {
            return entryPointKey().orElse(mapKey);
        }

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
