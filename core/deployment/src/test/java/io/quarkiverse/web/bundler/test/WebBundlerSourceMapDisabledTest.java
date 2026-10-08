package io.quarkiverse.web.bundler.test;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.test.QuarkusExtensionTest;

public class WebBundlerSourceMapDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.web-bundler.bundling.source-map", "false")
            .setForcedDependencies(
                    List.of(new ArtifactDependency("org.mvnpm", "jquery", null, "jar", "3.7.0", "provided", false)))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("web"));

    @Inject
    Bundle bundle;

    @Test
    public void test() {
        Assertions.assertNull(bundle.resolve("app.js.map"));
        Assertions.assertNull(bundle.resolve("app.css.map"));
    }
}
