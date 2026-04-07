package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.join;
import static io.quarkiverse.web.bundler.deployment.BundleWebAssetsScannerProcessor.DIST;
import static io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig.DEFAULT_ENTRY_POINT_KEY;
import static io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType.MANUAL;
import static io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourcesProcessor.WEB_BUNDLER_LIVE_RELOAD_PATH;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.model.AutoEntryPoint;
import io.mvnpm.esbuild.model.BundleOptions;
import io.mvnpm.esbuild.model.BundleOptionsBuilder;
import io.mvnpm.esbuild.model.EsBuildConfig;
import io.mvnpm.esbuild.model.EsBuildConfigBuilder;
import io.mvnpm.esbuild.plugin.EsBuildPluginSass;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig.LoadersConfig;
import io.quarkiverse.web.bundler.deployment.items.BundleConfigAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset;
import io.quarkiverse.web.bundler.deployment.items.DevWatchedLinkBuildItem;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.InstalledWebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ReadyForBundlingBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerEsbuildPluginBuiltItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem.Dependency;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;

public class BundlePrepareProcessor {

    private static final Logger LOGGER = Logger.getLogger(BundlePrepareProcessor.class);
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
    private static final String LAUNCH_MODE_ENV = "LAUNCH_MODE";

    static {
        for (EsBuildConfig.Loader loader : EsBuildConfig.Loader.values()) {
            if (!LOADER_CONFIGS.containsKey(loader)) {
                throw new Error("There is no WebBundleConfig.LoadersConfig for this loader : " + loader);
            }
        }
    }

