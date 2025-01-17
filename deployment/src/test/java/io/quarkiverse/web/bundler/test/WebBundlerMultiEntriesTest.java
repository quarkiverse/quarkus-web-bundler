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

public class WebBundlerMultiEntriesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application-multi.properties")
            .setForcedDependencies(
                    List.of(new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("multi", "web"));

    @Inject
    Bundle bundle;

    @Test
    public void test() {

        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Hello Qute Static!"))
                .body(Matchers.containsString("<link rel=\"stylesheet\" href=\"" + bundle.style("index") + "\" />"))
                .body(Matchers.containsString(" <script type=\"module\" src=\"" + bundle.script("index") + "\"></script>"));

        RestAssured.given()
                .get("/page1.html")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("<link rel=\"stylesheet\" href=\"" + bundle.style("page1") + "\" />"))
                .body(Matchers.containsString(" <script type=\"module\" src=\"" + bundle.script("page1") + "\"></script>"));

        RestAssured.given()
                .basePath("")
                .get(bundle.style("index"))
                .then()
                .statusCode(200);
        RestAssured.given()
                .basePath("")
                .get(bundle.script("index"))
                .then()
                .statusCode(200)
                .body(Matchers.containsString("console.log(\"Hello World!\");"));

        RestAssured.given()
                .basePath("")
                .get(bundle.style("page1"))
                .then()
                .statusCode(200);
        RestAssured.given()
                .basePath("")
                .get(bundle.script("page1"))
                .then()
                .statusCode(200)
                .body(Matchers.containsString("console.log(\"Hello World!\");"));

        RestAssured.given()
                .get("/hello.txt")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("Hello World!"));

    }

    @Test
    void testSourceMap() {
        final String jsMap = bundle.resolve("index.js.map");
        Assertions.assertNotNull(jsMap);
        final String cssMap = bundle.resolve("index.css.map");
        Assertions.assertNotNull(cssMap);
        RestAssured.given()
                .basePath("")
                .get(jsMap)
                .then()
                .statusCode(200);
        RestAssured.given()
                .basePath("")
                .get(cssMap)
                .then()
                .statusCode(200);
    }
}
