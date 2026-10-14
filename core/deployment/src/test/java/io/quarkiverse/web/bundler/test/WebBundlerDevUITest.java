package io.quarkiverse.web.bundler.test;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class WebBundlerDevUITest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("dev", "web")
                    .addAsResource(new StringAsset("quarkus.http.port=0"), "application.properties"));

    @Test
    public void entryPointsLinkToSource() {
        RestAssured.basePath = "";
        RestAssured.given()
                .get("/q/dev-ui/quarkus-web-bundler-common-data.js")
                .then()
                .statusCode(200)
                .body(Matchers.containsString(
                        "{\"path\":\"app.js\",\"type\":\"generated entry-point\",\"source\":null}"))
                .body(Matchers.matchesPattern(
                        "(?s).*\\{\"path\":\"app\\.js\",\"type\":\"auto-imported\",\"source\":\"file://[^\"]*src[/\\\\]+main[/\\\\]+resources[/\\\\]+web[/\\\\]+app\\.js\"}.*"));
    }
}