    @BuildStep
    ReadyForBundlingBuildItem prepareForBundling(WebBundlerConfig config,
            ProjectRootBuildItem projectRoot,
            InstalledWebDependenciesBuildItem installedWebDependencies,
            List<WebBundlerEsbuildPluginBuiltItem> plugins,
            List<EntryPointBuildItem> entryPoints,
            WebBundlerTargetDirBuildItem targetDir,
            Optional<BundleConfigAssetsBuildItem> bundleConfig,
            LaunchModeBuildItem launchMode,
            BuildProducer<DevWatchedLinkBuildItem> watchedLinks,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            HttpRootPathBuildItem httpRootPath) {
        if (entryPoints.isEmpty()) {
            if (!config.dependencies().autoImport().isEnabled()) {
                LOGGER.warn("Skipping Web Bundling because no entry-point detected (create one or enable auto-import)");
                return null;
            } else {
                if (installedWebDependencies == null || installedWebDependencies.isEmpty()) {
                    LOGGER.warn("Skipping Web Bundling because no Web Dependencies found for auto-import.");
                    return null;
                } else {
                    LOGGER.info("No Web Bundling entry points found, it will be generated based on direct Web Dependencies.");
                }
            }
        }

        final long startTime = Instant.now().toEpochMilli();

        try {
            Files.createDirectories(targetDir.webBundler());
            LOGGER.debugf("Preparing Web Bundle in %s", targetDir);
            final boolean browserLiveReload = launchMode.getLaunchMode().equals(LaunchMode.DEVELOPMENT)
                    && config.browserLiveReload();
            if (bundleConfig.isPresent()) {
                for (ProjectFile webAsset : bundleConfig.get().getWebAssets()) {
                    final Path targetConfig = targetDir.webBundler().resolve(webAsset.indexPath());
                    createAsset(launchMode, browserLiveReload, watchedLinks, watchedFiles, webAsset, targetConfig);
                }
            }

            final Map<String, EsBuildConfig.Loader> loaders = computeLoaders(config);
            final EsBuildConfigBuilder esBuildConfigBuilder = EsBuildConfig.builder()
                    .loader(loaders)
                    .outDir(StringPaths.join(DIST, config.bundlePath()))
                    .publicPath(config.publicBundlePath())
                    .splitting(config.bundling().splitting())
                    .sourceMap(config.bundling().sourceMapEnabled())
                    .define(LAUNCH_MODE_ENV, "'" + launchMode.getLaunchMode().name() + "'");
            boolean fixedNames = false;
            if (browserLiveReload) {
                esBuildConfigBuilder
                        .preserveSymlinks()
                        .minify(false)
                        .define("process.env.LIVE_RELOAD_PATH",
                                "'" + StringPaths.join(httpRootPath.getRootPath(), WEB_BUNDLER_LIVE_RELOAD_PATH)
                                        + "'")
                        .fixedEntryNames();
                fixedNames = true;
            }
            if (!config.bundling().envs().isEmpty()) {
                esBuildConfigBuilder.define(config.bundling().safeEnvs());
            }
            if (config.bundling().external().isPresent()) {
                for (String e : config.bundling().external().get()) {
                    esBuildConfigBuilder.addExternal(e);
                }
            } else {
                esBuildConfigBuilder.addExternal(join(config.httpRootPath(), "static/*"));
            }
            final BundleOptionsBuilder optionsBuilder = BundleOptions.builder()
                    .debugBuild(config.debug())
                    .withWorkDir(targetDir.webBundler())
                    .withDependencies(installedWebDependencies.toEsBuildWebDependencies())
                    .withEsConfig(esBuildConfigBuilder.build())
                    .withNodeModulesDir(installedWebDependencies.nodeModulesDir());

            final Set<String> installedPlugins = new HashSet<>();

            for (WebBundlerEsbuildPluginBuiltItem plugin : plugins) {
                if (installedPlugins.add(plugin.get().name())) {
                    optionsBuilder.addPlugin(plugin.get());
                } else {
                    throw new IllegalStateException("EsBuild plugins should only be installed once:" + plugins);
                }
            }

            if (config.sass()) {
                optionsBuilder.addPlugin(new EsBuildPluginSass());
                installedPlugins.add("sass");
            }

            final Set<String> directWebDependenciesIds = installedWebDependencies.list().stream().filter(Dependency::direct)
                    .map(Dependency::id).collect(Collectors.toSet());
            int addedEntryPoints = 0;
            final AutoEntryPoint.AutoDepsMode autoDepsMode = AutoEntryPoint.AutoDepsMode
                    .valueOf(config.dependencies().autoImport().mode().toString());
            for (EntryPointBuildItem entryPoint : entryPoints) {
                final List<String> scripts = new ArrayList<>();

                for (BundleWebAsset webAsset : entryPoint.assets()) {
                    String destination = webAsset.indexPath();
                    final Path scriptPath = targetDir.webBundler().resolve(destination);
                    createAsset(launchMode, browserLiveReload, watchedLinks, watchedFiles, webAsset, scriptPath);
                    // Manual assets are supposed to be imported by the entry point
                    if (!webAsset.bundleType().equals(MANUAL)) {
                        scripts.add(destination);
                    }
                }
                String scriptsLog = scripts.stream()
                        .map(s -> String.format("  - %s", s))
                        .collect(
                                Collectors.joining("\n"));

                if (!scripts.isEmpty() && entryPoint.output()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("Preparing Web Bundling for '%s' with %d files:\n%s", entryPoint.key(), scripts.size(),
                                scriptsLog);
                    } else {
                        LOGGER.infof("Preparing Web Bundling for '%s' with %d files", entryPoint.key(), scripts.size());
                    }
                    if (browserLiveReload) {
                        Files.write(targetDir.webBundler().resolve("live-reload.js"), readLiveReloadJs());
                        scripts.add("live-reload.js");
                    }

                    optionsBuilder.addAutoEntryPoint(targetDir.webBundler(), entryPoint.key(), scripts,
                            autoDepsMode,
                            directWebDependenciesIds::contains);
                    addedEntryPoints++;
                }
            }

            if (addedEntryPoints == 0) {
                List<String> scripts = new ArrayList<>();
                if (browserLiveReload) {
                    Files.write(targetDir.webBundler().resolve("live-reload.js"), readLiveReloadJs());
                    scripts.add("live-reload.js");
                }
                optionsBuilder.addAutoEntryPoint(targetDir.webBundler(), DEFAULT_ENTRY_POINT_KEY, scripts, autoDepsMode,
                        directWebDependenciesIds::contains);
                LOGGER.info("No custom Web Bundling entry points found, it will be generated based on web dependencies.");
            }

            final BundleOptions options = optionsBuilder.build();
            return new ReadyForBundlingBuildItem(startTime, options, targetDir.dist(), fixedNames);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void createAsset(
            LaunchModeBuildItem launchMode,
            boolean browserLiveReload,
            BuildProducer<DevWatchedLinkBuildItem> watchedLinks,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            ProjectFile webAsset,
            Path targetPath) throws IOException {
        Files.createDirectories(targetPath.getParent());
        if (launchMode.getLaunchMode().isDev()) {
            createLinkOrCopy(browserLiveReload, watchedLinks, watchedFiles, webAsset, targetPath);
        } else {
            Files.write(targetPath, webAsset.content(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }

    }

    /**
     * In dev mode, creates a symbolic link (if source is available and live reload is enabled),
     * copies the local file, or writes content bytes as a fallback.
     * Registers watchers so changes are picked up during live reload.
     */
    static void createLinkOrCopy(boolean browserLiveReload,
            BuildProducer<DevWatchedLinkBuildItem> watchedLinks,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            ProjectFile webAsset,
            Path targetPath) throws IOException {
        Files.deleteIfExists(targetPath);

        if (webAsset.hasSource() && browserLiveReload) {
            try {
                Files.createSymbolicLink(targetPath, webAsset.source());
                watchedLinks.produce(new DevWatchedLinkBuildItem(webAsset.source(), targetPath, true));
                watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                        .setRestartNeeded(false)
                        .setLocation(webAsset.liveReloadWatchPath())
                        .build());
                return;
            } catch (FileSystemException e) {
                // Symlink not supported, falling back to copy
            }
            watchedLinks.produce(new DevWatchedLinkBuildItem(webAsset.source(), targetPath, false));
            Files.copy(webAsset.source(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else if (webAsset.isLocalFile()) {
            // In that case we copy the target file
            watchedLinks.produce(new DevWatchedLinkBuildItem(webAsset.file(), targetPath, false));
            Files.copy(webAsset.file(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // We write the content
            Files.write(targetPath, webAsset.content(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private byte[] readLiveReloadJs() throws IOException {
        try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("web-bundler/live-reload.js")) {
            requireNonNull(resourceAsStream, "resource web-bundler/live-reload.js is required");
            return resourceAsStream.readAllBytes();
        }
    }

    private Map<String, EsBuildConfig.Loader> computeLoaders(WebBundlerConfig config) {
        Map<String, EsBuildConfig.Loader> loaders = new HashMap<>();
        for (EsBuildConfig.Loader loader : EsBuildConfig.Loader.values()) {
            final Function<LoadersConfig, Optional<Set<String>>> configFn = requireNonNull(LOADER_CONFIGS.get(loader));
            final Optional<Set<String>> values = configFn.apply(config.bundling().loaders());
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

}
