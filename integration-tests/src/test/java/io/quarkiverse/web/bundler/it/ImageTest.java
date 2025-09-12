package io.quarkiverse.web.bundler.it;

import java.net.URL;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ImageTest {
    @TestHTTPResource("/")
    URL url;

    @Test
    void image() {
        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .log().body()
                .body(Matchers
                        .containsString(
                                "<img src=\"/static/white_1920_1080.png\" srcset=\"/static/processed-images/1b139664/white_1920_1080_640.png 640w, /static/processed-images/1b139664/white_1920_1080_1024.png 1024w\"/>"));
        RestAssured.given()
                .get("/static/white_1920_1080.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/static/processed-images/1b139664/white_1920_1080_640.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/static/processed-images/1b139664/white_1920_1080_1024.png")
                .then()
                .statusCode(200);
    }

}
