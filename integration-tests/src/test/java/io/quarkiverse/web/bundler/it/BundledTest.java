package io.quarkiverse.web.bundler.it;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundled;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class BundledTest {

    @Inject
    Bundled bundled;

    @Test
    void testBundled() {

        Assertions.assertThat(bundled.mapping().names())
                .containsExactlyInAnyOrder(
                        "ajax-loader.gif",
                        "slick.eot",
                        "main.css",
                        "main.css.map",
                        "slick.ttf",
                        "page1.css.map",
                        "chunk.js.map",
                        "chunk.js",
                        "page1.css",
                        "page1.js.map",
                        "main.js",
                        "slick.woff",
                        "slick.svg",
                        "page1.js",
                        "main.js.map");

        for (String name : bundled.mapping().names()) {
            RestAssured.get(bundled.resolve(name))
                    .then()
                    .statusCode(200);
        }
    }

}
