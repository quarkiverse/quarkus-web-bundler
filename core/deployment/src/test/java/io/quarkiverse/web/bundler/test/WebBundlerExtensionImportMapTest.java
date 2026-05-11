package io.quarkiverse.web.bundler.test;

import java.nio.file.Path;
import java.util.Map;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.vertx.http.deployment.spi.WebDependencyJarBuildItem;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

/**
 * An extension declaring its web modules through {@link WebDependencyJarBuildItem} import mappings:
 * the modules are served by the extension, so they must not be bundled and must be resolvable through the import map.
 */
public class WebBundlerExtensionImportMapTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application-mixed.properties")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("extension-import-map", "web"))
            .addBuildChainCustomizer(b -> b.addBuildStep(context -> context.produce(new WebDependencyJarBuildItem(
                    ArtifactKey.ga("org.acme", "acme-greeter"),
                    Path.of("acme-greeter.jar"),
                    Map.of(
                            "@acme/greeter", "/_static/acme-greeter/greeter.js",
                            "@acme/greeter/", "/_static/acme-greeter/"))))
                    .produces(WebDependencyJarBuildItem.class)
                    .build());

    @Inject
    Bundle bundle;

    @Test
    void extensionModulesAreExternal() {
        final String script = bundle.script("app");
        Assertions.assertNotNull(script, "app script bundle should exist");
        RestAssured.given()
                .basePath("")
                .get(script)
                .then()
                .statusCode(200)
                .body(Matchers.containsString("\"@acme/greeter\""))
                .body(Matchers.containsString("\"@acme/greeter/extra.js\""));
    }

    @Test
    void extensionMappingsAreInTheImportMap() {
        final JsonObject imports = new JsonObject(bundle.importMap()).getJsonObject("imports");
        Assertions.assertEquals("/_static/acme-greeter/greeter.js", imports.getString("@acme/greeter"));
        Assertions.assertEquals("/_static/acme-greeter/", imports.getString("@acme/greeter/"));
        Assertions.assertEquals(bundle.script("app"), imports.getString("app"));
    }
}
