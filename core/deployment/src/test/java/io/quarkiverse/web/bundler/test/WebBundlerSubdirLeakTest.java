package io.quarkiverse.web.bundler.test;

import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class WebBundlerSubdirLeakTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application-mixed-subdir.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("mixed-subdir", "web"));

    @Inject
    Bundle bundle;

    @Test
    public void testNonStandardSubdirBundledWithApp() {
        final String appCss = bundle.style("app");
        Assertions.assertNotNull(appCss, "app CSS bundle should exist");

        // Non-standard subdirectories (e.g. web/utils/) are collected by the root
        // scan and bundled together with the app entry point.
        RestAssured.given()
                .basePath("")
                .get(appCss)
                .then()
                .statusCode(200)
                .body(containsString("font-family"))
                .body(containsString("margin-top"));
    }
}
