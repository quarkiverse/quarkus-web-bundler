package io.quarkiverse.web.bundler.test;

import java.util.List;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .setForcedDependencies(
                    List.of(new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web"));

    @Inject
    Bundle bundle;

    @Test
    public void test() {

        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("mode:TEST"))
                .body(Matchers.containsString("<link rel=\"stylesheet\" href=\"" + bundle.style("main") + "\" />"))
                .body(Matchers.containsString(" <script type=\"module\" src=\"" + bundle.script("main") + "\"></script>"));

        RestAssured.given()
                .get(bundle.style("main"))
                .then()
                .statusCode(200);
        RestAssured.given()
                .get(bundle.script("main"))
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/static/hello.txt")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("Hello World!"));

    }

    @Test
    void testSourceMap() {
        final String jsMap = bundle.resolve("main.js.map");
        Assertions.assertNotNull(jsMap);
        final String cssMap = bundle.resolve("main.css.map");
        Assertions.assertNotNull(cssMap);
        RestAssured.given()
                .get(jsMap)
                .then()
                .statusCode(200);
        RestAssured.given()
                .get(cssMap)
                .then()
                .statusCode(200);
    }
}
