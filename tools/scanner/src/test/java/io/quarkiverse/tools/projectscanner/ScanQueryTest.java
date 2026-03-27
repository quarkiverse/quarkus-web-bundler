package io.quarkiverse.tools.projectscanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScanQueryTest {

    /** Declaration that matches all files (used by most tests) */
    static final List<ScanDeclarationBuildItem> ALL_FILES = List.of(
            ScanDeclarationBuildItem.ofGlob("", "**"));

    static ProjectScanner scanner;

    @BeforeAll
    static void setup() throws IOException {
        scanner = ProjectScanner.forPaths(
                List.of(Path.of("src/test/resources/web")),
                ALL_FILES,
                List.of());
    }

    static List<String> scopedPaths(List<ProjectFile> files) {
        return files.stream().map(ProjectFile::scopedPath).sorted().toList();
    }

    @Test
    void scopeDir() throws IOException {
        var results = scopedPaths(scanner.query()
                .scopeDirs("public").list());
        assertThat(results).contains("style.css", "logo.png",
                "deep/nested/file.js");
        assertThat(results).doesNotContain("index.js");
    }

    @Test
    void matchingGlob() throws IOException {
        var results = scopedPaths(scanner.query()
                .matchingGlob("**.html").list());
        assertThat(results).contains("index.html", "about.html",
                "app/page.html", "templates/header.html");
    }

    @Test
    void matchingGlobNonRecursive() throws IOException {
        var results = scopedPaths(scanner.query()
                .matchingGlob("*.html").list());
        assertThat(results).contains("index.html", "about.html");
        assertThat(results).doesNotContain("app/page.html", "templates/header.html");
    }

    @Test
    void scopeDirWithGlob() throws IOException {
        // Scope to "app", glob "*.html" matches against relativized paths
        var results = scopedPaths(scanner.query()
                .scopeDirs("app").matchingGlob("*.html").list());
        assertThat(results).containsExactly("page.html");
    }

    @Test
    void scopeDirWithGlobRecursive() throws IOException {
        var results = scopedPaths(scanner.query()
                .scopeDirs("public").matchingGlob("**/*.js").list());
        assertThat(results).containsExactly("deep/nested/file.js");
    }

    @Test
    void matchingRegex() throws IOException {
        var results = scopedPaths(scanner.query()
                .matchingRegex(".*\\.json").list());
        assertThat(results).containsExactly("tsconfig.json");
    }

    @Test
    void scopeDirAndGlobCombined() throws IOException {
        // Scope narrows to app/, glob "**.html" matches relativized "page.html"
        var results = scopedPaths(scanner.query()
                .scopeDirs("app")
                .matchingGlob("**.html").list());
        assertThat(results).containsExactly("page.html");
    }

    @Test
    void excludesWithScope() throws IOException {
        // Scope to app/, exclude "**.html" applied to relativized paths
        var results = scopedPaths(scanner.query()
                .scopeDirs("app")
                .addExcluded(List.of("glob:**.html")).list());
        assertThat(results).contains("index.js", "style.css",
                "components/button.ts");
        assertThat(results).doesNotContain("page.html");
    }

    @Test
    void noFilters() throws IOException {
        var results = scopedPaths(scanner.query().list());
        assertThat(results).hasSize(12);
        assertThat(results).contains(
                "index.html", "about.html", "tsconfig.json",
                "app/index.js", "app/style.css", "app/page.html",
                "app/components/button.ts",
                "public/logo.png", "public/style.css",
                "public/deep/nested/file.js",
                "static/hello.txt", "templates/header.html");
    }

    @Test
    void specificFile() throws IOException {
        var results = scopedPaths(scanner.query()
                .matchingGlob("tsconfig.json").list());
        assertThat(results).containsExactly("tsconfig.json");
    }

    @Test
    void multipleScopeDirs() throws IOException {
        var results = scopedPaths(scanner.query()
                .scopeDirs("app", "public").list());
        assertThat(results).contains("index.js", "style.css", "page.html",
                "components/button.ts",
                "style.css", "logo.png", "deep/nested/file.js");
        assertThat(results).doesNotContain("index.html", "hello.txt", "header.html");
    }

    @Test
    void multipleScopeDirsWithGlob() throws IOException {
        // Glob "**.html" applied to relativized paths from each scope dir
        var results = scopedPaths(scanner.query()
                .scopeDirs("app", "templates")
                .matchingGlob("**.html").list());
        assertThat(results).containsExactlyInAnyOrder("page.html", "header.html");
    }

    @Test
    void singleScopeDirEquivalent() throws IOException {
        // scopeDirs with one dir should produce same results
        var multiResults = scopedPaths(scanner.query()
                .scopeDirs("app").list());
        var varargResults = scopedPaths(scanner.query()
                .scopeDirs(List.of("app")).list());
        assertThat(multiResults).isEqualTo(varargResults);
    }

    @Test
    void indexBaseFromProjectRoot() throws IOException {
        Path projectDir = Path.of("src/test/resources");
        Path webDir = projectDir.resolve("web");
        var scannerWithRoot = ProjectScanner.forPaths(
                List.of(),
                projectDir,
                List.of(new ProjectScanner.LocalDirEntry(webDir, projectDir)),
                ALL_FILES,
                List.of(), StandardCharsets.UTF_8);
        var results = scopedPaths(scannerWithRoot.query().list());
        assertThat(results).contains("web/index.html", "web/app/index.js");
        assertThat(results).allMatch(p -> p.startsWith("web/"));
    }

    @Test
    void mergeByPathFirstListWins() throws IOException {
        // "app" and "public" both have "style.css"
        var appFiles = scanner.query().scopeDirs("app").list();
        var publicFiles = scanner.query().scopeDirs("public").list();

        // app first → app's style.css wins
        var merged = ScanQueryBuilder.mergeByScopedPath(appFiles, publicFiles);
        var styleCss = merged.stream().filter(f -> f.scopedPath().equals("style.css")).toList();
        assertThat(styleCss).hasSize(1);
        assertThat(styleCss.get(0).indexPath()).startsWith("app/");

        // public first → public's style.css wins
        var mergedReversed = ScanQueryBuilder.mergeByScopedPath(publicFiles, appFiles);
        var styleCssReversed = mergedReversed.stream().filter(f -> f.scopedPath().equals("style.css")).toList();
        assertThat(styleCssReversed).hasSize(1);
        assertThat(styleCssReversed.get(0).indexPath()).startsWith("public/");
    }

    @Test
    void mergeByPathPreservesNonOverlapping() throws IOException {
        var appFiles = scanner.query().scopeDirs("app").list();
        var publicFiles = scanner.query().scopeDirs("public").list();

        var merged = ScanQueryBuilder.mergeByScopedPath(appFiles, publicFiles);
        var paths = merged.stream().map(ProjectFile::scopedPath).toList();
        // app-only files present
        assertThat(paths).contains("index.js", "page.html", "components/button.ts");
        // public-only files present
        assertThat(paths).contains("logo.png", "deep/nested/file.js");
    }

    @Test
    void declarationScopeDir() throws IOException {
        var declarations = List.of(ScanDeclarationBuildItem.of("app"));
        var declScanner = ProjectScanner.forPaths(
                List.of(Path.of("src/test/resources/web")),
                declarations,
                List.of());
        var results = scopedPaths(declScanner.query().list());
        assertThat(results).contains("app/index.js", "app/style.css", "app/page.html",
                "app/components/button.ts");
        assertThat(results).doesNotContain("index.html", "about.html", "tsconfig.json",
                "public/logo.png", "static/hello.txt");
    }

    @Test
    void declarationScopeDirWithGlob() throws IOException {
        var declarations = List.of(ScanDeclarationBuildItem.ofGlob("app", "**.html"));
        var declScanner = ProjectScanner.forPaths(
                List.of(Path.of("src/test/resources/web")),
                declarations,
                List.of());
        var results = scopedPaths(declScanner.query().list());
        assertThat(results).containsExactly("app/page.html");
    }

    @Test
    void declarationMultipleScopes() throws IOException {
        // Multiple scope dirs require constructing ScanQuery directly
        var declarations = List.of(new ScanDeclarationBuildItem(new ScanQuery(
                List.of("app/", "public/"), List.of(), List.of(), Set.of())));
        var declScanner = ProjectScanner.forPaths(
                List.of(Path.of("src/test/resources/web")),
                declarations,
                List.of());
        var results = scopedPaths(declScanner.query().list());
        assertThat(results).contains("app/index.js", "public/logo.png");
        assertThat(results).doesNotContain("index.html", "static/hello.txt");
    }

    @Test
    void declarationEmptyMeansEmptyIndex() throws IOException {
        var declScanner = ProjectScanner.forPaths(
                List.of(Path.of("src/test/resources/web")),
                List.of(),
                List.of());
        var results = scopedPaths(declScanner.query().list());
        assertThat(results).isEmpty();
    }

    @Test
    void declarationWithExcludesFiltersAtIndexTime() throws IOException {
        var declarations = List.of(ScanDeclarationBuildItem.of("app", List.of("glob:**.css")));
        var declScanner = ProjectScanner.forPaths(
                List.of(Path.of("src/test/resources/web")),
                declarations,
                List.of());
        var results = scopedPaths(declScanner.query().list());
        assertThat(results).contains("app/index.js", "app/page.html", "app/components/button.ts");
        assertThat(results).doesNotContain("app/style.css");
    }

    @Test
    void indexBaseFromDir() throws IOException {
        Path projectDir = Path.of("src/test/resources");
        Path webDir = projectDir.resolve("web");
        var scannerNoRoot = ProjectScanner.forPaths(
                List.of(),
                projectDir,
                List.of(new ProjectScanner.LocalDirEntry(webDir, webDir)),
                ALL_FILES,
                List.of(), StandardCharsets.UTF_8);
        var results = scopedPaths(scannerNoRoot.query().list());
        assertThat(results).contains("index.html", "app/index.js");
        assertThat(results).noneMatch(p -> p.startsWith("web/"));
    }
}
