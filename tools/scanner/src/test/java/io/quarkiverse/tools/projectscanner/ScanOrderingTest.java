package io.quarkiverse.tools.projectscanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for reproducible ordering and {@link DuplicateStrategy} in scan results.
 */
class ScanOrderingTest {

    static final Path WEB_DIR = Path.of("src/test/resources/web");
    static final Path PROJECT_DIR = Path.of("src/test/resources");
    static final List<ScanDeclarationBuildItem> ALL_FILES = List.of(
            ScanDeclarationBuildItem.ofGlob("", "**"));

    /**
     * Scan results must be sorted alphabetically by scopedPath.
     * Does NOT use .sorted() — verifies the scanner itself returns ordered results.
     */
    @Test
    void resultsAreSortedByPath() throws IOException {
        var scanner = ProjectScanner.forPaths(
                List.of(WEB_DIR),
                ALL_FILES,
                List.of());
        var results = scanner.query().list();

        List<String> paths = results.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths).isSorted();
    }

    /**
     * Filtered queries must also return sorted results.
     */
    @Test
    void filteredResultsAreSortedByPath() throws IOException {
        var scanner = ProjectScanner.forPaths(
                List.of(WEB_DIR),
                ALL_FILES,
                List.of());

        var results = scanner.query().scopeDirs("app").list();
        List<String> paths = results.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths).isSorted();

        var results2 = scanner.query().scopeDirs("app").matchingGlob("**").list();
        List<String> paths2 = results2.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths2).isSorted();

        var results3 = scanner.query().matchingGlob("**.html").list();
        List<String> paths3 = results3.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths3).isSorted();
    }

    /**
     * matchingInDirs queries must return sorted results by indexPath.
     */
    @Test
    void anyDirResultsAreSortedByIndexPath() throws IOException {
        var scanner = ProjectScanner.forPaths(
                List.of(WEB_DIR),
                ALL_FILES,
                List.of());

        var results = scanner.query()
                .scopeDirs("app", "public")
                .list();

        List<String> paths = results.stream().map(ProjectFile::indexPath).toList();
        assertThat(paths).isSorted();
    }

    // -- DuplicateStrategy.PREFER_APP (default) --

    /**
     * PREFER_APP: local project files (highest priority) take priority over classpath resources.
     * The scanner indexes the same files as both LOCAL_PROJECT_FILE and ROOT_APPLICATION_RESOURCE;
     * PREFER_APP must keep LOCAL_PROJECT_FILE for every path.
     */
    @Test
    void preferApp_localFilesTakePriority() throws IOException {
        var scanner = scannerWithDuplicates();

        var results = scanner.query()
                .duplicateStrategy(DuplicateStrategy.PREFER_APP)
                .list();

        assertNoDuplicatePaths(results);
        assertThat(results).allMatch(f -> f.origin() == ProjectFile.Origin.LOCAL_PROJECT_FILE,
                "Local project files should take priority");
        // Verify a specific known file to ensure the assertion is not vacuous
        assertThat(results).anyMatch(f -> f.scopedPath().equals("index.html")
                && f.origin() == ProjectFile.Origin.LOCAL_PROJECT_FILE);
    }

    /**
     * PREFER_APP: results remain sorted after dedup.
     */
    @Test
    void preferApp_remainsSorted() throws IOException {
        var scanner = scannerWithDuplicates();

        var results = scanner.query()
                .duplicateStrategy(DuplicateStrategy.PREFER_APP)
                .list();

        List<String> paths = results.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths).isSorted();
    }

    // -- DuplicateStrategy.PREFER_DEPENDENCY --

    /**
     * PREFER_DEPENDENCY: lower-priority sources win over higher-priority ones.
     * The scanner indexes the same files as both LOCAL_PROJECT_FILE (priority 40) and
     * ROOT_APPLICATION_RESOURCE (priority 30); PREFER_DEPENDENCY must keep ROOT_APPLICATION_RESOURCE.
     */
    @Test
    void preferDependency_lowerPriorityWins() throws IOException {
        var scanner = scannerWithDuplicates();

        var results = scanner.query()
                .duplicateStrategy(DuplicateStrategy.PREFER_DEPENDENCY)
                .list();

        assertNoDuplicatePaths(results);
        assertThat(results).allMatch(f -> f.origin() == ProjectFile.Origin.ROOT_APPLICATION_RESOURCE,
                "Lower-priority ROOT_APPLICATION_RESOURCE should win with PREFER_DEPENDENCY");
        // Verify a specific known file to ensure the assertion is not vacuous
        assertThat(results).anyMatch(f -> f.scopedPath().equals("index.html")
                && f.origin() == ProjectFile.Origin.ROOT_APPLICATION_RESOURCE);
    }

    /**
     * PREFER_DEPENDENCY: the opposite type is chosen compared to PREFER_APP.
     */
    @Test
    void preferDependency_oppositeOfPreferApp() throws IOException {
        var scanner = scannerWithDuplicates();

        var appResults = scanner.query()
                .duplicateStrategy(DuplicateStrategy.PREFER_APP)
                .list();
        var depResults = scanner.query()
                .duplicateStrategy(DuplicateStrategy.PREFER_DEPENDENCY)
                .list();

        // Same paths, but different types
        List<String> appPaths = appResults.stream().map(ProjectFile::scopedPath).toList();
        List<String> depPaths = depResults.stream().map(ProjectFile::scopedPath).toList();
        assertThat(appPaths).isEqualTo(depPaths);

        for (int i = 0; i < appResults.size(); i++) {
            assertThat(appResults.get(i).origin())
                    .as("Origins should differ for path '%s'", appPaths.get(i))
                    .isNotEqualTo(depResults.get(i).origin());
        }
    }

    /**
     * PREFER_DEPENDENCY: results remain sorted after dedup.
     */
    @Test
    void preferDependency_remainsSorted() throws IOException {
        var scanner = scannerWithDuplicates();

        var results = scanner.query()
                .duplicateStrategy(DuplicateStrategy.PREFER_DEPENDENCY)
                .list();

        List<String> paths = results.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths).isSorted();
    }

    // -- DuplicateStrategy.FAIL --

    /**
     * FAIL: throws when duplicate paths are found.
     */
    @Test
    void fail_throwsOnDuplicates() throws IOException {
        var scanner = scannerWithDuplicates();

        assertThatThrownBy(() -> scanner.query()
                .duplicateStrategy(DuplicateStrategy.FAIL)
                .list())
                .isInstanceOf(DuplicatePathException.class)
                .hasMessageContaining("Duplicate path found");
    }

    /**
     * FAIL: does not throw when there are no duplicates.
     */
    @Test
    void fail_succeedsWithoutDuplicates() throws IOException {
        var scanner = ProjectScanner.forPaths(
                List.of(WEB_DIR),
                ALL_FILES,
                List.of());

        var results = scanner.query()
                .duplicateStrategy(DuplicateStrategy.FAIL)
                .list();

        assertThat(results).isNotEmpty();
    }

    /**
     * FAIL: files with the same scopedPath but from different scope dirs (e.g. app/style.css and
     * public/style.css both have scopedPath "style.css") are NOT duplicates because dedup uses
     * indexPath (which includes the scope dir prefix).
     */
    @Test
    void fail_doesNotDeduplicateAcrossScopeDirs() throws IOException {
        var scanner = ProjectScanner.forPaths(
                List.of(WEB_DIR),
                ALL_FILES,
                List.of());

        var results = scanner.query()
                .scopeDirs("app", "public")
                .duplicateStrategy(DuplicateStrategy.FAIL)
                .list();

        List<String> scopedPaths = results.stream().map(ProjectFile::scopedPath).toList();
        // Both app/style.css and public/style.css have scopedPath "style.css"
        assertThat(scopedPaths.stream().filter("style.css"::equals).count())
                .as("Both app/style.css and public/style.css should be present")
                .isEqualTo(2);
    }

    // -- Helpers --

    /**
     * Creates a scanner where the same directory is indexed as both classpath (ROOT_APPLICATION_RESOURCE)
     * and local project dir (LOCAL_PROJECT_FILE), producing duplicates in the index.
     * This simulates a dev-mode scenario where src/main/resources files are available both
     * as classpath resources and as local project files.
     */
    private static ProjectScanner scannerWithDuplicates() throws IOException {
        return ProjectScanner.forPaths(
                List.of(WEB_DIR),
                PROJECT_DIR,
                List.of(new ProjectScanner.LocalDirEntry(WEB_DIR, WEB_DIR)),
                ALL_FILES,
                List.of(), StandardCharsets.UTF_8);
    }

    private static void assertNoDuplicatePaths(List<ProjectFile> results) {
        List<String> paths = results.stream().map(ProjectFile::scopedPath).toList();
        assertThat(paths).doesNotHaveDuplicates();
    }
}