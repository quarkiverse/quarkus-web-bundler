package io.quarkiverse.web.bundler.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class WebBundlerMixedOverrideTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withConfigurationResource("application-mixed-override.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("mixed-override", "web"));

    @Inject
    Bundle bundle;

    @Test
    public void testRootAndAppSameNamedFiles() {
        final String appCss = bundle.style("app");
        Assertions.assertNotNull(appCss, "app CSS bundle should exist");

        // Known: when web/index.css and web/app/index.css both exist, the root
        // file overwrites the app file (same destination path). Only the root
        // file's content survives.
        RestAssured.given()
                .basePath("")
                .get(appCss)
                .then()
                .statusCode(200)
                .body(containsString("color"))
                .body(not(containsString("font-family")));
    }
}
