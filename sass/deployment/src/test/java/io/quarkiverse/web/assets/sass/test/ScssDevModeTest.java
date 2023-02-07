package io.quarkiverse.web.assets.sass.test;

import java.net.URL;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;

public class ScssDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.vertx.caching=false"), "application.properties")
                    .addAsManifestResource(new StringAsset("$primary-color: #333;"),
                            "resources/_base.scss")
                    .addAsManifestResource(new StringAsset("@use 'base';\n"
                            + "\n"
                            + ".something {\n"
                            + "  color: base.$primary-color;\n"
                            + "}"),
                            "resources/styles.scss"));

    @TestHTTPResource
    URL url;

    @Test
    public void testCssModification() {
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".something {\n"
                        + "  color: #333;\n"
                        + "}"));
        // direct modification
        config.modifyResourceFile("META-INF/resources/styles.scss", s -> s.replace(".something", ".other"));
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".other {\n"
                        + "  color: #333;\n"
                        + "}"));
        // modification of an imported file
        config.modifyResourceFile("META-INF/resources/_base.scss", s -> s.replace("333", "444"));
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".other {\n"
                        + "  color: #444;\n"
                        + "}"));
    }

    @Test
    public void testDependencyModification() {
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".something {\n"
                        + "  color: #333;\n"
                        + "}"));
        // new dependency
        config.modifyResourceFile("META-INF/resources/styles.scss", s -> s.replace("base", "newbase"));
        config.addResourceFile("META-INF/resources/_newbase.scss", "$primary-color: #666;");
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".something {\n"
                        + "  color: #666;\n"
                        + "}"));
        // modification of the new dependency
        config.modifyResourceFile("META-INF/resources/_newbase.scss", s -> s.replace("666", "444"));
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".something {\n"
                        + "  color: #444;\n"
                        + "}"));
    }

    @Test
    public void testCssDeletion() {
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(200)
                .body(Matchers.is(".something {\n"
                        + "  color: #333;\n"
                        + "}"));
        config.deleteResourceFile("META-INF/resources/styles.scss");
        RestAssured
                .when()
                .get("/styles.css").then()
                .statusCode(404);
    }

    @Test
    public void testCssAddition() {
        RestAssured
                .when()
                .get("/newstyles.css").then()
                .statusCode(404);
        config.addResourceFile("META-INF/resources/newstyles.scss", "@use 'base';\n"
                + "\n"
                + ".something {\n"
                + "  color: base.$primary-color;\n"
                + "}");
        RestAssured
                .when()
                .get("/newstyles.css").then()
                .statusCode(200)
                .body(Matchers.is(".something {\n"
                        + "  color: #333;\n"
                        + "}"));
    }
}
