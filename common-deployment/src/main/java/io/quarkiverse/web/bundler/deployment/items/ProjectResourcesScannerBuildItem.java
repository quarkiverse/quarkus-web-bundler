package io.quarkiverse.web.bundler.deployment.items;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.toUnixPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.ResolvedDependency;

public final class ProjectResourcesScannerBuildItem extends SimpleBuildItem {

    private static final Logger LOGGER = Logger.getLogger(ProjectResourcesScannerBuildItem.class);

    Set<ApplicationArchive> allApplicationArchives;
    List<ResolvedDependency> extensionArtifacts;

    public ProjectResourcesScannerBuildItem(Set<ApplicationArchive> allApplicationArchives,
            List<ResolvedDependency> extensionArtifacts) {
        this.allApplicationArchives = allApplicationArchives;
        this.extensionArtifacts = extensionArtifacts;
    }

    public List<WebAsset> scan(String dir, String pathMatcher, Charset charset) throws IOException {
        return scan(new Scanner(dir, pathMatcher, charset));
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
                if (Files.isDirectory(rootDir)) {
                    final Path dirPath = rootDir.resolve(scanner.dir());
                    if (Files.isDirectory(dirPath) && dirPath.toString().endsWith(scanner.dir())) {
                        scan(rootDir, dirPath, scanner.pathMatcher(), scanner.charset, webAssetConsumer, true);
                        break;
                    }
                } else {
                    try (FileSystem artifactFs = ZipUtils.newFileSystem(rootDir)) {
                        Path rootDirFs = artifactFs.getPath("/");
                        Path dirPath = artifactFs.getPath(scanner.dir());
                        if (Files.exists(dirPath)) {
                            scan(rootDirFs, dirPath, scanner.pathMatcher(), scanner.charset(), webAssetConsumer,
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
                for (Path rootDir : tree.getRoots()) {
                    // Note that we cannot use ApplicationArchive.getChildPath(String) here because we would not be able to detect
                    // a wrong directory bundleName on case-insensitive file systems
                    try {
                        final Path dirPath = rootDir.resolve(scanner.dir());
                        if (Files.isDirectory(dirPath) && toUnixPath(dirPath.toString()).endsWith(scanner.dir())) {
                            scan(rootDir, dirPath, scanner.pathMatcher(), scanner.charset, webAssetConsumer, true);
                            break;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    private void scan(
            Path root,
            Path directory,
            String pathMatcher,
            Charset charset,
            Consumer<WebAsset> webAssetConsumer,
            boolean canReadLater)
            throws IOException {
        Path toScan = directory == null ? root : directory;
        try (Stream<Path> files = Files.find(toScan, 20, (p, a) -> Files.isRegularFile(p))) {
            Iterator<Path> iter = files.iterator();
            while (iter.hasNext()) {
                Path filePath = iter.next();
                if (!toScan.isAbsolute()
                        && filePath.isAbsolute()
                        && filePath.getRoot() != null) {
                    filePath = filePath.getRoot().relativize(filePath);
                }
                final Path relativePath = toScan.relativize(filePath);
                final PathMatcher assetsPathMatcher = relativePath.getFileSystem()
                        .getPathMatcher(pathMatcher);
                final boolean isAsset = assetsPathMatcher.matches(relativePath);
                if (isAsset) {
                    String assetPath = root.relativize(filePath).normalize().toString();
                    if (assetPath.contains("\\")) {
                        assetPath = toUnixPath(assetPath);
                    }
                    if (!assetPath.isEmpty()) {
                        webAssetConsumer.accept(toWebAsset(assetPath,
                                filePath.normalize(), charset, canReadLater));
                    }
                }
            }
        }
    }

    static WebAsset toWebAsset(String resourcePath, Path filePath, Charset charset, boolean canReadLater) {
        return new DefaultWebAsset(resourcePath, Optional.of(filePath), canReadLater ? null : readTemplateContent(filePath),
                charset);
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

    public record Scanner(String dir, String pathMatcher, Charset charset) {
    }
}
