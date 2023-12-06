package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.addTrailingSlash;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.ProjectResourcesScanner.Scanner;
import io.quarkiverse.web.bundler.deployment.WebBundlerConfig.EntryPointConfig;
import io.quarkiverse.web.bundler.deployment.items.BundleConfigAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.HtmlTemplatesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTagsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;

class WebAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebAssetsScannerProcessor.class);

    private static final String FEATURE = "web-bundler";
    public static final String MAIN_ENTRYPOINT_KEY = "main";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void collect(ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<EntryPointBuildItem> bundles,
            BuildProducer<StaticAssetsBuildItem> staticAssets,
            BuildProducer<QuteTagsBuildItem> quteTagsAssets,
            BuildProducer<BundleConfigAssetsBuildItem> bundleConfigAssets,
            BuildProducer<HtmlTemplatesBuildItem> htmlTemplatesAssets,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            WebBundlerConfig config,
            LiveReloadBuildItem liveReload)
            throws IOException {
        watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                .setLocationPredicate(s -> s.startsWith(config.webRoot())).setRestartNeeded(true).build());

        final WebAssetsLookupDevContext devContext = liveReload.getContextObject(WebAssetsLookupDevContext.class);
        if (liveReload.isLiveReload()
                && devContext != null
                && !hasNewWebResources(config, liveReload, devContext)) {
            // Project WebAssets shouldn't be changed even if the file is changed as content is not stored
            // WebAsset from dependencies means we should do a new scan
            LOGGER.debug("Web bundler scan not needed for live reload");
            produceWebAssets(bundles, staticAssets, quteTagsAssets, bundleConfigAssets, htmlTemplatesAssets, devContext, true);
            return;
        }
        LOGGER.debug("Web bundler scan started");
        Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        List<ResolvedDependency> extensionArtifacts = curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(Dependency::isRuntimeExtensionArtifact).collect(Collectors.toList());
        Map<String, EntryPointConfig> entryPointsConfig = new HashMap<>(config.bundle());
        final List<Scanner> staticAssetsScanners = new ArrayList<>();
        final List<Scanner> htmlTemplateAssetsScanner = new ArrayList<>();
        final List<Scanner> quteTagsAssetsScanners = new ArrayList<>();
        final List<Scanner> bundleConfigAssetsScanners = new ArrayList<>();

        if (!config.bundle().containsKey("app")) {
            entryPointsConfig.put("app", new ConfiguredEntryPoint("app", "app", MAIN_ENTRYPOINT_KEY));
        }

        final EntryPointConfig componentsEntryPoint = config.bundle().get("qute-components");
        if (componentsEntryPoint != null && componentsEntryPoint.enabled()) {
            quteTagsAssetsScanners.add(new Scanner(config.fromWebRoot(componentsEntryPoint.effectiveDir("qute-components")),
                    "glob:**.html", config.charset()));
        }

        final ProjectResourcesScanner resourcesScanner = new ProjectResourcesScanner(allApplicationArchives,
                extensionArtifacts);

        htmlTemplateAssetsScanner.add(new Scanner(config.webRoot(),
                "glob:*.html", config.charset()));

        staticAssetsScanners.add(new Scanner(config.fromWebRoot(config.staticDir()),
                "glob:**", config.charset()));

        final Map<String, List<BundleWebAsset>> bundleAssets = new HashMap<>();
        for (Map.Entry<String, EntryPointConfig> e : entryPointsConfig.entrySet()) {
            if (e.getValue().enabled()) {
                final String entryPointKey = e.getValue().effectiveKey(e.getKey());
                bundleAssets.putIfAbsent(entryPointKey, new ArrayList<>());
                final String dirFromWebRoot = config.fromWebRoot(e.getValue().effectiveDir(e.getKey()));
                // The regex is for all files but .html
                final List<WebAsset> assets = resourcesScanner.scan(dirFromWebRoot, "regex:^(.(?!\\.html$))*$",
                        config.charset());
                final Optional<WebAsset> entryPoint = assets.stream()
                        .filter(w -> w.resourceName().startsWith(addTrailingSlash(dirFromWebRoot) + "index."))
                        .findAny();
                for (WebAsset webAsset : assets) {
                    BundleType bundleType = entryPoint
                            .map(ep -> webAsset.equals(ep) ? BundleType.ENTRYPOINT : BundleType.MANUAL)
                            .orElse(BundleType.AUTO);
                    bundleAssets.get(entryPointKey).add(new BundleWebAsset(webAsset, bundleType));
                }
            }
        }

        bundleConfigAssetsScanners.add(new Scanner(config.webRoot(), "glob:tsconfig.json", config.charset()));

        final WebAssetsLookupDevContext context = new WebAssetsLookupDevContext(
                bundleAssets,
                resourcesScanner.scan(staticAssetsScanners),
                resourcesScanner.scan(quteTagsAssetsScanners),
                resourcesScanner.scan(bundleConfigAssetsScanners),
                resourcesScanner.scan(htmlTemplateAssetsScanner));
        produceWebAssets(bundles, staticAssets, quteTagsAssets, bundleConfigAssets, htmlTemplatesAssets, context, false);
        liveReload.setContextObject(WebAssetsLookupDevContext.class, context);
    }

    private static boolean hasNewWebResources(WebBundlerConfig config, LiveReloadBuildItem liveReload,
            WebAssetsLookupDevContext devContext) {
        final Set<String> webAssets = devContext.allWebAssets().stream()
                .map(WebAsset::resourceName)
                .collect(Collectors.toSet());
        // Check that all the changed resource are already in the web assets
        // If one is not then it's a new file
        return !liveReload.getChangedResources().stream().filter(c -> c.startsWith(config.webRoot()))
                .allMatch(webAssets::contains);
    }

    void produceWebAssets(BuildProducer<EntryPointBuildItem> bundles,
            BuildProducer<StaticAssetsBuildItem> staticAssets,
            BuildProducer<QuteTagsBuildItem> quteTagsAssets,
            BuildProducer<BundleConfigAssetsBuildItem> bundleConfigAssets,
            BuildProducer<HtmlTemplatesBuildItem> htmlTemplatesAssets,
            WebAssetsLookupDevContext context,
            boolean checkIfExists) {
        for (Map.Entry<String, List<BundleWebAsset>> e : context.bundleAssets().entrySet()) {
            bundles.produce(new EntryPointBuildItem(e.getKey(), checkIfExists ? checkWebAssets(e.getValue()) : e.getValue()));
        }
        staticAssets.produce(new StaticAssetsBuildItem(
                checkIfExists ? checkWebAssets(context.staticWebAssets()) : context.staticWebAssets()));

        bundleConfigAssets.produce(new BundleConfigAssetsBuildItem(
                checkIfExists ? checkWebAssets(context.bundleConfigWebAssets()) : context.bundleConfigWebAssets()));

        quteTagsAssets.produce(new QuteTagsBuildItem(
                checkIfExists ? checkWebAssets(context.quteWebAssets()) : context.quteWebAssets()));

        htmlTemplatesAssets.produce(new HtmlTemplatesBuildItem(
                checkIfExists ? checkWebAssets(context.htmlTemplateWebAssets()) : context.htmlTemplateWebAssets()));

    }

    private static <T extends WebAsset> List<T> checkWebAssets(List<T> webAssets) {
        return webAssets.stream().filter(w -> w.filePath().isPresent() && Files.isRegularFile(w.filePath().get()))
                .collect(
                        Collectors.toList());
    }

    record WebAssetsLookupDevContext(Map<String, List<BundleWebAsset>> bundleAssets, List<WebAsset> staticWebAssets,
            List<WebAsset> quteWebAssets, List<WebAsset> bundleConfigWebAssets, List<WebAsset> htmlTemplateWebAssets) {

        public List<WebAsset> allWebAssets() {
            final ArrayList<WebAsset> all = new ArrayList<>();
            all.addAll(staticWebAssets);
            all.addAll(quteWebAssets);
            all.addAll(bundleConfigWebAssets);
            all.addAll(htmlTemplateWebAssets);
            bundleAssets.values().forEach(all::addAll);
            return all;
        }
    }

}
