package io.quarkiverse.web.bundler.test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class WebBundlerDevModeWatchTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("dev", "web")
                    .addAsResource("application.properties"));

    @Test
    public void test() {
        RestAssured.basePath = "";
        RestAssured.given()
                .get("/foo/bar/")
                .then()
                .statusCode(200)
                .body(containsString("Hello Qute Static!"));
        test.modifyResourceFile("web/index.html", s -> s.replace("Hello Qute Static!", "Hello Qute Static! Modified!"));
        RestAssured.given()
                .get("/foo/bar/")
                .then()
                .statusCode(200)
                .body(containsString("Hello Qute Static! Modified!"));
        RestAssured.given()
                .get("/foo/bar/static/bundle/app.js")
                .then()
                .statusCode(200)
                .body(containsString("console.log(\"Hello World!\");"));
        test.modifyResourceFile("web/app.js", s -> s.replace("Hello World!", "Hello World! Modified!"));
        awaitContent("/foo/bar/static/bundle/app.js", "console.log(\"Hello World! Modified!\");");
        // Test public/static resource change
        RestAssured.given()
                .get("/foo/bar/static/hello.txt")
                .then()
                .statusCode(200)
                .body(containsString("Hello World!"));
        test.modifyResourceFile("web/static/hello.txt", s -> s.replace("Hello World!", "Hello Static Modified!"));
        awaitContent("/foo/bar/static/hello.txt", "Hello Static Modified!");

        test.modifyResourceFile("web/app.css", s -> s.replace("background-color: #6b6bf5;", "background-color: #123456;"));
        test.modifyResourceFile("web/other.scss", s -> s.replace("color: #AAAAAA;", "color: #567890;"));
        awaitContent("/foo/bar/static/bundle/app.css", "background-color: #123456;");
        RestAssured.given()
                .get("/foo/bar/static/bundle/app.css")
                .then()
                .statusCode(200)
                .body(containsString("color: #567890;"));
    }

    private static void awaitContent(String path, String expected) {
        await().pollInterval(300, MILLISECONDS)
                .atMost(10, SECONDS)
                .untilAsserted(() -> RestAssured.given()
                        .get(path)
                        .then()
                        .statusCode(200)
                        .body(containsString(expected)));
    }

}
