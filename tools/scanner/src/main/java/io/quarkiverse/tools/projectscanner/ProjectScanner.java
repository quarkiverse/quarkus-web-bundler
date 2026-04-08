package io.quarkiverse.tools.projectscanner;

import static io.quarkiverse.tools.projectscanner.util.ProjectUtils.findSrcResourcesDirs;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.tools.projectscanner.util.ProjectUtils;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.paths.OpenPathTree;
import io.quarkus.paths.PathTree;

/**
 * Scans all project resources once during initialization and provides fast filtering
 * against the pre-built index.
 * Scans from the root of all application archives, extension artifacts, and local directories.
 */
public final class ProjectScanner {

    private static final Logger LOGGER = Logger.getLogger(ProjectScanner.class);

    private final Map<String, Path> projectLocalDirsByName;

    // The pre-built sorted index of all assets
    private final List<IndexedFile> indexedAssets;

    // Directory index: maps each directory prefix (with trailing /) to the list of files under it
    // File references are shared with indexedAssets (no object duplication)
    private final Map<String, List<IndexedFile>> dirIndex;

    private final Charset charset;

    private ProjectScanner(
            Map<String, Path> projectLocalDirsByName,
            List<IndexedFile> indexedAssets,
            Map<String, List<IndexedFile>> dirIndex,
            Charset charset) {
        this.projectLocalDirsByName = Collections.unmodifiableMap(projectLocalDirsByName);
        this.indexedAssets = Collections.unmodifiableList(indexedAssets);
        this.dirIndex = Collections.unmodifiableMap(dirIndex);
        this.charset = charset;
    }

    public Map<String, Path> localDirsByName() {
        return projectLocalDirsByName;
    }

    record LocalDirEntry(Path dir, Path indexBase) {
    }

    /**
     * Creates a scanner by indexing the given paths (directories or JARs).
     * Package-private for testing.
     */
    static ProjectScanner forPaths(List<Path> paths, List<ScanDeclarationBuildItem> declarations,
            List<String> defaultIgnoredFiles) throws IOException {
        return forPaths(paths, null, List.of(), declarations, defaultIgnoredFiles, StandardCharsets.UTF_8);
    }

    /**
     * Creates a scanner by indexing the given paths, with optional local dir entries
     * that support the indexFromProjectRoot flag.
     * Package-private for testing.
     */
    static ProjectScanner forPaths(List<Path> paths, Path projectDir, List<LocalDirEntry> localDirEntries,
            List<ScanDeclarationBuildItem> declarations,
            List<String> defaultIgnoredFiles, Charset charset) throws IOException {
        List<IndexedFile> index = new ArrayList<>();
        List<PathMatcher> ignoredMatchers = compilePatterns(defaultIgnoredFiles);

        for (Path path : paths) {
            try (OpenPathTree tree = PathTree.ofDirectoryOrArchive(path).open()) {
                for (Path root : tree.getRoots()) {
                    indexDirectory(root, root, List.of(), index, ProjectFile.Origin.APPLICATION_RESOURCE,
                            ignoredMatchers, declarations);
                }
            }
        }

        for (LocalDirEntry entry : localDirEntries) {
            indexDirectory(entry.indexBase(), entry.dir(), List.of(), index, ProjectFile.Origin.LOCAL_PROJECT_FILE,
                    ignoredMatchers, declarations);
        }

        List<IndexedFile> sorted = sortIndex(index);
        return new ProjectScanner(Map.of(), sorted, buildDirIndex(sorted), charset);
    }

