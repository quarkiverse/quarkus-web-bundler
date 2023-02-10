package io.quarkiverse.web.assets.deployment;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.assets.deployment.WebAssetsConfig.BundleConfig;
import io.quarkiverse.web.assets.deployment.staticresources.StaticResourceBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;

class WebAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebAssetsProcessor.class);

    private static final String FEATURE = "web-assets";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void collectAssets(ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<WebAssetBuildItem> assetPaths,
            WebAssetsConfig config)
            throws IOException {
        Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        List<ResolvedDependency> extensionArtifacts = curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(Dependency::isRuntimeExtensionArtifact).collect(Collectors.toList());
        Map<String, BundleConfig> bundles = new HashMap<>(config.bundles());
        if (config.presetComponents()) {
            bundles.putAll(ComponentBundles.COMPONENT_BUNDLES);
        }
        if (config.presetAssets()) {
            bundles.putAll(AssetsBundles.ASSETS_BUNDLES);
        }
        for (Map.Entry<String, BundleConfig> e : bundles.entrySet()) {
            findBundleAssets(config, e.getKey(), e.getValue(), assetPaths,
                    allApplicationArchives,
                    extensionArtifacts);
        }

    }

    @BuildStep
    void processAssets(List<WebAssetBuildItem> webAssets,
            BuildProducer<StaticResourceBuildItem> staticResourceProducer) {
        for (WebAssetBuildItem webAsset : webAssets) {
            staticResourceProducer.produce(new StaticResourceBuildItem(
                    Set.of(new StaticResourceBuildItem.Source(webAsset.getPath(), webAsset.getFullPath())),
                    webAsset.getPath(),
                    webAsset.getContent(),
                    true,
                    true));
        }
    }

    private void findBundleAssets(WebAssetsConfig config,
            String bundleName,
            BundleConfig bundleConfig,
            BuildProducer<WebAssetBuildItem> webAssetsProducer,
            Set<ApplicationArchive> allApplicationArchives,
            List<ResolvedDependency> extensionArtifacts) throws IOException {
        final String bundleDir = bundleConfig.dir();
        for (ResolvedDependency artifact : extensionArtifacts) {
            if (isApplicationArchive(artifact, allApplicationArchives)) {
                // Skip extension archives that are also application archives
                continue;
            }
            for (Path path : artifact.getResolvedPaths()) {
                if (Files.isDirectory(path)) {
                    final Path bundleDirPath = path.resolve(bundleDir);
                    if (Files.isDirectory(bundleDirPath) && bundleDirPath.toString().endsWith(bundleDir)) {
                        LOGGER.debugf("Found extension assets dir: %s", bundleDirPath);
                        scan(webAssetsProducer, bundleConfig, bundleName, path, bundleDirPath);
                        break;
                    }
                } else {
                    try (FileSystem artifactFs = ZipUtils.newFileSystem(path)) {
                        Path bundleDirPath = artifactFs.getPath(bundleDir);
                        if (Files.exists(bundleDirPath)) {
                            LOGGER.debugf("Found extension assets in: %s", path);
                            scan(webAssetsProducer, bundleConfig, bundleName, artifactFs.getPath("/"), bundleDirPath);
                        }
                    } catch (IOException e) {
                        LOGGER.warnf(e, "Unable to create the file system from the path: %s", path);
                    }
                }
            }
        }
        for (ApplicationArchive archive : allApplicationArchives) {
            archive.accept(tree -> {
                for (Path rootDir : tree.getRoots()) {
                    // Note that we cannot use ApplicationArchive.getChildPath(String) here because we would not be able to detect
                    // a wrong directory bundleName on case-insensitive file systems
                    try {
                        final Path bundleDirPath = rootDir.resolve(bundleDir);
                        if (Files.isDirectory(bundleDirPath) && bundleDirPath.toString().endsWith(bundleDir)) {
                            LOGGER.debugf("Found extension assets dir: %s", bundleDirPath);
                            scan(webAssetsProducer, bundleConfig, bundleName, rootDir, bundleDirPath);
                            break;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    private void scan(BuildProducer<WebAssetBuildItem> webAssetsProducer,
            BundleConfig config,
            String bundleName,
            Path root,
            Path directory)
            throws IOException {
        Path toScan = directory == null ? root : directory;
        try (Stream<Path> files = Files.list(toScan)) {
            Iterator<Path> iter = files.iterator();
            while (iter.hasNext()) {
                Path filePath = iter.next();
                if (!toScan.isAbsolute()
                        && filePath.isAbsolute()
                        && filePath.getRoot() != null) {
                    filePath = filePath.getRoot().relativize(filePath);
                }
                final PathMatcher pathMatcher = filePath.getFileSystem()
                        .getPathMatcher("glob:**/" + config.pathMatcher().orElse(config.type().pathMatcher()));
                if (Files.isRegularFile(filePath) && pathMatcher.matches(filePath)) {
                    LOGGER.infof("Found %s asset %s", bundleName, filePath);
                    String assetPath = root.relativize(filePath).toString();
                    if (File.separatorChar != '/') {
                        assetPath = assetPath.replace(File.separatorChar, '/');
                    }
                    produceWebAsset(webAssetsProducer, bundleName, root.toString(), assetPath,
                            filePath, config);
                } else if (Files.isDirectory(filePath)) {
                    LOGGER.debugf("Scan directory: %s", filePath);
                    scan(webAssetsProducer, config, bundleName, root, filePath);
                }
            }
        }
    }

    private static void produceWebAsset(BuildProducer<WebAssetBuildItem> webAssetsProducer,
            String bundleName,
            String basePath,
            String filePath,
            Path originalPath,
            BundleConfig config) {
        if (filePath.isEmpty()) {
            return;
        }
        String fullPath = basePath + filePath;
        LOGGER.debugf("Produce template build items [filePath: %s, fullPath: %s, originalPath: %s", filePath, fullPath,
                originalPath);
        webAssetsProducer.produce(
                new WebAssetBuildItem(bundleName, filePath, originalPath, readTemplateContent(originalPath),
                        config.defaultCharset()));
    }

    private boolean isApplicationArchive(ResolvedDependency dependency, Set<ApplicationArchive> applicationArchives) {
        for (ApplicationArchive archive : applicationArchives) {
            if (archive.getKey() == null) {
                continue;
            }
            if (dependency.getGroupId().equals(archive.getKey().getGroupId())
                    && dependency.getArtifactId().equals(archive.getKey().getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    static byte[] readTemplateContent(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the template content from path: " + path, e);
        }
    }

}
