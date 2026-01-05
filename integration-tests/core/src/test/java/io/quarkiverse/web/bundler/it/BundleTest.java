package io.quarkiverse.web.bundler.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class BundleTest {

    @Inject
    Bundle bundle;

    @Test
    void testBundled() {

        Assertions.assertThat(bundle.mapping().names())
                .containsExactlyInAnyOrder(
                        "app.css.map",
                        "chunk.js",
                        "app.css",
                        "page-1.js",
                        "page-1.js.map",
                        "page-1.css",
                        "chunk.js.map",
                        "page-1.css.map",
                        "app.js.map",
                        "app.js");

        for (String name : bundle.mapping().names()) {
            RestAssured.given()
                    .basePath("")
                    .get(UriBuilder.fromUri(bundle.resolve(name)).build())
                    .then()
                    .statusCode(200);
        }
    }

}
