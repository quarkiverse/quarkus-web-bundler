package io.quarkiverse.web.bundler.test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.LogRecord;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class WebBundlerDevModeTsConfigTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setAllowFailedStart(true)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("dev-tsconfig", "web")
                    .addAsResource("application.properties"));

    @Test
    void shouldKeepTsConfigOnlyUnderDevWeb() {
        Assertions.assertFalse(hasDuplicateKeyFailure(),
                "Dev mode should start without Duplicate key for web/tsconfig.json");

        RestAssured.given()
                .get("/foo/bar/static/bundle/app.js")
                .then()
                .statusCode(200)
                .body(Matchers.allOf(Matchers.containsString("""
                        function greet(name) {
                          return `Hello, ${name}!`;
                        }"""), Matchers.containsString("console.log(greet(\"World\"));")));

        final Path deploymentDir = deploymentDir();

        Assertions.assertNotNull(deploymentDir, "Deployment directory should be available during dev mode test");

        final Path webBundlerDevDir = deploymentDir.resolve("target").resolve("web-bundler").resolve("dev");

        Assertions.assertTrue(Files.exists(webBundlerDevDir.resolve("web").resolve("tsconfig.json")),
                "tsconfig.json should be placed under target/web-bundler/dev/web");
        Assertions.assertTrue(Files.exists(webBundlerDevDir.resolve("tsconfig.json")),
                "tsconfig.json should be placed at target/web-bundler/dev");
    }

    private static boolean hasDuplicateKeyFailure() {
        return test.getLogRecords().stream()
                .map(LogRecord::getMessage)
                .anyMatch(message -> message != null
                        && message.contains("Duplicate key")
                        && message.contains("tsconfig.json"));
    }

    private static Path deploymentDir() {
        try {
            final Field field = QuarkusDevModeTest.class.getDeclaredField("deploymentDir");

            field.setAccessible(true);

            return (Path) field.get(test);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to access QuarkusDevModeTest deploymentDir", e);
        }
    }
}
