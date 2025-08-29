package io.quarkiverse.web.bundler.it;

import java.net.URL;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ResponsiveTest {
    @TestHTTPResource("/")
    URL url;

    @Test
    void responsive() {
        RestAssured.given()
                .get("/")
                .then()
                .statusCode(200)
                .log().body()
                .body(Matchers
                        .containsString(
                                "<img src=\"/static/white_1920_1080.png\" srcset=\"/responsives/1b139664/white_1920_1080_640.png 640w, /responsives/1b139664/white_1920_1080_1024.png 1024w\"/>"));
        RestAssured.given()
                .get("/static/white_1920_1080.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/responsives/1b139664/white_1920_1080_640.png")
                .then()
                .statusCode(200);
        RestAssured.given()
                .get("/responsives/1b139664/white_1920_1080_1024.png")
                .then()
                .statusCode(200);
    }

}
