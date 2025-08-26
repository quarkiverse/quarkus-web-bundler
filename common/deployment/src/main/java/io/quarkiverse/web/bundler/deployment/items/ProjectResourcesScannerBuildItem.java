package io.quarkiverse.web.bundler.deployment.items;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.toUnixPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.util.PathUtils;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.ResolvedDependency;

public final class ProjectResourcesScannerBuildItem extends SimpleBuildItem {

    private static final Logger LOGGER = Logger.getLogger(ProjectResourcesScannerBuildItem.class);

    Set<ApplicationArchive> allApplicationArchives;
    List<ResolvedDependency> extensionArtifacts;
    private final Collection<Path> srcResourcesDirs;
    private final List<Path> localDirs;
    private final String resourceWebDir;

    public ProjectResourcesScannerBuildItem(Set<ApplicationArchive> allApplicationArchives,
            List<ResolvedDependency> extensionArtifacts,
            Collection<Path> srcResourcesDirs,
            List<Path> localDirs,
            String resourceWebDir) {
        this.allApplicationArchives = allApplicationArchives;
        this.extensionArtifacts = extensionArtifacts;
        this.srcResourcesDirs = srcResourcesDirs;
        this.localDirs = localDirs;
        this.resourceWebDir = resourceWebDir;
    }

    public List<String> webDirs() {
        List<String> watchedDirs = new ArrayList<>();
        watchedDirs.add(resourceWebDir);
        localDirs.forEach(dir -> watchedDirs.add(dir.toString()));
        return watchedDirs;
    }

    public boolean hasWebStuffChanged(Set<String> changedResource) {
        return changedResource.stream().anyMatch(s -> webDirs().stream()
                .anyMatch(s::startsWith));
    }

    public List<WebAsset> scan(String dir, String pathMatcher, List<String> ignored, Charset charset) throws IOException {
        return scan(new Scanner(dir, pathMatcher, ignored, charset));
    }

    public List<WebAsset> scan(Scanner scanner) throws IOException {
        return scan(List.of(scanner));
    }

    public List<WebAsset> scan(List<Scanner> scanners) throws IOException {
        final List<WebAsset> webAssets = new ArrayList<>();
        for (Scanner assetsScanner : scanners) {
            scanProject(assetsScanner, webAssets::add);
        }
        return webAssets;
    }

    private void scanProject(Scanner scanner,
            Consumer<WebAsset> webAssetConsumer) throws IOException {
        for (ResolvedDependency artifact : extensionArtifacts) {
            if (isApplicationArchive(artifact, allApplicationArchives)) {
                // Skip extension archives that are also application archives
                continue;
            }
            for (Path rootDir : artifact.getResolvedPaths()) {
                Path webDir = rootDir.resolve(resourceWebDir);
                if (Files.isDirectory(webDir)) {
                    final Path dirPath = webDir.resolve(scanner.dir());
                    if (Files.isDirectory(dirPath) && dirPath.toString().endsWith(scanner.dir())) {
                        scan(webDir, dirPath, scanner.pathMatcher(), scanner.ignored(), scanner.charset, webAssetConsumer,
                                false);
                        break;
                    }
                } else {
                    try (FileSystem artifactFs = ZipUtils.newFileSystem(rootDir)) {
                        Path rootDirFs = artifactFs.getPath(PathUtils.prefixWithSlash(resourceWebDir));
                        Path dirPath = rootDirFs.resolve(scanner.dir());
                        if (Files.exists(dirPath)) {
                            scan(rootDirFs, dirPath, scanner.pathMatcher(), scanner.ignored(), scanner.charset(),
                                    webAssetConsumer,
                                    false);
                        }
                    } catch (IOException e) {
                        LOGGER.warnf(e, "Unable to create the file system from the rootDir: %s", rootDir);
                    }
                }
            }
        }
        for (ApplicationArchive archive : allApplicationArchives) {
            archive.accept(tree -> {
                scanRoots(tree.getRoots().stream().map(s -> s.resolve(resourceWebDir)).toList(), scanner, webAssetConsumer,
                        false);
            });
        }
        scanRoots(localDirs, scanner, webAssetConsumer, true);
    }

