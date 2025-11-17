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

public class WebBundlerEmptyTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.web-bundler.dependencies.compile-only", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web/index.html"));

    @Inject
    Bundle bundle;

    @Test
    public void test() {
        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString(" <script type=\"module\" src=\"" + bundle.script("app") + "\"></script>"));
        RestAssured.given()
                .basePath("")
                .get(bundle.script("app"))
                .then()
                .statusCode(200);
    }
}
