package io.quarkiverse.web.bundler.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class TailwindCSSBundleTest {
    @Inject
    Bundle bundle;

    @Test
    void testBundled() {
        RestAssured.given()
                .basePath("")
                .get(UriBuilder.fromUri(bundle.resolve("app.css")).build())
                .then()
                .statusCode(200)
                .body(Matchers.containsString(".text-3xl"))
                .body(Matchers.containsString(".xl\\:no-underline"));
    }

}
