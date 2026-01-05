package io.quarkiverse.web.bundler.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class SvelteBundleTest {
    @Inject
    Bundle bundle;

    @Test
    void testBundled() {
        RestAssured.given()
                .basePath("")
                .get(UriBuilder.fromUri(bundle.resolve("app.js")).build())
                .then()
                .statusCode(200)
                .body(Matchers.containsString(
                        "<g class=\"tick svelte-"))
                .body(Matchers.containsString(
                        "polyline.svelte-"));

    }

}
