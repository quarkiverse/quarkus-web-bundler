package io.quarkiverse.web.bundler.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.deployment.items.QuteTemplateSourcePathBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ResponsivePathMapperBuildItem;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerResponsiveTest {

    public static class MyBuildStep implements BuildStep {

        @Override
        public void execute(BuildContext context) {
            try {
                byte[] bytes = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("/roq/index.html").readAllBytes();
                context.produce(TemplatePathBuildItem.builder()
                        .path("index.html")
                        .content(new String(bytes, StandardCharsets.UTF_8))
                        .extensionInfo("Roq")
                        .build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            context.produce(new QuteTemplateSourcePathBuildItem("index.html",
                    java.nio.file.Path.of("target/test-classes/roq/index.html")));
            context.produce(new ResponsivePathMapperBuildItem(new Mapper()));
        }

        private static class Mapper implements Function<String, String> {
            @Override
            public String apply(String s) {
                return s.replace('é', '-').toLowerCase();
            }
        }
    }

    public static class Customiser implements Consumer<BuildChainBuilder> {

        @Override
        public void accept(BuildChainBuilder buildChainBuilder) {
            buildChainBuilder.addBuildStep(new MyBuildStep())
                    .produces(ResponsivePathMapperBuildItem.class)
                    .produces(TemplatePathBuildItem.class)
                    .produces(QuteTemplateSourcePathBuildItem.class)
                    .build();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application.properties")
            .addBuildChainCustomizer(new Customiser())
            .setForcedDependencies(
                    List.of(new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Endpoint.class)
                    .addAsResource("roq")
                    .addAsResource("web"));

    @Test
    public void testResponsiveBuildTime() {

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

    @Test
    public void testResponsiveRunTime() {

        RestAssured.given()
                .get("/rest")
                .then()
                .statusCode(200)
                .log().body()
                // FIXME There should actually be a newline in there but for some reason it gets removed
                .body(Matchers
                        .containsString(
                                "<img src=\"white_1920_1080.png\" srcset=\"/responsives/1b139664/white_1920_1080_640.png 640w, /responsives/1b139664/white_1920_1080_1024.png 1024w\"/>"
                                        + "<img src=\"/static/white_1920_1080.png\" srcset=\"/responsives/1b139664/white_1920_1080_640.png 640w, /responsives/1b139664/white_1920_1080_1024.png 1024w\"/>"
                                        + "<img src=\"fo-/fo-_1920_1080.png\" srcset=\"/responsives/1b139664/fo-_1920_1080_640.png 640w, /responsives/1b139664/fo-_1920_1080_1024.png 1024w\"/>"));
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
        RestAssured.given()
                .get("/responsives/1b139664/fo-_1920_1080_640.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/responsives/1b139664/fo-_1920_1080_1024.png")
                .then()
                .statusCode(200);
    }

    @Path("/rest")
    public static class Endpoint {
        @Inject
        @Location("index.html")
        Template index;

        @GET
        public String get() {
            return index.instance().render();
        }
    }
}
