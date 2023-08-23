package io.quarkiverse.web.bundler.it;

import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class StaticAssetsTest {

    @TestHTTPResource("/static/images/logo.svg")
    URL logo;

    @TestHTTPResource("/static/hello.txt")
    URL txt;

    @TestHTTPResource("/static/dir/hello.md")
    URL md;

    @Test
    void testLogo() throws IOException {
        RestAssured.get(logo)
                .then()
                .statusCode(200)
                .body(is(asString("/web/static/images/logo.svg")));
    }

    @Test
    void testMD() throws IOException {
        RestAssured.get(md)
                .then()
                .statusCode(200)
                .body(is(asString("/web/static/dir/hello.md")));
    }

    @Test
    void testTXT() throws IOException {
        RestAssured.get(txt)
                .then()
                .statusCode(200)
                .body(is(asString("/web/static/hello.txt")));
    }

    private static String asString(String name) throws IOException {
        try (InputStream resourceAsStream = StaticAssetsTest.class.getResourceAsStream(name)) {
            return new String(resourceAsStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
