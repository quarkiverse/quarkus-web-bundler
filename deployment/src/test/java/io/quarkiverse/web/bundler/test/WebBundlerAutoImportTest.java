package io.quarkiverse.web.bundler.test;

import java.util.List;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerAutoImportTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setForcedDependencies(
                    List.of(
                            new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web-auto", "web")
                    .addAsResource("application-auto.properties", "application.properties"));

    @Inject
    Bundle bundle;

    @Test
    public void test() {
        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("mode:TEST"))
                .body(Matchers.containsString("<!-- no style found for key 'main' in Bundler mapping !-->"))
                .body(Matchers.containsString(" <script type=\"module\" src=\"" + bundle.script("main") + "\"></script>"));
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
}
