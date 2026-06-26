package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig.DEFAULT_ENTRY_POINT_KEY;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.tools.projectscanner.ClasspathProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig.EntryPointConfig;
import io.quarkiverse.web.bundler.deployment.items.BundleConfigAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType;
import io.quarkiverse.web.bundler.deployment.items.DevWatcherHistoryBuildItem;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem.EntryPoint;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;

class BundleWebAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(BundleWebAssetsScannerProcessor.class);

    public static final String TARGET_DIR_NAME = "web-bundler/";
    public static final String DIST = "dist";

    @BuildStep(onlyIfNot = IsDevelopment.class)
    WebBundlerTargetDirBuildItem initTargetDirProd(OutputTargetBuildItem outputTarget, LaunchModeBuildItem launchMode) {
        return getWebBundlerTargetDirBuildItem(outputTarget, launchMode, false);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    WebBundlerTargetDirBuildItem initTargetDirDev(OutputTargetBuildItem outputTarget,
            LaunchModeBuildItem launchMode,
            DevWatcherHistoryBuildItem watcher, LiveReloadBuildItem liveReload) {
        final boolean keepDir = liveReload.isLiveReload()
                // create or delete = re-bundle
                && watcher != null
                && !watcher.detectedConfigChange()
                && !watcher.detectedAddOrRemoveChanges()
                // Probably a user initiated reload = re-bundle
                && !liveReload.getChangedResources().isEmpty();
        return getWebBundlerTargetDirBuildItem(outputTarget, launchMode, keepDir);
    }

    private static WebBundlerTargetDirBuildItem getWebBundlerTargetDirBuildItem(OutputTargetBuildItem outputTarget,
            LaunchModeBuildItem launchMode, boolean keepDir) {
        final String targetDirName = TARGET_DIR_NAME + launchMode.getLaunchMode().getDefaultProfile();
        final Path targetDir = outputTarget.getOutputDirectory().resolve(targetDirName);
        final Path distDir = targetDir.resolve(DIST);
        if (!keepDir) {
            try {
                FileUtil.deleteDirectory(targetDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new WebBundlerTargetDirBuildItem(targetDir, distDir, keepDir && Files.isDirectory(distDir));
    }

    @BuildStep
    void collect(ProjectScannerBuildItem scanner,
            BuildProducer<EntryPointBuildItem> bundles,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<BundleConfigAssetsBuildItem> bundleConfigAssets,
            WebBundlerConfig config)
            throws IOException {
        LOGGER.debug("Web Bundler scan - Bundles: start");
        final Map<String, EntryPoint> entryPoints = new TreeMap<>();

        final Map<String, EntryPointConfig> entryPointConfigs = config.bundleWithDefault();

        // Collect all entry point directory names for root-scan exclusion
        final List<String> allEntryPointDirs = new ArrayList<>();
        for (Map.Entry<String, EntryPointConfig> e : entryPointConfigs.entrySet()) {
            if (e.getValue().enabled()) {
                allEntryPointDirs.add(e.getValue().effectiveDir(e.getKey()));
            }
        }

        for (Map.Entry<String, EntryPointConfig> e : entryPointConfigs.entrySet()) {
            if (e.getValue().enabled()) {

                final String entryPointKey = e.getValue().effectiveKey(e.getKey());
                final String dir = e.getValue().effectiveDir(e.getKey());
                entryPoints.putIfAbsent(entryPointKey,
                        new EntryPoint(entryPointKey, dir, e.getValue().output(), new ArrayList<>()));

                final String fullDir = config.prefixWithWebRoot(dir);
                final List<ProjectFile> assets = scanner.query()
                        .scopeDirs(fullDir)
                        .addExcluded(config.ignoredFilesOrEmpty())
                        .addExcluded(List.of("glob:**.html"))
                        .list();

                // For the default "app" entry point, also collect loose root-level files
                // (only once for the canonical 'app' entry, not for other dirs merged into key 'app')
                final List<ProjectFile> allAssets;
                if (DEFAULT_ENTRY_POINT_KEY.equals(entryPointKey) && DEFAULT_ENTRY_POINT_KEY.equals(e.getKey())) {
                    List<String> rootExclusions = new ArrayList<>(
                            List.of("glob:templates/**", "glob:public/**", "glob:static/**",
                                    "glob:**.html", "glob:tsconfig.json"));
                    for (String epDir : allEntryPointDirs) {
                        rootExclusions.add("glob:" + epDir + "/**");
                    }
                    final List<ProjectFile> rootAssets = scanner.query()
                            .scopeDirs(config.webRoot())
                            .addExcluded(config.ignoredFilesOrEmpty())
                            .addExcluded(rootExclusions)
                            .list();
                    allAssets = new ArrayList<>(assets.size() + rootAssets.size());
                    allAssets.addAll(assets);
                    allAssets.addAll(rootAssets);
                } else {
                    allAssets = assets;
                }

                // Prefer index.* from the entry point dir, fall back to root
                final Optional<ProjectFile> entryPoint = assets.stream()
                        .filter(w -> w.scopedPath().startsWith("index."))
                        .findAny()
                        .or(() -> allAssets.stream()
                                .filter(w -> w.scopedPath().startsWith("index."))
                                .findAny());

                for (ProjectFile webAsset : allAssets) {
                    BundleType bundleType = entryPoint
                            .map(ep -> webAsset.equals(ep) ? BundleType.INDEX : BundleType.MANUAL)
                            .orElse(isImportSassFile(webAsset.scopedPath()) ? BundleType.MANUAL : BundleType.AUTO);
                    entryPoints.get(entryPointKey).assets().add(new BundleWebAsset(webAsset, bundleType));
                }
            }
        }

        if (entryPoints.size() == 1 && entryPoints.get(DEFAULT_ENTRY_POINT_KEY) != null
                && entryPoints.get(DEFAULT_ENTRY_POINT_KEY).assets().isEmpty()) {

            final String appJsResource = StringPaths.join(config.webRoot(), "app.js");
            final byte[] appJsContent = "".getBytes(StandardCharsets.UTF_8);
            generatedResourceProducer.produce(new GeneratedResourceBuildItem(appJsResource, appJsContent));
            entryPoints.get(DEFAULT_ENTRY_POINT_KEY)
                    .assets()
                    .add(new BundleWebAsset(
                            new ClasspathProjectFile(appJsResource, "app.js", null,
                                    ProjectFile.Origin.DEPENDENCY_RESOURCE, appJsResource, appJsContent,
                                    StandardCharsets.UTF_8),
                            BundleType.AUTO));
        }

        final List<ProjectFile> bundleConfigWebAssets = scanner.query()
                .scopeDirs(config.webRoot())
                .matchingGlob("tsconfig.json")
                .list();
        final WebAssetsLookupDevContext context = new WebAssetsLookupDevContext(
                config,
                entryPoints,
                bundleConfigWebAssets);
        produceWebAssets(bundles, bundleConfigAssets, context);
        LOGGER.debugf("Web Bundler scan - Bundles: %d entrypoints found", entryPoints.size());
    }

    private static boolean isImportSassFile(String resourceName) {
        final String fileName = Path.of(resourceName).getFileName().toString();
        return fileName.startsWith("_") && isSassFile(fileName);
    }

    private static boolean isSassFile(String fileName) {
        String lc = fileName.toLowerCase();
        return lc.endsWith(".scss") || lc.endsWith(".sass");
    }

    void produceWebAssets(BuildProducer<EntryPointBuildItem> bundles,
            BuildProducer<BundleConfigAssetsBuildItem> bundleConfigAssets,
            WebAssetsLookupDevContext context) {
        for (EntryPoint e : context.entryPoints().values()) {
            produceWebAssetsWithCheck(e.assets(),
                    webAssets -> {
                        bundles.produce(new EntryPointBuildItem(new EntryPoint(e.key(), e.dir(), e.output(), webAssets)));
                    });
        }

        produceWebAssetsWithCheck(context.bundleConfigWebAssets(),
                webAssets -> bundleConfigAssets.produce(new BundleConfigAssetsBuildItem(webAssets)));

    }

    private static <T extends ProjectFile> void produceWebAssetsWithCheck(List<T> e,
            Consumer<List<T>> consumer) {
        if (!e.isEmpty()) {
            consumer.accept(e);
        }
    }

    record WebAssetsLookupDevContext(WebBundlerConfig config, Map<String, EntryPoint> entryPoints,
            List<ProjectFile> bundleConfigWebAssets) {

    }

}
