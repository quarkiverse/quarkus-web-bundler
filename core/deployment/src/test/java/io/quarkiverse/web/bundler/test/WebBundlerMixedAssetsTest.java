package io.quarkiverse.web.bundler.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerMixedAssetsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application-mixed.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("mixed", "web"));

    @Inject
    Bundle bundle;

    @Test
    public void testMixedRootAndAppDirAssets() {
        final String appCss = bundle.style("app");
        org.junit.jupiter.api.Assertions.assertNotNull(appCss, "app CSS bundle should exist");

        RestAssured.given()
                .basePath("")
                .get(appCss)
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("font-family"))
                .body(org.hamcrest.Matchers.containsString("color"));
    }
}
