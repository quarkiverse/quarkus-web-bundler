package io.quarkiverse.web.bundler.deployment.util;

import static io.quarkiverse.web.bundler.deployment.util.ConfiguredPaths.addTrailingSlash;
import static io.quarkiverse.web.bundler.deployment.util.ConfiguredPaths.prefixWithSlash;
import static io.quarkiverse.web.bundler.deployment.util.ConfiguredPaths.removeLeadingSlash;
import static io.quarkiverse.web.bundler.deployment.util.ConfiguredPaths.removeTrailingSlash;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfiguredPathsTest {

    @Test
    void test() {
        assertEquals("/hello", prefixWithSlash("hello"));
        assertEquals("/hello", prefixWithSlash("/hello"));
        assertEquals("/hello", removeTrailingSlash("/hello/"));
        assertEquals("/hello", prefixWithSlash("/hello"));
        assertEquals("hello", removeLeadingSlash("/hello"));
        assertEquals("hello", removeLeadingSlash("hello"));
        assertEquals("hello/", addTrailingSlash("hello"));
        assertEquals("hello/", addTrailingSlash("hello/"));
    }

}
