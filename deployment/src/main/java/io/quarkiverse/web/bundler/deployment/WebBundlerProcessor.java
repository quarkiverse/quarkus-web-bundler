package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.ProjectResourcesScanner.readTemplateContent;
import static io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType.MANUAL;
import static io.quarkiverse.web.bundler.deployment.util.ResourcePaths.prefixWithSlash;
import static io.quarkiverse.web.bundler.runtime.qute.WebBundlerQuteContextRecorder.WEB_BUNDLER_ID_PREFIX;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import ch.nerdin.esbuild.Bundler;
import ch.nerdin.esbuild.modal.BundleOptionsBuilder;
import ch.nerdin.esbuild.modal.EsBuildConfig;
import ch.nerdin.esbuild.modal.EsBuildConfigBuilder;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTagsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode;
import io.quarkiverse.web.bundler.runtime.WebBundlerBuild;
import io.quarkiverse.web.bundler.runtime.WebBundlerBuildRecorder;
import io.quarkiverse.web.bundler.runtime.qute.BundleTag;
import io.quarkiverse.web.bundler.runtime.qute.WebBundlerQuteContextRecorder;
import io.quarkiverse.web.bundler.runtime.qute.WebBundlerQuteContextRecorder.WebBundlerQuteContext;
import io.quarkiverse.web.bundler.runtime.qute.WebBundlerQuteEngineObserver;
import io.quarkiverse.web.bundler.sass.SassBuildTimeCompiler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.runtime.LaunchMode;

class WebBundlerProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerProcessor.class);
    private static final String TARGET_DIR_NAME = "web-bundler";

    @BuildStep
    void bundle(WebBundlerConfig config,
            WebDependenciesBuildItem webDependencies,
            List<EntryPointBuildItem> entryPoints,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchMode,
            OutputTargetBuildItem outputTarget) {
        if (entryPoints.isEmpty()) {
            LOGGER.info("No bundle to process");
            return;
        }
        final BundlesBuildContext bundlesBuildContext = liveReload.getContextObject(BundlesBuildContext.class);
        final boolean isLiveReload = liveReload.isLiveReload()
                && bundlesBuildContext != null
                && bundlesBuildContext.bundleDistDir() != null;
        if (isLiveReload
                && webDependencies.getDependencies().equals(bundlesBuildContext.webDependencies())
                && entryPoints.equals(bundlesBuildContext.entryPoints())
                && entryPoints.stream().map(EntryPointBuildItem::getWebAssets).flatMap(List::stream)
                        .map(WebAsset::resourceName)
                        .noneMatch(liveReload.getChangedResources()::contains)
                && Files.isDirectory(bundlesBuildContext.bundleDistDir())) {
            LOGGER.debug("Bundling not needed for live reload");
            handleBundleDistDir(generatedBundleProducer, staticResourceProducer, bundlesBuildContext.bundleDistDir(), false);
            return;
        }
        boolean hasScssChange = isLiveReload
                && liveReload.getChangedResources().stream().anyMatch(WebBundlerProcessor::isSassFile);
        final Bundler.BundleType type = Bundler.BundleType.valueOf(webDependencies.getType().toString());
        final Path targetDir = outputTarget.getOutputDirectory().resolve(TARGET_DIR_NAME);
        try {
            if (!isLiveReload) {
                FileUtil.deleteDirectory(targetDir);
            }
            Files.createDirectories(targetDir);
            LOGGER.debugf("Preparing bundle in %s", targetDir);
            final Map<String, EsBuildConfig.Loader> loaders = EsBuildConfigBuilder.getDefaultLoadersMap();
            loaders.put(".scss", EsBuildConfig.Loader.CSS);
            final BundleOptionsBuilder options = new BundleOptionsBuilder()
                    .setWorkFolder(targetDir)
                    .withDependencies(webDependencies.getDependencies())
                    .withEsConfig(new EsBuildConfigBuilder()
                            .loader(loaders)
                            .minify(launchMode.getLaunchMode().equals(LaunchMode.NORMAL)).build())
                    .withType(type);
            int addedEntryPoints = 0;
            for (EntryPointBuildItem entryPoint : entryPoints) {
                final List<Path> scriptsPath = new ArrayList<>();
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
                        scriptsPath.add(scriptPath);
                    }
                }
                String scripts = scriptsPath.stream()
                        .map(s -> String.format("  - %s", targetDir.relativize(s)))
                        .collect(
                                Collectors.joining("\n"));

                LOGGER.infof("Bundling '%s' with:\n%s", entryPoint.getEntryPointKey(), scripts);
                if (scriptsPath.size() > 0) {
                    options.addEntryPoint(entryPoint.getEntryPointKey(), scriptsPath);
                    addedEntryPoints++;
                }
            }
            if (addedEntryPoints > 0) {
                // SCSS conversion
                if (!isLiveReload || hasScssChange) {
                    try (Stream<Path> stream = Files.find(targetDir, Integer.MAX_VALUE,
                            (p, a) -> !p.toString().contains("node_modules")
                                    && isCompiledSassFile(p.getFileName().toString()))) {
                        stream.forEach(p -> convertToScss(p, targetDir));
                    }
                }
                if (isLiveReload
                        && !Objects.equals(webDependencies.getDependencies(), bundlesBuildContext.webDependencies())) {
                    Bundler.install(targetDir, webDependencies.getDependencies(), type);
                    String deps = webDependencies.getDependencies().stream().map(Path::getFileName).map(Path::toString).collect(
                            Collectors.joining(", "));
                    LOGGER.infof("%s Web dependencies changed: %s", webDependencies.getType(), deps);
                } else {
                    if (!isLiveReload) {
                        String deps = webDependencies.getDependencies().stream().map(Path::getFileName).map(Path::toString)
                                .collect(
                                        Collectors.joining(", "));
                        LOGGER.infof("%s Web dependencies detected: %s", webDependencies.getType(), deps);
                    }
                }
                final Path bundleDir = Bundler.bundle(options.build());

                if (!Files.isDirectory(bundleDir)) {
                    liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext());
                    LOGGER.error("It seems bundling didn't go well");
                    return;
                }

                LOGGER.debugf("Bundle generated in %s", bundleDir);
                handleBundleDistDir(generatedBundleProducer, staticResourceProducer, bundleDir, true);
                liveReload.setContextObject(BundlesBuildContext.class,
                        new BundlesBuildContext(webDependencies.getDependencies(), entryPoints, bundleDir));
            } else {
                liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext());
                LOGGER.debugf("No entrypoint found, no bundle generated");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void convertToScss(Path file, Path root) {
        LOGGER.debugf("Converting %s to css", file);
        String content = SassBuildTimeCompiler.convertScss(file, root, (s, s2) -> {
        });
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void handleBundleDistDir(BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, Path bundleDir, boolean changed) {
        try {
            Map<String, String> bundle = new HashMap<>();
            Files.list(bundleDir).forEach(path -> {
                final String fileName = path.getFileName().toString();
                final int dashIndex = fileName.lastIndexOf("-");
                final int extIndex = fileName.indexOf(".");
                final String key = fileName.substring(0, dashIndex) + fileName.substring(extIndex, fileName.length());
                bundle.put(key, "/static/" + fileName);
            });
            generatedBundleProducer.produce(new GeneratedBundleBuildItem(bundleDir, bundle));
            makeDirPublic(staticResourceProducer, "/static/", bundleDir, WatchMode.DISABLED, changed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    void processPublic(WebBundlerConfig config,
            PublicAssetsBuildItem staticAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            final String publicPath = webAsset.pathFromWebRoot(config.webRoot()).substring(config.publicDir().length());
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(publicPath), liveReload, webAsset);
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    void initQuteTags(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            WebBundlerQuteContextRecorder recorder,
            QuteTagsBuildItem quteTags) {
        final Map<String, String> templates = new HashMap<>();
        final List<String> tags = new ArrayList<>();
        for (WebAsset webAsset : quteTags.getWebAssets()) {
            final String tag = webAsset.filePath().get().getFileName().toString();
            final String tagName = tag.contains(".") ? tag.substring(0, tag.indexOf('.')) : tag;
            templates.put(WEB_BUNDLER_ID_PREFIX + tagName, new String(webAsset.readContentFromFile(), webAsset.charset()));
            tags.add(tagName);
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(WebBundlerQuteEngineObserver.class));
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(WebBundlerQuteContext.class)
                .supplier(recorder.createContext(tags, templates))
                .done());
        additionalBeans.produce(new AdditionalBeanBuildItem(BundleTag.class));
    }

    @BuildStep
    @Record(STATIC_INIT)
    void initBundlerBuild(BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            GeneratedBundleBuildItem generatedBundle,
            WebBundlerBuildRecorder recorder) {
        final Map<String, String> bundle = generatedBundle != null ? generatedBundle.getBundle() : Map.of();
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(WebBundlerBuild.class)
                .supplier(recorder.createContext(bundle))
                .done());
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
                webAsset.readContentFromFile(),
                liveReload.isLiveReload() && liveReload.getChangedResources().contains(webAsset.resourceName()),
                WatchMode.RESTART);
    }

    private static void makeDirPublic(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            String publicPath,
            Path bundleDir, WatchMode watchMode, boolean changed)
            throws IOException {
        Files.list(bundleDir)
                .forEach(p -> makePublic(staticResourceProducer, publicPath + p.getFileName(), p.normalize(), watchMode,
                        changed));
    }

    private static void makePublic(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, String publicPath,
            Path file, WatchMode watchMode, boolean changed) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, Collections.emptySet(), publicPath, readTemplateContent(file), changed,
                watchMode);
    }

    private void handleSassStyle(EntryPointBuildItem bundle,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            WebAsset asset) {
        LOGGER.debugf("Handling %s as a sass web asset", asset.resourceName());
    }

    private static void handleStaticResource(
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            Set<GeneratedStaticResourceBuildItem.Source> sources,
            String publicPath,
            byte[] content,
            boolean changed,
            WatchMode watchMode) {
        LOGGER.debugf("new static resource available: %s", publicPath);
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

    public record BundlesBuildContext(List<Path> webDependencies, List<EntryPointBuildItem> entryPoints,
            Path bundleDistDir) {

        public BundlesBuildContext() {
            this(List.of(), List.of(), null);
        }
    }
}
