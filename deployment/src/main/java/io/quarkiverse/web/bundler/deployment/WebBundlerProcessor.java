package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.ProjectResourcesScanner.readTemplateContent;
import static io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType.MANUAL;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.join;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.surroundWithSlashes;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.BundleException;
import io.mvnpm.esbuild.Bundler;
import io.mvnpm.esbuild.model.BundleOptions;
import io.mvnpm.esbuild.model.BundleOptionsBuilder;
import io.mvnpm.esbuild.model.BundleResult;
import io.mvnpm.esbuild.model.EsBuildConfig;
import io.mvnpm.esbuild.model.EsBuildConfigBuilder;
import io.mvnpm.esbuild.model.WebDependency;
import io.quarkiverse.web.bundler.deployment.WebBundlerConfig.LoadersConfig;
import io.quarkiverse.web.bundler.deployment.items.BundleConfigAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.bundler.deployment.items.HtmlTemplatesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode;
import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkiverse.web.bundler.runtime.WebBundlerBuildRecorder;
import io.quarkiverse.web.bundler.runtime.WebDependenciesBlockerRecorder;
import io.quarkiverse.web.bundler.sass.SassBuildTimeCompiler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.qute.*;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

class WebBundlerProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerProcessor.class);

    private static final Map<EsBuildConfig.Loader, Function<LoadersConfig, Optional<Set<String>>>> LOADER_CONFIGS = Map
            .ofEntries(
                    entry(EsBuildConfig.Loader.JS, LoadersConfig::js),
                    entry(EsBuildConfig.Loader.JSX, LoadersConfig::jsx),
                    entry(EsBuildConfig.Loader.TS, LoadersConfig::ts),
                    entry(EsBuildConfig.Loader.TSX, LoadersConfig::tsx),
                    entry(EsBuildConfig.Loader.CSS, LoadersConfig::css),
                    entry(EsBuildConfig.Loader.LOCAL_CSS, LoadersConfig::localCss),
                    entry(EsBuildConfig.Loader.GLOBAL_CSS, LoadersConfig::globalCss),
                    entry(EsBuildConfig.Loader.JSON, LoadersConfig::json),
                    entry(EsBuildConfig.Loader.TEXT, LoadersConfig::text),
                    entry(EsBuildConfig.Loader.FILE, LoadersConfig::file),
                    entry(EsBuildConfig.Loader.EMPTY, LoadersConfig::empty),
                    entry(EsBuildConfig.Loader.COPY, LoadersConfig::copy),
                    entry(EsBuildConfig.Loader.DATAURL, LoadersConfig::dataUrl),
                    entry(EsBuildConfig.Loader.BASE64, LoadersConfig::base64),
                    entry(EsBuildConfig.Loader.BINARY, LoadersConfig::binary));
    private static final String TARGET_DIR_NAME = "web-bundler";

    static {
        for (EsBuildConfig.Loader loader : EsBuildConfig.Loader.values()) {
            if (!LOADER_CONFIGS.containsKey(loader)) {
                throw new Error("There is no WebBundleConfig.LoadersConfig for this loader : " + loader);
            }
        }
    }

    @BuildStep
    void bundle(WebBundlerConfig config,
            WebDependenciesBuildItem webDependencies,
            List<EntryPointBuildItem> entryPoints,
            BundleConfigAssetsBuildItem bundleConfigs,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchMode,
            OutputTargetBuildItem outputTarget) throws BuildException {
        if (entryPoints.isEmpty()) {
            LOGGER.info("No bundle to process");
            return;
        }
        final BundlesBuildContext bundlesBuildContext = liveReload.getContextObject(BundlesBuildContext.class);
        final boolean isLiveReload = liveReload.isLiveReload()
                && bundlesBuildContext != null
                && bundlesBuildContext.bundleDistDir() != null;
        if (isLiveReload
                && Objects.equals(webDependencies.getDependencies(), bundlesBuildContext.webDependencies())
                && !liveReload.getChangedResources().contains(config.fromWebRoot("tsconfig.json"))
                && entryPoints.equals(bundlesBuildContext.entryPoints())
                && entryPoints.stream().map(EntryPointBuildItem::getWebAssets).flatMap(List::stream)
                        .map(WebAsset::resourceName)
                        .noneMatch(liveReload.getChangedResources()::contains)
                && Files.isDirectory(bundlesBuildContext.bundleDistDir())) {
            LOGGER.debug("Bundling not needed for live reload");
            handleBundleDistDir(config, generatedBundleProducer, staticResourceProducer, bundlesBuildContext.bundleDistDir(),
                    null, false);
            return;
        }
        boolean hasScssChange = isLiveReload
                && liveReload.getChangedResources().stream().anyMatch(WebBundlerProcessor::isSassFile);
        final Path targetDir = outputTarget.getOutputDirectory().resolve(TARGET_DIR_NAME);
        final Path nodeModulesDir = resolveNodeModulesDir(config, outputTarget);
        try {
            if (!isLiveReload) {
                FileUtil.deleteDirectory(targetDir);
            }
            Files.createDirectories(targetDir);
            LOGGER.debugf("Preparing bundle in %s", targetDir);

            if (!bundleConfigs.getWebAssets().isEmpty()) {
                for (WebAsset webAsset : bundleConfigs.getWebAssets()) {
                    if (webAsset.filePath().isPresent()) {
                        final Path targetConfig = targetDir.resolve(webAsset.pathFromWebRoot(config.webRoot()));
                        Files.deleteIfExists(targetConfig);
                        Files.copy(webAsset.filePath().get(), targetConfig);
                    }
                }
            }

            final Map<String, EsBuildConfig.Loader> loaders = computeLoaders(config);
            loaders.put(".scss", EsBuildConfig.Loader.CSS);
            final EsBuildConfigBuilder esBuildConfigBuilder = new EsBuildConfigBuilder()
                    .loader(loaders)
                    .publicPath(config.publicBundlePath())
                    .splitting(config.bundleSplitting())
                    .minify(launchMode.getLaunchMode().equals(LaunchMode.NORMAL));
            if (config.externalImports().isPresent()) {
                for (String e : config.externalImports().get()) {
                    esBuildConfigBuilder.addExternal(e);
                }
            } else {
                esBuildConfigBuilder.addExternal(join(config.httpRootPath(), "static/*"));
            }
            final BundleOptionsBuilder optionsBuilder = new BundleOptionsBuilder()
                    .withWorkDir(targetDir)
                    .withDependencies(webDependencies.getDependencies())
                    .withEsConfig(esBuildConfigBuilder.build())
                    .withNodeModulesDir(nodeModulesDir);
            int addedEntryPoints = 0;
            for (EntryPointBuildItem entryPoint : entryPoints) {
                final List<String> scripts = new ArrayList<>();
                for (BundleWebAsset webAsset : entryPoint.getWebAssets()) {
                    String destination = webAsset.pathFromWebRoot(config.webRoot());
                    final Path scriptPath = targetDir.resolve(destination);
                    if (hasScssChange && isSassFile(scriptPath.getFileName().toString())) {
                        // This scss has been converted to css in the last cycle
                        Files.deleteIfExists(scriptPath);
                    }
                    if (!isLiveReload
                            || liveReload.getChangedResources().contains(webAsset.resourceName())
                            || !Files.exists(scriptPath)) {
                        Files.createDirectories(scriptPath.getParent());
                        if (webAsset.hasContent()) {
                            Files.write(scriptPath, webAsset.content(), StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                        } else {
                            Files.copy(webAsset.filePath().orElseThrow(), scriptPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    if (!webAsset.type().equals(MANUAL) && !isImportSassFile(scriptPath.getFileName().toString())) {
                        scripts.add(destination);
                    }
                }
                String scriptsLog = scripts.stream()
                        .map(s -> String.format("  - %s", s))
                        .collect(
                                Collectors.joining("\n"));

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Bundling '%s' (%d files):\n%s", entryPoint.getEntryPointKey(), scripts.size(), scriptsLog);
                } else {
                    LOGGER.infof("Bundling '%s' (%d files)", entryPoint.getEntryPointKey(), scripts.size());
                }

                if (!scripts.isEmpty()) {
                    optionsBuilder.addAutoEntryPoint(targetDir, entryPoint.getEntryPointKey(), scripts);
                    addedEntryPoints++;
                }
            }
            if (addedEntryPoints > 0) {
                final BundleOptions options = optionsBuilder.build();
                if (!isLiveReload
                        || !Objects.equals(webDependencies.getDependencies(), bundlesBuildContext.webDependencies())) {
                    long startedInstall = Instant.now().toEpochMilli();
                    if (Bundler.install(targetDir, options)) {
                        final long duration = Instant.now().minusMillis(startedInstall).toEpochMilli();
                        if (LOGGER.isDebugEnabled()) {
                            String deps = webDependencies.getDependencies().stream().map(WebDependency::id)
                                    .collect(
                                            Collectors.joining(", "));
                            LOGGER.infof("%d web dependencies installed in %sms: %s", webDependencies.getDependencies().size(),
                                    duration, deps);
                        } else {
                            LOGGER.infof("%d web Dependencies installed in %sms.", webDependencies.getDependencies().size(),
                                    duration);
                        }
                    } else if (webDependencies.getDependencies().isEmpty()) {
                        LOGGER.info("No web dependencies to install.");
                    } else {
                        LOGGER.info("All web dependencies are already installed.");
                    }
                }
                final long startedBundling = Instant.now().toEpochMilli();
                // SCSS conversion
                if (!isLiveReload || hasScssChange) {
                    try (Stream<Path> stream = Files.find(targetDir, Integer.MAX_VALUE,
                            (p, a) -> !p.toString().contains("node_modules")
                                    && isCompiledSassFile(p.getFileName().toString()))) {
                        stream.forEach(p -> convertToScss(p, targetDir));
                    }
                }
                final BundleResult result = Bundler.bundle(options, false);
                if (!result.result().output().isBlank()) {
                    LOGGER.debugf(result.result().output());
                }

                handleBundleDistDir(config, generatedBundleProducer, staticResourceProducer, result.dist(), startedBundling,
                        true);
                liveReload.setContextObject(BundlesBuildContext.class,
                        new BundlesBuildContext(webDependencies.getDependencies(), entryPoints, result.dist()));
            } else {
                liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext());
                LOGGER.debugf("No entrypoint found, no bundle generated.");
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (BundleException e) {
            liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext());
            throw e;
        }
    }

    private static Path resolveNodeModulesDir(WebBundlerConfig config, OutputTargetBuildItem outputTarget) {
        if (config.dependencies().nodeModules().isEmpty()) {
            return outputTarget.getOutputDirectory().resolve(BundleOptions.NODE_MODULES);
        }
        final Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
        final Path nodeModulesDir = Path.of(config.dependencies().nodeModules().get().trim());
        if (nodeModulesDir.isAbsolute() && Files.isDirectory(nodeModulesDir.getParent())) {
            return nodeModulesDir;
        }
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            throw new IllegalStateException(
                    "If not absolute, the node_modules directory is resolved relative to the project root, but Web Bundler was not able to find the project root.");
        }
        return projectRoot.resolve(nodeModulesDir);
    }

    private Map<String, EsBuildConfig.Loader> computeLoaders(WebBundlerConfig config) {
        Map<String, EsBuildConfig.Loader> loaders = new HashMap<>();
        for (EsBuildConfig.Loader loader : EsBuildConfig.Loader.values()) {
            final Function<LoadersConfig, Optional<Set<String>>> configFn = requireNonNull(LOADER_CONFIGS.get(loader));
            final Optional<Set<String>> values = configFn.apply(config.loaders());
            if (values.isPresent()) {
                for (String v : values.get()) {
                    final String ext = v.startsWith(".") ? v : "." + v;
                    if (loaders.containsKey(ext)) {
                        throw new ConfigurationException(
                                "A Web Bundler file extension for loaders is provided more than once: " + ext);
                    }
                    loaders.put(ext, loader);
                }
            }
        }
        return loaders;
    }

    static void convertToScss(Path file, Path root) {
        LOGGER.debugf("Converting %s to css", file);
        String content = SassBuildTimeCompiler.convertScss(file, root, (s, s2) -> {
        });
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void handleBundleDistDir(WebBundlerConfig config, BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, Path bundleDir, Long started,
            boolean changed) {
        try {
            Map<String, String> bundle = new HashMap<>();
            List<String> names = new ArrayList<>();
            StringBuilder mappingString = new StringBuilder();
            try (Stream<Path> stream = Files.find(bundleDir, 20, (p, i) -> Files.isRegularFile(p))) {
                stream.forEach(path -> {
                    final String relativePath = bundleDir.relativize(path).toString();
                    final String key = relativePath.replaceAll("-[^-.]+\\.", ".");
                    final String publicBundleAssetPath = join(config.publicBundlePath(), relativePath);
                    final String fileName = path.getFileName().toString();
                    final String ext = fileName.substring(fileName.indexOf("."));
                    if (Bundle.BUNDLE_MAPPING_EXT.contains(ext)) {
                        mappingString.append("  ").append(key).append(" => ").append(publicBundleAssetPath).append("\n");
                        bundle.put(key, publicBundleAssetPath);
                    }
                    names.add(publicBundleAssetPath);
                    if (config.shouldQuarkusServeBundle()) {
                        // The root-path will already be added by the static resources handler
                        final String resourcePath = surroundWithSlashes(config.bundlePath()) + relativePath;
                        makePublic(staticResourceProducer, resourcePath, path.normalize(), WatchMode.DISABLED, changed);
                    }
                });
            }
            if (started != null) {
                LOGGER.infof("Bundle generated %d files in %sms", names.size(),
                        Instant.now().minusMillis(started).toEpochMilli());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Bundle dir: '%s'\n  - %s", bundleDir, names.size(),
                            String.join("\n  - ", names));
                }
                if (LOGGER.isDebugEnabled() || LaunchMode.current() == LaunchMode.DEVELOPMENT) {
                    LOGGER.infof("Bundle#mapping:\n%s", mappingString);
                }

            }

            generatedBundleProducer.produce(new GeneratedBundleBuildItem(bundleDir, bundle));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    void processStaticWebAssets(WebBundlerConfig config,
            StaticAssetsBuildItem staticAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.pathFromWebRoot(config.webRoot());
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(publicPath), liveReload, webAsset);
        }
    }

    @BuildStep
    void processHtmlTemplateWebAssets(WebBundlerConfig config,
            HtmlTemplatesBuildItem htmlTemplates,
            GeneratedBundleBuildItem generatedBundle,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        final Map<String, String> bundle = generatedBundle != null ? generatedBundle.getBundle() : Map.of();
        final Bundle.Mapping mapping = new Bundle.Mapping() {
            @Override
            public String get(String name) {
                return bundle.get(name);
            }

            @Override
            public Set<String> names() {
                return bundle.keySet();
            }
        };
        final Engine engine = Engine.builder()
                .addDefaults()
                .addNamespaceResolver(new NamespaceResolver.NamespaceResolverImpl("inject", 0, (c) -> {
                    if (c.getName().equals("bundle")) {
                        return CompletedStage.of(new Bundle(mapping));
                    }
                    return null;
                }))
                .addLocator(new WebBundlerTagsLocator())
                .addSectionHelper(new UserTagSectionHelper.Factory("bundle", "web-bundler/bundle.html"))
                .addValueResolver(new ReflectionValueResolver())
                .addParserHook(new Qute.IndexedArgumentsParserHook())
                .addResultMapper(new HtmlEscaper(ImmutableList.of("text/html", "text/xml")))
                .build();
        for (WebAsset webAsset : htmlTemplates.getWebAssets()) {
            final byte[] bytes = webAsset.contentOrReadFromFile();
            final String content = engine.parse(new String(bytes, webAsset.charset())).render();
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(webAsset.pathFromWebRoot(config.webRoot())), liveReload,
                    HtmlPageWebAsset.of(webAsset, content));
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    void initBundler(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            GeneratedBundleBuildItem generatedBundle,
            WebBundlerBuildRecorder recorder) {
        final Map<String, String> bundle = generatedBundle != null ? generatedBundle.getBundle() : Map.of();
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(Bundle.Mapping.class)
                .supplier(recorder.createContext(bundle))
                .done());
        additionalBeans.produce(new AdditionalBeanBuildItem(Bundle.class));
    }

    @BuildStep
    @Record(STATIC_INIT)
    void webDepBlocker(WebBundlerConfig config, BuildProducer<RouteBuildItem> routes, WebDependenciesBlockerRecorder recorder) {
        if (!config.dependencies().serve()) {
            routes.produce(RouteBuildItem.builder().orderedRoute("/_static/*", 0)
                    .handler(recorder.handler())
                    .build());
            routes.produce(RouteBuildItem.builder().orderedRoute("/webjars/*", 0)
                    .handler(recorder.handler())
                    .build());
        }
    }

    private static void makeWebAssetPublic(
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            String publicPath,
            LiveReloadBuildItem liveReload,
            WebAsset webAsset) {
        handleStaticResource(
                staticResourceProducer,
                Set.of(new GeneratedStaticResourceBuildItem.Source(webAsset.resourceName(), webAsset.filePath())),
                publicPath,
                webAsset.contentOrReadFromFile(),
                liveReload.isLiveReload() && liveReload.getChangedResources().contains(webAsset.resourceName()),
                WatchMode.RESTART);
    }

    private static void makePublic(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, String publicPath,
            Path file, WatchMode watchMode, boolean changed) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, Collections.emptySet(), publicPath, readTemplateContent(file), changed,
                watchMode);
    }

    private static void handleStaticResource(
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            Set<GeneratedStaticResourceBuildItem.Source> sources,
            String publicPath,
            byte[] content,
            boolean changed,
            WatchMode watchMode) {
        staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                sources,
                publicPath,
                content,
                true,
                watchMode,
                changed));
    }

    /**
     * Returns true if the given filename (not path) does not start with _
     * and ends with either .sass or .scss case-insensitive
     */
    public static boolean isCompiledSassFile(String filename) {
        return !filename.startsWith("_") && isSassFile(filename);
    }

    public static boolean isImportSassFile(String filename) {
        return filename.startsWith("_") && isSassFile(filename);
    }

    public static boolean isSassFile(String filename) {
        String lc = filename.toLowerCase();
        return lc.endsWith(".scss") || lc.endsWith(".sass");
    }

    public record BundlesBuildContext(List<WebDependency> webDependencies, List<EntryPointBuildItem> entryPoints,
            Path bundleDistDir) {

        public BundlesBuildContext() {
            this(List.of(), List.of(), null);
        }
    }

    static Path findProjectRoot(Path outputDirectory) {
        Path currentPath = outputDirectory;
        do {
            if (Files.exists(currentPath.resolve(Paths.get("src", "main")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.properties")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yaml")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yml")))) {
                return currentPath.normalize();
            }
            if (currentPath.getParent() != null && Files.exists(currentPath.getParent())) {
                currentPath = currentPath.getParent();
            } else {
                return null;
            }
        } while (true);
    }

    private static final class WebBundlerTagsLocator implements TemplateLocator {
        @Override
        public Optional<TemplateLocation> locate(String id) {
            if (!id.startsWith("web-bundler/")) {
                return Optional.empty();
            }
            String name = id.replace("web-bundler/", "");
            try (InputStream templateStream = this.getClass().getResourceAsStream("/templates/tags/" + name)) {
                if (templateStream == null) {
                    return Optional.empty();
                }
                return Optional.of(new TemplateLocation() {
                    @Override
                    public Reader read() {
                        return new InputStreamReader(templateStream, StandardCharsets.UTF_8);
                    }

                    @Override
                    public Optional<Variant> getVariant() {
                        return Optional.empty();
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    record HtmlPageWebAsset(String resourceName, byte[] content, Charset charset) implements WebAsset {

        static HtmlPageWebAsset of(WebAsset sourceAsset, String content) {
            return new HtmlPageWebAsset(sourceAsset.resourceName(), content.getBytes(sourceAsset.charset()),
                    sourceAsset.charset());
        }

        @Override
        public Optional<Path> filePath() {
            return Optional.empty();
        }

    }
}