    public static ProjectScanner create(LaunchModeBuildItem launchMode,
            ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            ProjectRootBuildItem projectDir,
            List<ScanLocalDirBuildItem> projectLocalDirs,
            List<ScanDeclarationBuildItem> declarations,
            List<String> defaultIgnoredFiles,
            Charset charset,
            int warningThreshold) throws IOException {
        final Collection<Path> srcResourcesDirs = launchMode.getLaunchMode().isDevOrTest()
                ? findSrcResourcesDirs(curateOutcome)
                : List.of();

        // Collect all contributed local directories and resolve them against project root
        final Map<String, Path> projectLocalDirsByName = new LinkedHashMap<>();
        final List<LocalDirEntry> localDirEntries = new ArrayList<>();
        if (projectDir.exists()) {
            for (ScanLocalDirBuildItem item : projectLocalDirs) {
                Path resolvedDir = ProjectUtils.resolveSubDir(projectDir.path(), item.dir());
                if (resolvedDir == null) {
                    continue;
                }
                projectLocalDirsByName.put(item.dir().toString(), resolvedDir);
                Path indexBase = item.indexBase() != null && !item.indexBase().isEmpty()
                        ? ProjectUtils.resolveSubDir(projectDir.path(), item.indexBase())
                        : projectDir.path();
                if (indexBase == null) {
                    // if null we use project root as base by default
                    indexBase = projectDir.path();
                }
                localDirEntries.add(new LocalDirEntry(resolvedDir, indexBase));
            }
        }

        // Build the index immediately with default ignored files applied
        List<IndexedFile> indexedAssets = buildIndex(applicationArchives, curateOutcome,
                srcResourcesDirs, projectDir.path(), localDirEntries, declarations, defaultIgnoredFiles);

        LOGGER.debugf("Indexed %d project resources", indexedAssets.size());
        checkIndexSize(indexedAssets.size(), warningThreshold);

        return new ProjectScanner(projectLocalDirsByName, indexedAssets, buildDirIndex(indexedAssets), charset);
    }

    private static void checkIndexSize(int size, int warningThreshold) {
        if (size > warningThreshold) {
            LOGGER.warnf("Scanner indexed %d files, exceeding the warning threshold of %d. "
                    + "This may indicate a ScanDeclarationBuildItem with too broad a scope. "
                    + "If your application legitimately has this many scanned resources, "
                    + "increase quarkus.project-scanner.indexed-files-warning-threshold.", size, warningThreshold);
        }
    }

    public ScanQueryBuilder query() {
        return new ScanQueryBuilder(this);
    }

    /**
     * Finds all project files matching the given query.
     * Uses the directory index for scoped lookups when scope dirs are set.
     * Matching and exclude filters receive scoped paths (relativized from the scope directory).
     * Results are sorted alphabetically by index path and deduplicated
     * according to the provided {@link DuplicateStrategy}.
     */
    List<ProjectFile> find(ScanQuery query, DuplicateStrategy duplicateStrategy) throws IOException {
        List<String> scopeDirs = query.scopeDirs();
        Set<ProjectFile.Origin> origins = query.origins();
        List<ProjectFile> results = new ArrayList<>();

        boolean hasRootScope = scopeDirs.contains("");

        if (hasRootScope) {
            // Root scope: full scan, filters see full paths
            for (IndexedFile indexed : indexedAssets) {
                if (matchesOrigin(indexed, origins) && matchesFilters(indexed.indexPath(), query)) {
                    results.add(indexed.toProjectFile(indexed.indexPath(), charset));
                }
            }
        } else {
            // Scoped: search only files under scope dirs, filters see relativized paths
            for (String scopeDir : scopeDirs) {
                for (IndexedFile indexed : dirIndex.getOrDefault(scopeDir, List.of())) {
                    if (!matchesOrigin(indexed, origins)) {
                        continue;
                    }
                    String scopedPath = indexed.indexPath().substring(scopeDir.length());
                    if (matchesFilters(scopedPath, query)) {
                        results.add(indexed.toProjectFile(scopedPath, charset));
                    }
                }
            }
        }

        return applyDuplicateStrategy(results, duplicateStrategy);
    }

    private static boolean matchesOrigin(IndexedFile indexed, Set<ProjectFile.Origin> origins) {
        return origins.isEmpty() || origins.contains(indexed.origin());
    }

    private static boolean matchesFilters(String path, ScanQuery query) {
        for (ScanFilter filter : query.matching()) {
            if (!filter.test(path)) {
                return false;
            }
        }
        for (ScanFilter filter : query.excludes()) {
            if (filter.test(path)) {
                return false;
            }
        }
        return true;
    }

