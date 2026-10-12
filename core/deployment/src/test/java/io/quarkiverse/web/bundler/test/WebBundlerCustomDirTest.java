package io.quarkiverse.web.bundler.test;

import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class WebBundlerCustomDirTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withConfigurationResource("application-mixed-custom-dir.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("mixed-custom-dir", "web"));

    @Inject
    Bundle bundle;

    @Test
    public void testCustomDirEntryPoint() {
        final String appCss = bundle.style("app");
        Assertions.assertNotNull(appCss, "app CSS bundle should exist");

        RestAssured.given()
                .basePath("")
                .get(appCss)
                .then()
                .statusCode(200)
                .body(containsString("font-family"));
    }
}
