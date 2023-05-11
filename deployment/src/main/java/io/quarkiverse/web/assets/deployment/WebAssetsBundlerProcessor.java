package io.quarkiverse.web.assets.deployment;

import static io.quarkiverse.web.assets.deployment.ProjectResourcesScanner.readTemplateContent;
import static io.quarkiverse.web.assets.deployment.items.BundleWebAsset.BundleType.MANUAL;
import static io.quarkiverse.web.assets.runtime.qute.WebAssetsQuteContextRecorder.WEB_ASSETS_ID_PREFIX;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import ch.nerdin.esbuild.Bundler;
import ch.nerdin.esbuild.modal.BundleOptionsBuilder;
import ch.nerdin.esbuild.modal.EsBuildConfig;
import ch.nerdin.esbuild.modal.EsBuildConfigBuilder;
import io.quarkiverse.web.assets.deployment.items.BundleWebAsset;
import io.quarkiverse.web.assets.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.assets.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.assets.deployment.items.QuteTagsBuildItem;
import io.quarkiverse.web.assets.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.assets.deployment.items.StylesAssetsBuildItem;
import io.quarkiverse.web.assets.deployment.items.WebAsset;
import io.quarkiverse.web.assets.deployment.items.WebDependenciesBuildItem;
import io.quarkiverse.web.assets.deployment.staticresources.GeneratedStaticResourceBuildItem;
import io.quarkiverse.web.assets.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode;
import io.quarkiverse.web.assets.runtime.qute.WebAssetsQuteContextRecorder;
import io.quarkiverse.web.assets.runtime.qute.WebAssetsQuteContextRecorder.WebAssetsQuteContext;
import io.quarkiverse.web.assets.runtime.qute.WebAssetsQuteEngineObserver;
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

class WebAssetsBundlerProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebAssetsBundlerProcessor.class);
    private static final String TARGET_DIR_NAME = "web-assets";

    @BuildStep
    void processBundles(WebAssetsConfig config,
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
        if (liveReload.isLiveReload()
                && bundlesBuildContext != null
                && entryPoints.equals(bundlesBuildContext.getEntryPoints())
                && entryPoints.stream().map(EntryPointBuildItem::getWebAssets).flatMap(List::stream)
                        .map(WebAsset::getResourceName)
                        .noneMatch(liveReload.getChangedResources()::contains)
                && Files.isDirectory(bundlesBuildContext.getBundleDistDir())) {
            LOGGER.debug("Bundling not needed for live reload");
            handleBundleDistDir(generatedBundleProducer, staticResourceProducer, bundlesBuildContext.getBundleDistDir(), false);
            //TODO remove for HotDeploymentWatchedFileBuildItem.builder()
            entryPoints.stream().map(EntryPointBuildItem::getWebAssets)
                    .flatMap(List::stream)
                    .forEach(p -> watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(p.getResourceName(), true)));

            return;
        }
        String deps = webDependencies.getDependencies().stream().map(Path::getFileName).map(Path::toString).collect(
                Collectors.joining(", "));
        LOGGER.infof("%s Web dependencies detected: %s", webDependencies.getType(), deps);

        final Bundler.BundleType type = Bundler.BundleType.valueOf(webDependencies.getType().toString());
        final Path targetDir = outputTarget.getOutputDirectory().resolve(TARGET_DIR_NAME);
        try {
            Files.createDirectories(targetDir);
            LOGGER.debugf("Preparing bundle in %s", targetDir);
            FileUtil.deleteDirectory(targetDir);
            final Map<String, EsBuildConfig.Loader> loaders = EsBuildConfigBuilder.getDefaultLoadersMap();
            loaders.put(".scss", EsBuildConfig.Loader.CSS);
            final BundleOptionsBuilder options = new BundleOptionsBuilder()
                    .setWorkFolder(targetDir)
                    .withDependencies(webDependencies.getDependencies())
                    .withEsConfig(new EsBuildConfigBuilder()
                            .loader(loaders)
                            .minify(launchMode.getLaunchMode().equals(LaunchMode.NORMAL)).build())
                    .withType(type);
            for (EntryPointBuildItem entryPoint : entryPoints) {
                final List<Path> scriptsPath = new ArrayList<>();
                for (BundleWebAsset webAsset : entryPoint.getWebAssets()) {
                    String destination = webAsset.pathFromWebRoot(config.webRoot());
                    final Path scriptPath = targetDir.resolve(destination);
                    Files.createDirectories(scriptPath.getParent());
                    // TODO convert scss to css
                    // and replace the imports
                    if (webAsset.hasContent()) {
                        Files.write(scriptPath, webAsset.getContent(), StandardOpenOption.CREATE);
                    } else {
                        Files.copy(webAsset.getFilePath().orElseThrow(), scriptPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (!webAsset.type().equals(MANUAL)) {
                        scriptsPath.add(scriptPath);
                    }
                }
                String scripts = scriptsPath.stream()
                        .map(s -> String.format("  - %s", targetDir.relativize(s)))
                        .collect(
                                Collectors.joining("\n"));

                LOGGER.infof("Bundling '%s' with:\n%s", entryPoint.getEntryPointKey(), scripts);
                entryPoint.getWebAssets()
                        .forEach(p -> watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(p.getResourceName(), true)));
                options.addEntryPoint(entryPoint.getEntryPointKey(), scriptsPath);
            }
            //SCSS conversion
            try (Stream<Path> stream = Files.find(targetDir, Integer.MAX_VALUE,
                    (p, a) -> isCompiledSassFile(p.getFileName().toString()))) {
                stream.forEach(p -> ScssConverter.convertToScss(p, targetDir));
            }

            final Path bundleDir = Bundler.bundle(options.build());

            LOGGER.debugf("Bundle generated in %s", bundleDir);
            handleBundleDistDir(generatedBundleProducer, staticResourceProducer, bundleDir, true);
            liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext(entryPoints, bundleDir));
        } catch (IOException e) {
            throw new RuntimeException(e);
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
                bundle.put(key, fileName);
            });
            generatedBundleProducer.produce(new GeneratedBundleBuildItem(bundleDir, bundle));
            makeStaticDir(staticResourceProducer, "/static/", bundleDir, WatchMode.DISABLED, changed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    void processStatic(WebAssetsConfig config,
            StaticAssetsBuildItem staticAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            makeWebAssetStatic(config, staticResourceProducer, liveReload, webAsset);
        }
    }

    @BuildStep
    void processStyles(WebAssetsConfig config,
            StylesAssetsBuildItem stylesAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : stylesAssets.getWebAssets()) {
            // TODO deal with scss
            makeWebAssetStatic(config, staticResourceProducer, liveReload, webAsset);
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    void processQuteTags(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            WebAssetsQuteContextRecorder recorder,
            QuteTagsBuildItem quteTags,
            GeneratedBundleBuildItem generatedBundle,
            LiveReloadBuildItem liveReload) {
        final Map<String, String> templates = new HashMap<>();
        final List<String> tags = new ArrayList<>();
        for (WebAsset webAsset : quteTags.getWebAssets()) {
            final String tag = webAsset.getFilePath().get().getFileName().toString();
            final String tagName = tag.contains(".") ? tag.substring(0, tag.indexOf('.')) : tag;
            templates.put(WEB_ASSETS_ID_PREFIX + tagName, new String(webAsset.readContentFromFile(), webAsset.getCharset()));
            tags.add(tagName);
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(WebAssetsQuteEngineObserver.class));
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(WebAssetsQuteContext.class)
                .supplier(recorder.createContext(tags, templates, generatedBundle.getBundle()))
                .done());

    }

    private static void makeWebAssetStatic(
            WebAssetsConfig config,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload,
            WebAsset webAsset) {
        handleStaticResource(staticResourceProducer,
                Set.of(new GeneratedStaticResourceBuildItem.Source(webAsset.getResourceName(), webAsset.getFilePath())),
                webAsset.pathFromWebRoot(config.webRoot()),
                webAsset.readContentFromFile(),
                liveReload.isLiveReload() && liveReload.getChangedResources().contains(webAsset.getResourceName()),
                WatchMode.RESTART);
    }

    private static void makeStaticDir(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            String publicPath,
            Path bundleDir, WatchMode watchMode, boolean changed)
            throws IOException {
        Files.list(bundleDir)
                .forEach(p -> makeStatic(staticResourceProducer, publicPath + p.getFileName(), p.normalize(), watchMode,
                        changed));
    }

    private static void makeStatic(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, String publicPath,
            Path file, WatchMode watchMode, boolean changed) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, Collections.emptySet(), publicPath, readTemplateContent(file), changed,
                watchMode);
    }

    private void handleQuteTag(EntryPointBuildItem bundle,
            WebAsset resource) {
        LOGGER.debugf("Handling %s as a qute tag web asset", resource.getResourceName());
    }

    private void handleSassStyle(EntryPointBuildItem bundle,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            WebAsset asset) {
        LOGGER.debugf("Handling %s as a sass web asset", asset.getResourceName());
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
        if (filename.startsWith("_")) {
            return false;
        }
        String lc = filename.toLowerCase();
        return lc.endsWith(".scss") || lc.endsWith(".sass");
    }

    public static class BundlesBuildContext {

        private final List<EntryPointBuildItem> bundles;
        private final Path bundleDistDir;

        public BundlesBuildContext(List<EntryPointBuildItem> bundles, Path bundleDistDir) {
            this.bundles = bundles;
            this.bundleDistDir = bundleDistDir;
        }

        public List<EntryPointBuildItem> getEntryPoints() {
            return bundles;
        }

        public Path getBundleDistDir() {
            return bundleDistDir;
        }
    }
}
