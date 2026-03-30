package io.quarkiverse.web.bundler.test;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Regression test for <a href="https://github.com/quarkiverse/quarkus-web-bundler/issues/409">#409</a>:
 * a custom tsconfig.json in the web root (with no app/ subdir) should not cause a duplicate key exception.
 */
public class WebBundlerTsConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.web-bundler.dependencies.compile-only", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("tsconfig", "web"));

    @Inject
    Bundle bundle;

    @Test
    public void testBundleWithTsConfig() {
        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("<script type=\"module\" src=\"" + bundle.script("app") + "\"></script>"));
        RestAssured.given()
                .basePath("")
                .get(bundle.script("app"))
                .then()
                .statusCode(200);
    }
}