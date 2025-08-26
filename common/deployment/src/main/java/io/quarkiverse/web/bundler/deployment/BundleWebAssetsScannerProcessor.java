package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.WebBundlerConfig.DEFAULT_ENTRY_POINT_KEY;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.addTrailingSlash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.WebBundlerConfig.EntryPointConfig;
import io.quarkiverse.web.bundler.deployment.items.*;
import io.quarkiverse.web.bundler.deployment.items.BundleWebAsset.BundleType;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem.EntryPoint;
import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem.Scanner;
import io.quarkiverse.web.bundler.deployment.util.PathUtils;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;

class BundleWebAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(BundleWebAssetsScannerProcessor.class);
    private static final String FEATURE = "web-bundler";
    public static final String TARGET_DIR_NAME = "web-bundler/";
    public static final String DIST = "dist";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    WebBundlerTargetDirBuildItem initTargetDir(OutputTargetBuildItem outputTarget, LaunchModeBuildItem launchMode) {
        final String targetDirName = TARGET_DIR_NAME + launchMode.getLaunchMode().getDefaultProfile();
        final Path targetDir = outputTarget.getOutputDirectory().resolve(targetDirName);
        final Path distDir = targetDir.resolve(DIST);
        try {
            FileUtil.deleteDirectory(targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new WebBundlerTargetDirBuildItem(targetDir, distDir);
    }

    @BuildStep
    void collect(ProjectResourcesScannerBuildItem scanner,
            BuildProducer<EntryPointBuildItem> bundles,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<BundleConfigAssetsBuildItem> bundleConfigAssets,
            WebBundlerConfig config)
            throws IOException {

        LOGGER.debug("Web Bundler scan - Bundles: start");

        final List<Scanner> bundleConfigAssetsScanners = new ArrayList<>();

        final Map<String, EntryPoint> entryPoints = new HashMap<>();

        // This is legacy support for web/app dir
        boolean appDir = checkAppDir(config);

        for (Map.Entry<String, EntryPointConfig> e : config.bundleWithDefault(appDir).entrySet()) {
            if (e.getValue().enabled()) {

                final String entryPointKey = e.getValue().effectiveKey(e.getKey());
                final String dir = e.getValue().effectiveDir(e.getKey());
                entryPoints.putIfAbsent(entryPointKey, new EntryPoint(entryPointKey, dir, new ArrayList<>()));
                // The regex is for all files but .html
                final List<WebAsset> assets = scanner.scan(dir, null,
                        dir.isEmpty() ? config.getRootEntryPointIgnoredFiles() : config.getEntryPointIgnoredFiles(),
                        config.charset());
                final Optional<WebAsset> entryPoint = assets.stream()
                        .filter(w -> w.webPath().startsWith(addTrailingSlash(dir) + "index."))
                        .findAny();
                for (WebAsset webAsset : assets) {
                    BundleType bundleType = entryPoint
                            // If it's not the entry point we consider it as a manual asset (imported by the entry point)
                            .map(ep -> webAsset.equals(ep) ? BundleType.INDEX : BundleType.MANUAL)
                            // When there is no entry point we consider it as a auto asset unless it's a sass import file (_*.sass)
                            .orElse(isImportSassFile(webAsset.webPath()) ? BundleType.MANUAL : BundleType.AUTO);
                    entryPoints.get(entryPointKey).assets().add(new BundleWebAsset(webAsset, bundleType));
                }
            }
        }

        if (entryPoints.size() == 1 && entryPoints.get(DEFAULT_ENTRY_POINT_KEY) != null
                && entryPoints.get(DEFAULT_ENTRY_POINT_KEY).assets().isEmpty()) {

            // Let's create an empty app javascript when there is nothing provided
            final String appJsResource = PathUtils.join(config.webRoot(), "app.js");
            final byte[] appJsContent = "".getBytes(StandardCharsets.UTF_8);
            generatedResourceProducer.produce(new GeneratedResourceBuildItem(appJsResource, appJsContent));
            entryPoints.get(DEFAULT_ENTRY_POINT_KEY)
                    .assets()
                    .add(new BundleWebAsset(
                            new JarResourceWebAsset("app.js", null, appJsResource, appJsContent, StandardCharsets.UTF_8),
                            BundleType.AUTO));
        }

        bundleConfigAssetsScanners.add(new Scanner("glob:tsconfig.json", config.getEffectiveIgnoredFiles(), config.charset()));

        final WebAssetsLookupDevContext context = new WebAssetsLookupDevContext(
                config,
                entryPoints,
                scanner.scan(bundleConfigAssetsScanners));
        produceWebAssets(bundles, bundleConfigAssets, context);
        LOGGER.debugf("Web Bundler scan - Bundles: %d entrypoints found", entryPoints.size());
    }

    private static boolean checkAppDir(WebBundlerConfig config) {
        AtomicBoolean appDir = new AtomicBoolean(false);
        QuarkusClassLoader.visitRuntimeResources(config.webRoot(), p -> {
            final Path appDirPath = p.getPath().resolve("app");
            if (!Files.isDirectory(appDirPath)) {
                appDir.set(false);
                return;
            }
            try (var entries = Files.list(appDirPath)) {
                final boolean app = Files.isDirectory(appDirPath) && entries.findFirst().isPresent();
                appDir.set(app);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return appDir.get();
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
                        bundles.produce(new EntryPointBuildItem(new EntryPoint(e.key(), e.dir(), webAssets)));
                    });
        }

        produceWebAssetsWithCheck(context.bundleConfigWebAssets(),
                webAssets -> bundleConfigAssets.produce(new BundleConfigAssetsBuildItem(webAssets)));

    }

    private static <T extends WebAsset> void produceWebAssetsWithCheck(List<T> e,
            Consumer<List<T>> consumer) {
        if (!e.isEmpty()) {
            consumer.accept(e);
        }
    }

    record WebAssetsLookupDevContext(WebBundlerConfig config, Map<String, EntryPoint> entryPoints,
            List<WebAsset> bundleConfigWebAssets) {

    }

}
