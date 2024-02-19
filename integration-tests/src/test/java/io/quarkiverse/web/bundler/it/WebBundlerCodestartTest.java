package io.quarkiverse.web.bundler.it;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;
import static io.quarkus.devtools.testing.SnapshotTesting.checkContains;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class WebBundlerCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .setupStandaloneExtensionTest("io.quarkiverse.web-bundler:quarkus-web-bundler")
            .languages(JAVA)
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.assertThatGeneratedTreeMatchSnapshots(JAVA, "src/main/resources/web");
        codestartTest.assertThatGeneratedFile(JAVA, ".gitignore")
                .satisfies(checkContains("node_modules/"));
    }

    @Test
    void testBuild() throws Throwable {
        codestartTest.buildAllProjects();
    }

}
