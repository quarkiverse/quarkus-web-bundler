package io.quarkiverse.web.assets.deployment;

import java.io.File;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.assets.deployment.items.WebAsset;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.maven.dependency.ResolvedDependency;

public class ProjectResourcesScanner {

    private static final Logger LOGGER = Logger.getLogger(ProjectResourcesScanner.class);

    Set<ApplicationArchive> allApplicationArchives;
    List<ResolvedDependency> extensionArtifacts;

    public ProjectResourcesScanner(Set<ApplicationArchive> allApplicationArchives,
            List<ResolvedDependency> extensionArtifacts) {
        this.allApplicationArchives = allApplicationArchives;
        this.extensionArtifacts = extensionArtifacts;
    }

    public List<WebAsset> scan(String dir, String glob, Charset charset) throws IOException {
        return scan(new Scanner(dir, glob, charset));
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
                        scan(rootDir, dirPath, scanner.glob(), scanner.charset, webAssetConsumer);
                        break;
                    }
                } else {
                    try (FileSystem artifactFs = ZipUtils.newFileSystem(rootDir)) {
                        Path dirPath = artifactFs.getPath(scanner.dir());
                        if (Files.exists(dirPath)) {
                            scan(rootDir, artifactFs.getPath("/"), scanner.glob(), scanner.charset(), webAssetConsumer);
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
                        if (Files.isDirectory(dirPath) && dirPath.toString().endsWith(scanner.dir())) {
                            scan(rootDir, dirPath, scanner.glob(), scanner.charset, webAssetConsumer);
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
            String glob,
            Charset charset,
            Consumer<WebAsset> webAssetConsumer)
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
                final PathMatcher assetsPathMatcher = filePath.getFileSystem()
                        .getPathMatcher("glob:" + glob);
                final boolean isAsset = assetsPathMatcher.matches(filePath);
                if (Files.isRegularFile(filePath) && isAsset) {
                    String assetPath = root.relativize(filePath).normalize().toString();
                    if (File.separatorChar != '/') {
                        assetPath = assetPath.replace(File.separatorChar, '/');
                    }
                    if (!assetPath.isEmpty()) {
                        webAssetConsumer.accept(toWebAsset(assetPath,
                                filePath.normalize(), charset));
                    }
                } else if (Files.isDirectory(filePath)) {
                    LOGGER.debugf("Scan directory: %s", filePath);
                    scan(root, filePath, glob, charset, webAssetConsumer);
                }
            }
        }
    }

    static WebAsset toWebAsset(String resourcePath, Path filePath, Charset charset) {
        return new WebAsset(resourcePath, filePath, readTemplateContent(filePath),
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

    static byte[] readTemplateContent(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the template content from path: " + path, e);
        }
    }

    static class Scanner {
        private final String dir;
        private final String glob;

        private final Charset charset;

        Scanner(String dir, String glob, Charset charset) {
            this.dir = dir;
            this.glob = glob;
            this.charset = charset;
        }

        public String dir() {
            return dir;
        }

        public String glob() {
            return glob;
        }

        public Charset charset() {
            return charset;
        }
    }
}
