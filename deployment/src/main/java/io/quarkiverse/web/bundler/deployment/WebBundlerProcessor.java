package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.BundleWebAssetsScannerProcessor.MAIN_ENTRYPOINT_KEY;
import static io.quarkiverse.web.bundler.deployment.StaticWebAssetsProcessor.makePublic;
import static io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType.MANUAL;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.join;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.surroundWithSlashes;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
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
import io.mvnpm.esbuild.model.AutoEntryPoint.AutoDepsMode;
import io.mvnpm.esbuild.model.BundleOptions;
import io.mvnpm.esbuild.model.BundleOptionsBuilder;
import io.mvnpm.esbuild.model.BundleResult;
import io.mvnpm.esbuild.model.EsBuildConfig;
import io.mvnpm.esbuild.model.EsBuildConfigBuilder;
import io.quarkiverse.web.bundler.deployment.WebBundlerConfig.LoadersConfig;
import io.quarkiverse.web.bundler.deployment.items.BundleConfigAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem.Dependency;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkiverse.web.bundler.runtime.BundleRedirectHandlerRecorder;
import io.quarkiverse.web.bundler.runtime.WebBundlerBuildRecorder;
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
            Optional<BundleConfigAssetsBuildItem> bundleConfig,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchMode,
            OutputTargetBuildItem outputTarget) throws BuildException {
        if ((entryPoints.isEmpty() && !config.dependencies().autoImport().isEnabled()) || webDependencies.isEmpty()) {
            LOGGER.info("No bundle or dependencies to process");
            return;
        }
        final BundlesBuildContext bundlesBuildContext = liveReload.getContextObject(BundlesBuildContext.class);
        final boolean isLiveReload = liveReload.isLiveReload()
                && bundlesBuildContext != null
                && bundlesBuildContext.bundleDistDir() != null;
        if (isLiveReload
                && Objects.equals(webDependencies.list(), bundlesBuildContext.dependencies())
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
        final Path targetDir = outputTarget.getOutputDirectory().resolve(TARGET_DIR_NAME);
        final Path nodeModulesDir = resolveNodeModulesDir(config, outputTarget);
        try {
            if (!isLiveReload) {
                FileUtil.deleteDirectory(targetDir);
            }
            Files.createDirectories(targetDir);
            LOGGER.debugf("Preparing bundle in %s", targetDir);

            if (bundleConfig.isPresent()) {
                for (WebAsset webAsset : bundleConfig.get().getWebAssets()) {
                    if (webAsset.filePath().isPresent()) {
                        final Path targetConfig = targetDir.resolve(webAsset.pathFromWebRoot(config.webRoot()));
                        Files.deleteIfExists(targetConfig);
                        Files.copy(webAsset.filePath().get(), targetConfig);
                    }
                }
            }

            final Map<String, EsBuildConfig.Loader> loaders = computeLoaders(config);
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
                    .withDependencies(webDependencies.list().stream().map(Dependency::toEsBuildWebDependency).toList())
                    .withEsConfig(esBuildConfigBuilder.build())
                    .withNodeModulesDir(nodeModulesDir);
            final Set<String> directWebDependenciesIds = webDependencies.list().stream().filter(Dependency::direct)
                    .map(Dependency::id).collect(Collectors.toSet());
            int addedEntryPoints = 0;
            final AutoDepsMode autoDepsMode = AutoDepsMode.valueOf(config.dependencies().autoImport().mode().toString());
            for (EntryPointBuildItem entryPoint : entryPoints) {
                final List<String> scripts = new ArrayList<>();
                for (BundleWebAsset webAsset : entryPoint.getWebAssets()) {
                    String destination = webAsset.pathFromWebRoot(config.webRoot());
                    final Path scriptPath = targetDir.resolve(destination);
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
                    // Manual assets are supposed to be imported by the entry point
                    if (!webAsset.type().equals(MANUAL)) {
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
                    optionsBuilder.addAutoEntryPoint(targetDir, entryPoint.getEntryPointKey(), scripts, autoDepsMode,
                            directWebDependenciesIds::contains);
                    addedEntryPoints++;
                }
            }

            if (addedEntryPoints == 0) {
                optionsBuilder.addAutoEntryPoint(targetDir, MAIN_ENTRYPOINT_KEY, List.of(), autoDepsMode,
                        directWebDependenciesIds::contains);
                LOGGER.info("No custom entry points found, it will be generated based on web dependencies.");
            }

            final BundleOptions options = optionsBuilder.build();
            if (!isLiveReload
                    || !Objects.equals(webDependencies.list(), bundlesBuildContext.dependencies())) {
                long startedInstall = Instant.now().toEpochMilli();
                if (Bundler.install(targetDir, options)) {
                    final long duration = Instant.now().minusMillis(startedInstall).toEpochMilli();
                    if (LOGGER.isDebugEnabled()) {
                        String deps = webDependencies.list().stream().map(Dependency::id)
                                .collect(
                                        Collectors.joining(", "));
                        LOGGER.infof("%d web dependencies installed in %sms: %s", webDependencies.list().size(),
                                duration, deps);
                    } else {
                        LOGGER.infof("%d web Dependencies installed in %sms.", webDependencies.list().size(),
                                duration);
                    }
                } else if (webDependencies.isEmpty()) {
                    LOGGER.info("No web dependencies to install.");
                } else {
                    LOGGER.info("All web dependencies are already installed.");
                }
            }
            final long startedBundling = Instant.now().toEpochMilli();
            final BundleResult result = Bundler.bundle(options, false);
            if (!result.result().output().isBlank()) {
                LOGGER.debugf(result.result().output());
            }

            handleBundleDistDir(config, generatedBundleProducer, staticResourceProducer, result.dist(), startedBundling,
                    true);
            liveReload.setContextObject(BundlesBuildContext.class,
                    new BundlesBuildContext(webDependencies.list(), entryPoints, result.dist()));

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

    void handleBundleDistDir(WebBundlerConfig config, BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer, Path bundleDir, Long started,
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
                        makePublic(staticResourceProducer, resourcePath, path.normalize());
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
    @Record(STATIC_INIT)
    void initBundleBean(
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
    void initBundleRedirect(WebBundlerConfig config, BuildProducer<RouteBuildItem> routes,
            BundleRedirectHandlerRecorder recorder, GeneratedBundleBuildItem generatedBundle) {
        if (config.bundleRedirect()) {
            final Map<String, String> bundle = generatedBundle != null ? generatedBundle.getBundle() : Map.of();
            routes.produce(RouteBuildItem.builder().route(join(prefixWithSlash(config.bundlePath()), "*"))
                    .handler(recorder.handler(bundle))
                    .build());
        }
    }

    public record BundlesBuildContext(List<Dependency> dependencies, List<EntryPointBuildItem> entryPoints,
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

}