    private static boolean isLocalFileSystem(Path path) {
        try {
            return "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    private void scanRoots(Collection<Path> tree, Scanner scanner, Consumer<WebAsset> webAssetConsumer,
            boolean external) {
        for (Path rootDir : tree) {
            // Note that we cannot use ApplicationArchive.getChildPath(String) here because we would not be able to detect
            // a wrong directory bundleName on case-insensitive file systems
            try {
                final Path dirPath = rootDir.resolve(scanner.dir());
                if (Files.isDirectory(dirPath) && toUnixPath(dirPath.toString()).endsWith(scanner.dir())) {
                    scan(rootDir, dirPath, scanner.pathMatcher(), scanner.ignored(), scanner.charset, webAssetConsumer,
                            external);
                    break;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void scan(
            Path root,
            Path directory,
            String pathMatcher,
            List<String> ignored,
            Charset charset,
            Consumer<WebAsset> webAssetConsumer,
            boolean external)
            throws IOException {
        Path toScan = directory == null ? root : directory;
        boolean isLocalFileSystem = isLocalFileSystem(toScan);
        try (Stream<Path> files = Files.find(toScan, 20, (p, a) -> Files.isRegularFile(p))) {
            Iterator<Path> iter = files.iterator();
            while (iter.hasNext()) {
                Path filePath = iter.next();
                if (!toScan.isAbsolute()
                        && filePath.isAbsolute()
                        && filePath.getRoot() != null) {
                    filePath = filePath.getRoot().relativize(filePath);
                }
                final boolean isAsset = (pathMatcher == null || matcher(toScan, List.of(pathMatcher)).test(filePath))
                        && !matcher(toScan, ignored).test(filePath);
                if (isAsset) {
                    String assetPath = root.relativize(filePath).normalize().toString();
                    if (assetPath.contains("\\")) {
                        assetPath = toUnixPath(assetPath);
                    }

                    if (!assetPath.isEmpty()) {
                        final Path srcFilePath = external ? filePath : (isLocalFileSystem ? findSrc(assetPath) : null);
                        webAssetConsumer.accept(toWebAsset(assetPath,
                                filePath.normalize(), srcFilePath, charset, isLocalFileSystem, external));
                    }
                }
            }
        }
    }

    Path findSrc(String assetPath) {
        for (Path srcResourcesDir : this.srcResourcesDirs) {
            final Path absolutePath = srcResourcesDir.resolve(resourceWebDir).resolve(assetPath);
            if (Files.isRegularFile(absolutePath)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("found source for %s in %s", assetPath, absolutePath);
                }
                return absolutePath;
            }
        }
        return null;
    }

    WebAsset toWebAsset(String webPath, Path filePath, Path srcFilePath, Charset charset,
            boolean isLocalFileSystem, boolean external) {
        if (external) {
            return new ExternalFileWebAsset(webPath, srcFilePath, charset);
        }
        final String resourcePath = PathUtils.join(resourceWebDir, webPath);
        if (srcFilePath != null) {
            return new ProjectResourceWebAsset(webPath, srcFilePath, resourcePath, WebAsset.Type.PROJECT_SOURCE, charset);
        }
        if (isLocalFileSystem) {
            return new ProjectResourceWebAsset(webPath, filePath, resourcePath, WebAsset.Type.PROJECT_RESOURCE, charset);
        }
        return new JarResourceWebAsset(webPath, filePath, resourcePath, readTemplateContent(filePath),
                charset);
    }

    static Predicate<Path> matcher(Path rootDir, List<String> rules) {
        if (rules.isEmpty()) {
            return path -> true;
        }
        return path -> rules.stream()
                .anyMatch(s -> path.getFileSystem().getPathMatcher(s).matches(rootDir.relativize(path)));
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

    public static byte[] readTemplateContent(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the content from path: " + path, e);
        }
    }

    public record Scanner(String dir, String pathMatcher, List<String> ignored, Charset charset) {
        public Scanner(String pathMatcher, List<String> ignored, Charset charset) {
            this("", pathMatcher, ignored, charset);
        }

    }
}
