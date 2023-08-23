package io.quarkiverse.web.bundler.it;

import jakarta.inject.Inject;

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
                        "main.css.map",
                        "chunk.js",
                        "main.css",
                        "page1.js",
                        "page1.js.map",
                        "page1.css",
                        "chunk.js.map",
                        "page1.css.map",
                        "main.js.map",
                        "main.js");

        for (String name : bundle.mapping().names()) {
            RestAssured.get(bundle.resolve(name))
                    .then()
                    .statusCode(200);
        }
    }

}
