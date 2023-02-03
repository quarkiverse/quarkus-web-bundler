package io.quarkiverse.web.assets.deployment;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.assets.deployment.WebAssetsConfig.BundleConfig;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
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
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths,
            BuildProducer<WebAssetPathBuildItem> assetPaths,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources,
            WebAssetsConfig config)
            throws IOException {
        Set<Path> basePaths = new HashSet<>();
        Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        List<ResolvedDependency> extensionArtifacts = curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(Dependency::isRuntimeExtensionArtifact).collect(Collectors.toList());
        Map<String, BundleConfig> bundles = new HashMap<>(config.bundles());
        if (config.presetComponents()) {
            bundles.putAll(ComponentBundles.COMPONENT_BUNDLES);
        }
        if (config.presetAssets()) {
            bundles.putAll(ComponentBundles.COMPONENT_BUNDLES);
        }
        for (Map.Entry<String, BundleConfig> e : bundles.entrySet()) {
            findBundleAssets(config, e.getKey(), e.getValue(), assetPaths, nativeImageResources, watchedPaths, basePaths,
                    allApplicationArchives,
                    extensionArtifacts);
        }

    }

    private void findBundleAssets(WebAssetsConfig config, String name, BundleConfig bundleConfig,
            BuildProducer<WebAssetPathBuildItem> assetPaths,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths,
            Set<Path> basePaths, Set<ApplicationArchive> allApplicationArchives,
            List<ResolvedDependency> extensionArtifacts) throws IOException {
        final String bundleDir = bundleConfig.dir();
        for (ResolvedDependency artifact : extensionArtifacts) {
            if (isApplicationArchive(artifact, allApplicationArchives)) {
                // Skip extension archives that are also application archives
                continue;
            }
            for (Path path : artifact.getResolvedPaths()) {
                if (Files.isDirectory(path)) {
                    // Try to find the templates in the root dir
                    try (Stream<Path> paths = Files.list(path)) {
                        Path basePath = paths.filter(p -> isWebAssetsRootPath(bundleDir, p)).findFirst().orElse(null);
                        if (basePath != null) {
                            LOGGER.debugf("Found extension assets dir: %s", path);
                            scan(name, bundleConfig, basePath, bundleDir + "/", watchedPaths, assetPaths, nativeImageResources,
                                    basePath);
                            break;
                        }
                    }
                } else {
                    try (FileSystem artifactFs = ZipUtils.newFileSystem(path)) {

                        Path basePath = artifactFs.getPath(bundleDir);
                        if (Files.exists(basePath)) {
                            LOGGER.debugf("Found extension assets in: %s", path);
                            scan(name, bundleConfig, basePath, bundleDir + "/", watchedPaths, assetPaths, nativeImageResources,
                                    basePath);
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
                    // a wrong directory name on case-insensitive file systems
                    try (Stream<Path> rootDirPaths = Files.list(rootDir)) {
                        Path basePath = rootDirPaths.filter(p -> isWebAssetsRootPath(bundleDir, p)).findFirst().orElse(null);
                        if (basePath != null) {
                            LOGGER.debugf("Found assets dir: %s", basePath);
                            basePaths.add(basePath);
                            scan(name, bundleConfig, basePath, bundleDir + "/", watchedPaths, assetPaths, nativeImageResources,
                                    basePath);
                            break;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    private void scan(String name, BundleConfig config, Path directory, String basePath,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths, BuildProducer<WebAssetPathBuildItem> webAssetPaths,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources, Path root)
            throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            Iterator<Path> iter = files.iterator();
            while (iter.hasNext()) {
                Path filePath = iter.next();
                if (!directory.isAbsolute()
                        && filePath.isAbsolute()
                        && filePath.getRoot() != null) {
                    filePath = filePath.getRoot().relativize(filePath);
                }
                final PathMatcher pathMatcher = filePath.getFileSystem()
                        .getPathMatcher("glob:**/" + config.pathMatcher().orElse(config.type().pathMatcher()));
                if (Files.isRegularFile(filePath) && pathMatcher.matches(filePath)) {
                    LOGGER.infof("Found %s asset %s", name, filePath);
                    String assetPath = root.relativize(filePath).toString();
                    if (File.separatorChar != '/') {
                        assetPath = assetPath.replace(File.separatorChar, '/');
                    }
                    produceTemplateBuildItems(webAssetPaths, watchedPaths, nativeImageResources, basePath, assetPath,
                            filePath, config);
                } else if (Files.isDirectory(filePath)) {
                    LOGGER.debugf("Scan directory: %s", filePath);
                    scan(name, config, filePath, basePath, watchedPaths, webAssetPaths, nativeImageResources, root);
                }
            }
        }
    }

    private static void produceTemplateBuildItems(BuildProducer<WebAssetPathBuildItem> webAssetPaths,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources, String basePath, String filePath,
            Path originalPath,
            BundleConfig config) {
        if (filePath.isEmpty()) {
            return;
        }
        String fullPath = basePath + filePath;
        LOGGER.debugf("Produce template build items [filePath: %s, fullPath: %s, originalPath: %s", filePath, fullPath,
                originalPath);
        // NOTE: we cannot just drop the template because a template param can be added
        watchedPaths.produce(new HotDeploymentWatchedFileBuildItem(fullPath, true));
        nativeImageResources.produce(new NativeImageResourceBuildItem(fullPath));
        webAssetPaths.produce(
                new WebAssetPathBuildItem(filePath, originalPath, readTemplateContent(originalPath, config.defaultCharset())));
    }

    private static boolean isWebAssetsRootPath(String bundleDir, Path path) {
        return path.getFileName().toString().equals(bundleDir);
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

    static String readTemplateContent(Path path, Charset defaultCharset) {
        try {
            return Files.readString(path, defaultCharset);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the template content from path: " + path, e);
        }
    }

}
