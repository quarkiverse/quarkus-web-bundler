package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.removeLeadingSlash;
import static java.util.function.Predicate.not;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotBlank;

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
    @NotBlank
    String webRoot();

    default String fromWebRoot(String dir) {
        return addTrailingSlash(webRoot()) + removeLeadingSlash(dir);
    }

    /**
     * Bundles of scripts and styles
     */
    Map<String, EntryPointConfig> bundle();

    /**
     * Any static file to be served under this path
     */
    @WithName("static")
    @WithDefault("static")
    @NotBlank
    String staticDir();

    /**
     * Bundle files will be served under this path
     */
    @WithName("bundle")
    @WithDefault("static/bundle")
    @NotBlank
    String bundleDir();

    /**
     * The config for presets
     */
    PresetsConfig presets();

    /**
     * The config for esbuild loaders https://esbuild.github.io/content-types/
     */
    LoadersConfig loaders();

    /**
     * This defines the list of external paths for esbuild (https://esbuild.github.io/api/#external).
     * Instead of being bundled, the import will be preserved.
     */
    Optional<List<String>> externalImports();

    /**
     * Enable or disable bundle splitting (https://esbuild.github.io/api/#splitting)
     * Code shared between multiple entry points is split off into a separate shared file (chunk) that both entry points import
     */
    @WithDefault("true")
    Boolean bundleSplitting();

    /**
     * Configure how dependencies are collected
     */
    WebDependenciesConfig dependencies();

    /**
     * The default charset
     */
    @WithDefault("UTF-8")
    Charset charset();

    interface PresetsConfig {

        /**
         * Configuration preset to allow defining the web app with scripts and styles to bundle.
         * - {web-root}/app/**\/*
         *
         * If an index.js/ts is detected, it will be used as entry point for your app.
         * If not found the entry point will be auto-generated with all the files in the app directory.
         *
         * => processed and added to static/[key].js and static/[key].css (key is "main" by default)
         */
        PresetConfig app();

        /**
         * Configuration preset to allow defining web components (js + style + html) as a bundle.
         * Convention is to use:
         * - /{web-root}/components/[name]/[name].js/ts
         * - /{web-root}/components/[name]/[name].scss/css
         * - /{web-root}/components/[name]/[name].html (Qute tag)
         *
         * => processed and added to static/[key].js and static/[key].css (key is "main" by default)
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

        /**
         * If enabled web dependencies will also be served, this is usually not needed as they are already bundled.
         */
        @WithDefault("false")
        boolean serve();

    }

    interface LoadersConfig {

        /**
         * Configure the file extensions using the js loader: https://esbuild.github.io/content-types/#javascript
         */
        @WithDefault("js,cjs,mjs")
        Optional<Set<String>> js();

        /**
         * Configure the file extensions using the jsx loader: https://esbuild.github.io/content-types/#jsx
         */
        @WithDefault("jsx")
        Optional<Set<String>> jsx();

        /**
         * Configure the file extensions using the tsx loader: https://esbuild.github.io/content-types/#jsx
         */
        @WithDefault("tsx")
        Optional<Set<String>> tsx();

        /**
         * Configure the file extensions using the ts loader: https://esbuild.github.io/content-types/#typescript
         */
        @WithDefault("ts,mts,cts")
        Optional<Set<String>> ts();

        /**
         * Configure the file extensions using the css loader: https://esbuild.github.io/content-types/#css
         */
        @WithDefault("css")
        Optional<Set<String>> css();

        /**
         * Configure the file extensions using the local-css loader: https://esbuild.github.io/content-types/#css
         */
        @WithDefault(".module.css")
        Optional<Set<String>> localCss();

        /**
         * Configure the file extensions using the global-css loader: https://esbuild.github.io/content-types/#css
         */
        Optional<Set<String>> globalCss();

        /**
         * Configure the file extensions using the file loader: https://esbuild.github.io/content-types/#file
         * This loader will copy the file to the output directory and embed the file name into the bundle as a string.
         */
        @WithDefault("aac,abw,arc,avif,avi,azw,bin,bmp,bz,bz2,cda,csv,yaml,yml,doc,docx,eot,epub,gz,gif,htm,html,ico,ics,jar,jpeg,jpg,jsonld,mid,midi,mp3,mp4,mpeg,mpkg,odp,ods,odt,oga,ogv,ogx,opus,otf,png,pdf,ppt,pptx,rar,rtf,svg,tar,tif,tiff,ttf,vsd,wav,weba,webm,webp,woff,woff2,xhtml,xls,xlsx,xml,xul,zip,3gp,3g2,7z")
        Optional<Set<String>> file();

        /**
         * Configure the file extensions using the copy loader: https://esbuild.github.io/content-types/#copy
         */
        Optional<Set<String>> copy();

        /**
         * Configure the file extensions using the base64 loader: https://esbuild.github.io/content-types/#base64
         */
        Optional<Set<String>> base64();

        /**
         * Configure the file extensions using the binary loader: https://esbuild.github.io/content-types/#binary
         */
        Optional<Set<String>> binary();

        /**
         * Configure the file extensions using the dataurl loader: https://esbuild.github.io/content-types/#data-url
         */
        Optional<Set<String>> dataUrl();

        /**
         * Configure the file extensions using the empty loader: https://esbuild.github.io/content-types/#empty-file
         */
        Optional<Set<String>> empty();

        /**
         * Configure the file extensions using the text loader: https://esbuild.github.io/content-types/#text
         */
        @WithDefault("txt")
        Optional<Set<String>> text();

        /**
         * Configure the file extensions using the json loader: https://esbuild.github.io/content-types/#json
         */
        @WithDefault("json")
        Optional<Set<String>> json();

    }

    interface EntryPointConfig {

        /**
         * Enable or disable this entry point.
         * You can use this to use the map key as key and dir for this entry point.
         */
        @WithParentName
        @WithDefault("true")
        boolean enabled();

        /**
         * The directory for this entry point under the web root.
         * By default, it will use the bundle map key.
         */
        Optional<String> dir();

        /**
         * The key for this entry point
         * By default, it will use the bundle map key.
         */
        Optional<String> key();

        default String effectiveDir(String mapKey) {
            return dir().filter(not(String::isBlank)).orElse(effectiveKey(mapKey));
        }

        default String effectiveKey(String mapKey) {
            return key().filter(not(String::isBlank)).orElse(mapKey);
        }

    }

    enum WebDependencyType {
        WEBJARS("org.webjars.npm"::equals),
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
