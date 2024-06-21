package io.quarkiverse.web.bundler.test;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class WebBundlerDevModeTest {

    // Start hot reload (DevMode) test with your extension loaded

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("dev", "web")
                    .addAsResource("application.properties"));

    @Test
    public void test() throws InterruptedException {
        RestAssured.given()
                .get("/foo/bar/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Hello Qute Static!"));
        test.modifyResourceFile("web/index.html", s -> s.replace("Hello Qute Static!", "Hello Qute Static! Modified!"));
        RestAssured.given()
                .get("/foo/bar/")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Hello Qute Static! Modified!"));
        RestAssured.given()
                .get("/foo/bar/static/bundle/main.js")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("console.log(\"Hello World!\");"));
        test.modifyResourceFile("web/app/app.js", s -> s.replace("Hello World!", "Hello World! Modified!"));
        RestAssured.given()
                .get("/foo/bar/static/bundle/main.js")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("console.log(\"Hello World! Modified!\");"));
        test.modifyResourceFile("web/app/app.css", s -> s.replace("background-color: #6b6bf5;", "background-color: #123456;"));
        test.modifyResourceFile("web/app/other.scss", s -> s.replace("color: #AAAAAA;", "color: #567890;"));
        Thread.sleep(500);
        RestAssured.given()
                .get("/foo/bar/static/bundle/main.css")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("background-color: #123456;"))
                .body(Matchers.containsString("color: #567890;"));
    }
}
