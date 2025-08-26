package io.quarkiverse.web.bundler.test;

import java.util.List;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerResponsiveTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .setForcedDependencies(
                    List.of(new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web"));

    @Test
    public void testResponsive() {

        RestAssured.given()
                .get("/responsive.html")
                .then()
                .statusCode(200)
                .log().body()
                .body(Matchers
                        .containsString(
                                "<img src=\"static/white_1920_1080.png\" srcset=\"/responsives/1b139664/white_1920_1080_640.png 640w, /responsives/1b139664/white_1920_1080_1024.png 1024w\"/>"));
        RestAssured.given()
                .get("/static/white_1920_1080.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/responsives/1b139664/white_1920_1080_640.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/responsives/1b139664/white_1920_1080_1024.png")
                .then()
                .statusCode(200);
    }
}