    private static List<IndexedFile> buildIndex(ApplicationArchivesBuildItem applicationArchives,
            CurateOutcomeBuildItem curateOutcome,
            Collection<Path> srcResourcesDirs,
            Path projectDir, List<LocalDirEntry> localDirs,
            List<ScanDeclarationBuildItem> declarations,
            List<String> defaultIgnoredFiles) throws IOException {
        List<IndexedFile> index = new ArrayList<>();

        // Compile default ignored patterns once for all indexing operations
        List<PathMatcher> ignoredMatchers = compilePatterns(defaultIgnoredFiles);

        // Build app archive keys to skip extension artifacts that are also app archives (same as Qute core)
        final Set<ApplicationArchive> allApplicationArchives = applicationArchives.getAllApplicationArchives();
        final Set<ArtifactKey> appArtifactKeys = new HashSet<>(allApplicationArchives.size());
        for (var archive : allApplicationArchives) {
            appArtifactKeys.add(archive.getKey());
        }

        // Index extension artifacts as DEPENDENCY_RESOURCE (skip those that are also app archives)
        for (ResolvedDependency artifact : curateOutcome.getApplicationModel()
                .getDependencies(DependencyFlags.RUNTIME_EXTENSION_ARTIFACT)) {
            if (appArtifactKeys.contains(artifact.getKey())) {
                continue;
            }
            try (OpenPathTree tree = artifact.getContentTree().open()) {
                for (Path rootDir : tree.getRoots()) {
                    indexDirectory(rootDir, rootDir, srcResourcesDirs, index,
                            ProjectFile.Origin.DEPENDENCY_RESOURCE, ignoredMatchers, declarations);
                }
            }
        }

        // Index non-root application archives as DEPENDENCY_RESOURCE
        for (ApplicationArchive archive : applicationArchives.getApplicationArchives()) {
            archive.accept(tree -> {
                for (Path rootDir : tree.getRoots()) {
                    try {
                        indexDirectory(rootDir, rootDir, srcResourcesDirs, index,
                                ProjectFile.Origin.DEPENDENCY_RESOURCE, ignoredMatchers, declarations);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }

        // Index root archive (scanned last, same as Quarkus core Qute processor)
        applicationArchives.getRootArchive().accept(tree -> {
            for (Path rootDir : tree.getRoots()) {
                try {
                    indexDirectory(rootDir, rootDir, srcResourcesDirs, index,
                            ProjectFile.Origin.APPLICATION_RESOURCE, ignoredMatchers, declarations);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });

        // Index local directories
        for (LocalDirEntry entry : localDirs) {
            indexDirectory(entry.indexBase(), entry.dir(), srcResourcesDirs, index,
                    ProjectFile.Origin.LOCAL_PROJECT_FILE, ignoredMatchers, declarations);
        }

        return sortIndex(index);
    }

    /**
     * Sorts all indexed assets by indexPath (alphabetically), then by priority descending
     * so that for duplicate paths the highest-priority source comes first.
     * All versions are kept; deduplication is applied at query time via {@link DuplicateStrategy}.
     */
    private static List<IndexedFile> sortIndex(List<IndexedFile> index) {
        List<IndexedFile> result = new ArrayList<>(index);
        result.sort(Comparator.comparing(IndexedFile::indexPath)
                .thenComparing(Comparator.comparingInt(IndexedFile::priority).reversed()));
        return result;
    }

    /**
     * Builds a directory index from the sorted list of indexed files.
     * Each directory prefix (with trailing {@code /}) maps to all files recursively under it.
     * The lists share the same {@link IndexedFile} instances as the main index (no object duplication).
     * List order is preserved from the sorted index (by path then priority descending).
     */
    private static Map<String, List<IndexedFile>> buildDirIndex(List<IndexedFile> sortedIndex) {
        Map<String, List<IndexedFile>> index = new HashMap<>();
        for (IndexedFile file : sortedIndex) {
            String path = file.indexPath();
            int pos = 0;
            while ((pos = path.indexOf('/', pos)) != -1) {
                String dirPrefix = path.substring(0, pos + 1); // include trailing /
                index.computeIfAbsent(dirPrefix, k -> new ArrayList<>()).add(file);
                pos++;
            }
        }
        return index;
    }

    /**
     * Applies the given {@link DuplicateStrategy} to deduplicate results by index path.
     * Expects input already sorted by path then priority descending (from the index order).
     */
    private static List<ProjectFile> applyDuplicateStrategy(List<ProjectFile> results, DuplicateStrategy strategy) {
        if (results.size() <= 1) {
            return results;
        }
        Map<String, ProjectFile> byPath = new LinkedHashMap<>(results.size());
        for (ProjectFile file : results) {
            ProjectFile existing = byPath.get(file.indexPath());
            if (existing == null) {
                byPath.put(file.indexPath(), file);
            } else {
                switch (strategy) {
                    case FAIL -> throw new DuplicatePathException(
                            file.indexPath(), existing.origin(), file.origin());
                    case PREFER_APP -> {
                        // First seen is already highest priority (index is sorted by priority descending),
                        // so keep it: LOCAL_PROJECT_FILE > APPLICATION_RESOURCE > DEPENDENCY_RESOURCE
                    }
                    case PREFER_DEPENDENCY -> {
                        // Replace with later entry which has lower priority (index is sorted by priority
                        // descending), so the last seen wins: DEPENDENCY_RESOURCE > APPLICATION_RESOURCE > LOCAL_PROJECT_FILE
                        byPath.put(file.indexPath(), file);
                    }
                }
            }
        }
        return new ArrayList<>(byPath.values());
    }

    private static void indexDirectory(Path baseDirPath,
            Path directory,
            Collection<Path> srcResourcesDirs,
            List<IndexedFile> index,
            ProjectFile.Origin origin,
            List<PathMatcher> ignoredMatchers,
            List<ScanDeclarationBuildItem> declarations) throws IOException {
        boolean isLocalFileSystem = ProjectFile.isLocalFileSystem(directory);

        try (Stream<Path> files = Files.find(directory, 20, (p, a) -> a.isRegularFile())) {
            Iterator<Path> iter = files.iterator();
            while (iter.hasNext()) {
                Path filePath = iter.next();
                if (!directory.isAbsolute()
                        && filePath.isAbsolute()
                        && filePath.getRoot() != null) {
                    filePath = filePath.getRoot().relativize(filePath);
                }

                String indexPath = baseDirPath.relativize(filePath).normalize().toString();
                if (indexPath.contains("\\")) {
                    indexPath = toUnixPath(indexPath);
                }

                if (!indexPath.isEmpty()) {
                    // Apply default ignored files filter during indexing
                    if (matchesAnyPattern(indexPath, ignoredMatchers)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debugf("Ignoring file during indexing: %s", indexPath);
                        }
                        continue;
                    }

                    // Only index files matching at least one declaration
                    if (!matchesAnyDeclaration(indexPath, declarations)) {
                        continue;
                    }

                    final Path srcFilePath = origin == ProjectFile.Origin.LOCAL_PROJECT_FILE ? filePath
                            : (isLocalFileSystem ? findSrc(indexPath, srcResourcesDirs) : null);

                    index.add(new IndexedFile(indexPath, filePath.normalize(), srcFilePath, origin));
                }
            }
        }
    }

    private static boolean matchesAnyDeclaration(String indexPath, List<ScanDeclarationBuildItem> declarations) {
        for (ScanDeclarationBuildItem declaration : declarations) {
            if (declaration.query().matches(indexPath)) {
                return true;
            }
        }
        return false;
    }

    static PathMatcher createPathMatcher(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        return FileSystems.getDefault().getPathMatcher(pattern);
    }

    /**
     * Compiles a list of pattern strings into PathMatcher objects using Unix-style matching.
     *
     * @param patterns the list of pattern strings
     * @return a list of compiled PathMatcher objects
     */
    static List<PathMatcher> compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }

        List<PathMatcher> matchers = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            try {
                PathMatcher matcher = createPathMatcher(pattern);
                if (matcher != null) {
                    matchers.add(matcher);
                }
            } catch (Exception e) {
                LOGGER.warnf(e, "Failed to compile pattern: %s", pattern);
            }
        }
        return matchers;
    }

    /**
     * Checks if a file path matches a given PathMatcher.
     *
     * @param assetPath the path to check (relative path)
     * @param pathMatcher the compiled PathMatcher to match against
     * @return true if the path matches the pattern, false otherwise
     */
    private static boolean matchesPattern(String assetPath, PathMatcher pathMatcher) {
        if (pathMatcher == null || assetPath == null || assetPath.isEmpty()) {
            return false;
        }

        try {
            Path path = Path.of(assetPath);
            return pathMatcher.matches(path);
        } catch (Exception e) {
            LOGGER.warnf(e, "Failed to apply path matcher to asset %s", assetPath);
            return false;
        }
    }

    /**
     * Checks if a file path matches any of the provided PathMatchers.
     *
     * @param assetPath the path to check (relative path)
     * @param pathMatchers the list of compiled PathMatchers to match against
     * @return true if the path matches any pattern, false otherwise
     */
    private static boolean matchesAnyPattern(String assetPath, List<PathMatcher> pathMatchers) {
        if (pathMatchers == null || pathMatchers.isEmpty() || assetPath == null || assetPath.isEmpty()) {
            return false;
        }

        for (PathMatcher pathMatcher : pathMatchers) {
            if (matchesPattern(assetPath, pathMatcher)) {
                return true;
            }
        }
        return false;
    }

    private static Path findSrc(String assetPath, Collection<Path> srcResourcesDirs) {
        for (Path srcResourcesDir : srcResourcesDirs) {
            final Path absolutePath = srcResourcesDir.resolve(assetPath);
            if (Files.isRegularFile(absolutePath)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("found source for %s in %s", assetPath, absolutePath);
                }
                return absolutePath;
            }
        }
        return null;
    }

    public static byte[] readRuntimeResourceContent(String resourcePath) {
        AtomicReference<byte[]> content = new AtomicReference<>();
        QuarkusClassLoader.visitRuntimeResources(resourcePath, (v) -> {
            if (!Files.isRegularFile(v.getPath())) {
                throw new RuntimeException("Failed to locate file on disk for reading: " + v.getPath());
            }
            try {
                content.set(Files.readAllBytes(v.getPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        if (content.get() == null) {
            throw new RuntimeException("Failed to read content: " + resourcePath);
        }
        return content.get();
    }

    /**
     * Holds an indexed file with metadata for fast filtering and path recalculation.
     */
    private static class IndexedFile {
        final String indexPath; // Path relative to the index base
        final Path filePath; // The actual file path
        final Path srcFilePath; // Source file path (may be null)
        final ProjectFile.Origin origin;

        IndexedFile(String indexPath, Path filePath, Path srcFilePath, ProjectFile.Origin origin) {
            this.indexPath = indexPath;
            this.filePath = filePath;
            this.srcFilePath = srcFilePath;
            this.origin = origin;
        }

        int priority() {
            return switch (origin) {
                case LOCAL_PROJECT_FILE -> 40;
                case APPLICATION_RESOURCE -> 30;
                case DEPENDENCY_RESOURCE -> 10;
            };
        }

        String indexPath() {
            return indexPath;
        }

        ProjectFile.Origin origin() {
            return origin;
        }

        /**
         * Creates a new ProjectFile with the specified scoped path and charset.
         */
        ProjectFile toProjectFile(String scopedPath, Charset charset) {
            if (ProjectFile.isLocalFileSystem(filePath)) {
                String resPath = (origin != ProjectFile.Origin.LOCAL_PROJECT_FILE) ? indexPath : null;
                Path source = (origin == ProjectFile.Origin.LOCAL_PROJECT_FILE) ? filePath : srcFilePath;
                return new LocalProjectFile(indexPath, scopedPath, filePath, source, origin, resPath, charset);
            } else {
                return new ClasspathProjectFile(indexPath, scopedPath, filePath, origin, indexPath, charset);
            }
        }

    }

}
