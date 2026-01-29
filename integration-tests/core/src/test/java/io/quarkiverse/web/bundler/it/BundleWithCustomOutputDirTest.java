package io.quarkiverse.web.bundler.it;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;

import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

@QuarkusTest
@TestProfile(BundleWithCustomOutputDirTest.CustomPackageOutputDirectory.class)
public class BundleWithCustomOutputDirTest {

    @Inject
    Bundle bundle;

    @Test
    void testBundled() {
        for (String name : bundle.mapping().names()) {
            RestAssured.given()
                    .basePath("")
                    .get(UriBuilder.fromUri(bundle.resolve(name)).build())
                    .then()
                    .statusCode(200);
        }
    }

    public static class CustomPackageOutputDirectory implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.package.output-directory", "some/custom/package/output/directory");
        }
    }
}
