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

public class ScssDevModeErrorTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.vertx.caching=false"), "application.properties")
                    .addAsManifestResource(new StringAsset("nothing{}"),
                            "resources/_base.scss")
                    .addAsManifestResource(new StringAsset("@use 'base';\n"
                            + "\n"
                            + "nada{}"),
                            "resources/styles.scss"));

    @TestHTTPResource
    URL url;

    @Test
    public void testError() {
        System.err.println("Adding mistake");
        config.modifyResourceFile("META-INF/resources/_base.scss", file -> "error;");
        RestAssured.get("/anything")
                .then()
                .statusCode(500)
                .body(Matchers.containsString("Error restarting Quarkus - Found 1 SASS problem"))
                .body(Matchers.containsString("META-INF/resources/_base.scss:1:6"))
                .body(Matchers.containsString("error;"))
                .body(Matchers.containsString("======^"));
        System.err.println("Fixing mistake");
        // now try to reset it
        config.modifyResourceFile("META-INF/resources/_base.scss", file -> "noerror{}");
        RestAssured.get("/styles.css")
                .then()
                .statusCode(200);
        System.err.println("Done");
    }
}
