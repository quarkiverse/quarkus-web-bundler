package io.quarkiverse.web.assets.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.assets.deployment.ProjectResourcesScanner.Scanner;
import io.quarkiverse.web.assets.deployment.WebAssetsConfig.BundleConfig;
import io.quarkiverse.web.assets.deployment.items.BundleBuildItem;
import io.quarkiverse.web.assets.deployment.items.QuteTagsBuildItem;
import io.quarkiverse.web.assets.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.assets.deployment.items.StylesAssetsBuildItem;
import io.quarkiverse.web.assets.deployment.items.WebAsset;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;

class WebAssetsLookupProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebAssetsLookupProcessor.class);

    private static final String FEATURE = "web-assets";
    public static final String MAIN_BUNDLE_KEY = "main";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void collect(ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<BundleBuildItem> bundles,
            BuildProducer<StaticAssetsBuildItem> staticAssets,
            BuildProducer<StylesAssetsBuildItem> styleAssets,
            BuildProducer<QuteTagsBuildItem> quteTagsAssets,
            WebAssetsConfig config,
            LiveReloadBuildItem liveReload)
            throws IOException {

        final WebAssetsLookupDevContext devContext = liveReload.getContextObject(WebAssetsLookupDevContext.class);
        if (liveReload.isLiveReload() && devContext != null) {
            // TODO: restart the scan when there are new files
            produceWebAssets(bundles, staticAssets, styleAssets, quteTagsAssets, devContext, true);
            return;
        }

        Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        List<ResolvedDependency> extensionArtifacts = curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(Dependency::isRuntimeExtensionArtifact).collect(Collectors.toList());
        Map<String, BundleConfig> bundlesConfig = new HashMap<>(config.bundles());
        final List<Scanner> staticAssetsScanners = new ArrayList<>();
        final List<Scanner> stylesAssetsScanners = new ArrayList<>();
        final List<Scanner> quteTagsAssetsScanners = new ArrayList<>();

        if (config.presets().components().enabled()) {
            //TODO handle scss
            bundlesConfig.put("components",
                    new ConfiguredBundle("web/components", config.presets().components().bundleKey().orElse(MAIN_BUNDLE_KEY),
                            "**/*.{css,js,jsx}"));
            quteTagsAssetsScanners.add(new Scanner("web/components", Globs.QUTE_TAGS.glob(), config.charset()));
        }
        if (config.presets().app().enabled()) {
            //TODO handle scss
            bundlesConfig.put("app",
                    new ConfiguredBundle("web/app", config.presets().app().bundleKey().orElse(MAIN_BUNDLE_KEY),
                            "**/*.{css,js,jsx}"));
        }

        if (config.staticAssets().enabled()) {
            staticAssetsScanners.add(new Scanner(config.staticAssets().dir().orElse("web/static"),
                    config.staticAssets().glob().orElse(Globs.ALL.glob()), config.charset()));
        }
        if (config.styles().enabled()) {
            stylesAssetsScanners.add(new Scanner(config.staticAssets().dir().orElse("web/styles"),
                    config.staticAssets().glob().orElse(Globs.STYLES.glob()), config.charset()));
        }
        final ProjectResourcesScanner resourcesScanner = new ProjectResourcesScanner(allApplicationArchives,
                extensionArtifacts);
        final Map<String, List<WebAsset>> bundleAssets = new HashMap<>();
        for (Map.Entry<String, BundleConfig> e : bundlesConfig.entrySet()) {
            String key = e.getValue().key().orElse(e.getKey());
            bundleAssets.putIfAbsent(key, new ArrayList<>());
            bundleAssets.get(key).addAll(
                    resourcesScanner.scan(e.getValue().dir().orElse(e.getKey()), e.getValue().glob(), config.charset()));
        }
        final WebAssetsLookupDevContext context = new WebAssetsLookupDevContext(bundleAssets,
                resourcesScanner.scan(staticAssetsScanners),
                resourcesScanner.scan(stylesAssetsScanners), resourcesScanner.scan(quteTagsAssetsScanners));
        produceWebAssets(bundles, staticAssets, styleAssets, quteTagsAssets, context, false);
        liveReload.setContextObject(WebAssetsLookupDevContext.class, context);
    }

    void produceWebAssets(BuildProducer<BundleBuildItem> bundles, BuildProducer<StaticAssetsBuildItem> staticAssets,
            BuildProducer<StylesAssetsBuildItem> styleAssets, BuildProducer<QuteTagsBuildItem> quteTagsAssets,
            WebAssetsLookupDevContext context, boolean checkIfExists) {
        for (Map.Entry<String, List<WebAsset>> e : context.getBundleAssets().entrySet()) {
            bundles.produce(new BundleBuildItem(e.getKey(), checkIfExists ? checkWebAssets(e.getValue()) : e.getValue()));
        }

        staticAssets.produce(new StaticAssetsBuildItem(
                checkIfExists ? checkWebAssets(context.getStaticWebAssets()) : context.getStaticWebAssets()));

        styleAssets.produce(new StylesAssetsBuildItem(
                checkIfExists ? checkWebAssets(context.getStylesWebAssets()) : context.getStylesWebAssets()));

        quteTagsAssets.produce(new QuteTagsBuildItem(
                checkIfExists ? checkWebAssets(context.getQuteWebAssets()) : context.getQuteWebAssets()));

    }

    private static List<WebAsset> checkWebAssets(List<WebAsset> webAssets) {
        return webAssets.stream().filter(w -> Files.isRegularFile(w.getFilePath())).collect(
                Collectors.toList());
    }

    static class WebAssetsLookupDevContext {

        private final Map<String, List<WebAsset>> bundleAssets;
        private final List<WebAsset> staticWebAssets;
        private final List<WebAsset> stylesWebAssets;

        private final List<WebAsset> quteWebAssets;

        WebAssetsLookupDevContext(Map<String, List<WebAsset>> bundleAssets, List<WebAsset> staticWebAssets,
                List<WebAsset> stylesWebAssets, List<WebAsset> quteWebAssets) {
            this.bundleAssets = bundleAssets;
            this.staticWebAssets = staticWebAssets;
            this.stylesWebAssets = stylesWebAssets;
            this.quteWebAssets = quteWebAssets;
        }

        public Map<String, List<WebAsset>> getBundleAssets() {
            return bundleAssets;
        }

        public List<WebAsset> getStaticWebAssets() {
            return staticWebAssets;
        }

        public List<WebAsset> getStylesWebAssets() {
            return stylesWebAssets;
        }

        public List<WebAsset> getQuteWebAssets() {
            return quteWebAssets;
        }
    }

}
