package io.quarkiverse.web.assets.deployment;

import static io.quarkiverse.web.assets.deployment.ProjectResourcesScanner.toWebAsset;
import static io.quarkiverse.web.assets.runtime.qute.WebAssetsQuteContextRecorder.WEB_ASSETS_ID_PREFIX;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import ch.nerdin.esbuild.Bundler;
import ch.nerdin.esbuild.modal.BundleOptionsBuilder;
import ch.nerdin.esbuild.modal.EsBuildConfigBuilder;
import io.quarkiverse.web.assets.deployment.items.BundleBuildItem;
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
import io.quarkus.runtime.LaunchMode;

class WebAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebAssetsProcessor.class);

    @BuildStep
    void processBundles(WebDependenciesBuildItem webDependencies,
            List<BundleBuildItem> bundles,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchMode) {
        if (bundles.isEmpty()) {
            LOGGER.info("No bundle to process");
            return;
        }
        final BundlesBuildContext bundlesBuildContext = liveReload.getContextObject(BundlesBuildContext.class);
        if (liveReload.isLiveReload()
                && bundlesBuildContext != null
                && bundles.equals(bundlesBuildContext.getBundles())
                && bundles.stream().map(BundleBuildItem::getWebAssets).flatMap(List::stream)
                        .map(WebAsset::getResourceName)
                        .noneMatch(liveReload.getChangedResources()::contains)
                && Files.isDirectory(bundlesBuildContext.getBundleDistDir())) {
            handleBundleDistDir(generatedBundleProducer, staticResourceProducer, bundlesBuildContext.getBundleDistDir());
            bundles.stream().map(BundleBuildItem::getWebAssets)
                    .flatMap(List::stream)
                    .forEach(p -> watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(p.getResourceName(), true)));
            return;
        }
        String deps = webDependencies.getDependencies().stream().map(Path::getFileName).map(Path::toString).collect(
                Collectors.joining(", "));
        LOGGER.infof("%s Web dependencies detected: %s", webDependencies.getType(), deps);

        final Bundler.BundleType type = Bundler.BundleType.valueOf(webDependencies.getType().toString());
        final BundleOptionsBuilder options = new BundleOptionsBuilder()
                .withDependencies(webDependencies.getDependencies())
                .withEsConfig(new EsBuildConfigBuilder()
                        .minify(launchMode.getLaunchMode().equals(LaunchMode.NORMAL)).build())
                .withType(type);
        for (BundleBuildItem bundle : bundles) {
            String scripts = bundle.getWebAssets().stream().map(WebAsset::getResourceName).map(s -> String.format("  - %s", s))
                    .collect(
                            Collectors.joining("\n"));
            LOGGER.infof("Bundling '%s' with:\n %s", bundle.getKey(), scripts);
            final List<Path> scriptsPath = bundle.getWebAssets().stream().map(WebAsset::getFilePath).collect(
                    Collectors.toList());
            bundle.getWebAssets()
                    .forEach(p -> watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(p.getResourceName(), true)));
            options.addEntryPoint(bundle.getKey(), scriptsPath);
        }
        try {
            final Path bundleDir = Bundler.bundle(options.build());
            handleBundleDistDir(generatedBundleProducer, staticResourceProducer, bundleDir);
            liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext(bundles, bundleDir));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void handleBundleDistDir(BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, Path bundleDir) {
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
            makeStaticDir(staticResourceProducer, "/bundle/", bundleDir, WatchMode.DISABLED);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    void processStatic(StaticAssetsBuildItem staticAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : staticAssets.getWebAssets()) {
            handleStaticResource(staticResourceProducer, webAsset,
                    liveReload.isLiveReload() && liveReload.getChangedResources().contains(webAsset.getResourceName()),
                    WatchMode.RESTART);
        }
    }

    @BuildStep
    void processStyles(StylesAssetsBuildItem stylesAssets,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            LiveReloadBuildItem liveReload) {
        for (WebAsset webAsset : stylesAssets.getWebAssets()) {
            // TODO deal with scss
            handleStaticResource(staticResourceProducer, webAsset,
                    liveReload.isLiveReload() && liveReload.getChangedResources().contains(webAsset.getResourceName()),
                    WatchMode.RESTART);
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
            final String tag = webAsset.getFilePath().getFileName().toString();
            final String tagName = tag.contains(".") ? tag.substring(0, tag.indexOf('.')) : tag;
            templates.put(WEB_ASSETS_ID_PREFIX + tagName, new String(webAsset.getContent(), webAsset.getCharset()));
            tags.add(tagName);
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(WebAssetsQuteEngineObserver.class));
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(WebAssetsQuteContext.class)
                .supplier(recorder.createContext(tags, templates, generatedBundle.getBundle()))
                .done());

    }

    private static void makeStaticDir(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            String resourcePath,
            Path bundleDir, WatchMode watchMode)
            throws IOException {
        Files.list(bundleDir)
                .forEach(p -> makeStatic(staticResourceProducer, resourcePath + p.getFileName(), p.normalize(), watchMode));
    }

    private static void makeStatic(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer, String resourceName,
            Path file, WatchMode watchMode) {
        if (!Files.exists(file)) {
            return;
        }
        handleStaticResource(staticResourceProducer, toWebAsset(resourceName, file, Charset.defaultCharset()), true, watchMode);
    }

    private void handleQuteTag(BundleBuildItem bundle,
            WebAsset resource) {
        LOGGER.debugf("Handling %s as a qute tag web asset", resource.getResourceName());
    }

    private void handleSassStyle(BundleBuildItem bundle, BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            WebAsset asset) {
        LOGGER.debugf("Handling %s as a sass web asset", asset.getResourceName());
    }

    private static void handleStaticResource(BuildProducer<GeneratedStaticResourceBuildItem> staticResourceProducer,
            WebAsset resource, boolean changed, WatchMode watchMode) {
        LOGGER.debugf("%s is public", resource.getResourceName());
        staticResourceProducer.produce(new GeneratedStaticResourceBuildItem(
                Set.of(new GeneratedStaticResourceBuildItem.Source(resource.getResourceName(), resource.getFilePath())),
                resource.getResourceName(),
                resource.getContent(),
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

        private final List<BundleBuildItem> bundles;
        private final Path bundleDistDir;

        public BundlesBuildContext(List<BundleBuildItem> bundles, Path bundleDistDir) {
            this.bundles = bundles;
            this.bundleDistDir = bundleDistDir;
        }

        public List<BundleBuildItem> getBundles() {
            return bundles;
        }

        public Path getBundleDistDir() {
            return bundleDistDir;
        }
    }
}
