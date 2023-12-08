package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.join;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;
import static java.util.function.Predicate.not;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.runtime.annotations.ConfigDocDefault;
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
        return join(webRoot(), dir);
    }

    /**
     * Bundle entry points config for scripts and styles
     */
    @ConfigDocDefault("if not overridden, by default, 'app' directory will be bundled with 'main' as entry point key")
    Map<String, EntryPointConfig> bundle();

    /**
     * Resources located in {quarkus.web-bundler.web-root}/{quarkus.web-bundler.static} will be served by Quarkus.
     * This directory path is also used as prefix for serving
     * (e.g. {quarkus.web-bundler.web-root}/static/foo.png will be served on {quarkus.http.root-path}/static/foo.png)
     */
    @WithName("static")
    @WithDefault("static")
    String staticDir();

    /**
     * When configured with an internal path (e.g. 'foo/bar'), Bundle files will be served on this path by Quarkus (prefixed by
     * {quarkus.http.root-path}).
     * When configured with an external URL (e.g. 'https://my.cdn.org/'), Bundle files will NOT be served by Quarkus
     * and all resolved paths in the bundle and mapping will automatically point to this url (a CDN for example).
     */
    @WithDefault("static/bundle")
    String bundlePath();

    /**
     * The config for esbuild loaders https://esbuild.github.io/content-types/
     */
    LoadersConfig loaders();

    /**
     * This defines the list of external paths for esbuild (https://esbuild.github.io/api/#external).
     * Instead of being bundled, the import will be preserved.
     */
    @ConfigDocDefault("{quarkus.http.root-path}static/*")
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

    default String httpRootPath() {
        Config allConfig = ConfigProvider.getConfig();
        final String rootPath = allConfig.getOptionalValue("quarkus.http.root-path", String.class)
                .orElse("/");
        return prefixWithSlash(rootPath);
    }

    default String publicBundlePath() {
        return isExternalBundlePath() ? bundlePath() : join(httpRootPath(), bundlePath());
    }

    default boolean isExternalBundlePath() {
        return bundlePath().matches("^https?://.*");
    }

    default boolean shouldQuarkusServeBundle() {
        return !isExternalBundlePath();
    }

    interface WebDependenciesConfig {

        /**
         * Path to the node_modules directory (relative to the project root).
         */
        @ConfigDocDefault("node_modules will be in the build/target directory")
        Optional<String> nodeModules();

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
         */
        @ConfigDocDefault("the bundle map key")
        Optional<String> dir();

        /**
         * The key for this entry point (use the same key as another to bundle them together).
         */
        @ConfigDocDefault("the bundle map key")
        Optional<String> key();

        /**
         * Indicate if this directory contains qute tags (as .html files)
         * This is only available if the Quarkus Qute extension is in the project.
         */
        @WithDefault("false")
        boolean quteTags();

        default String effectiveDir(String mapKey) {
            return dir().filter(not(String::isBlank)).orElse(mapKey);
        }

        default String effectiveKey(String mapKey) {
            return key().filter(not(String::isBlank)).orElse(mapKey);
        }

    }

}
