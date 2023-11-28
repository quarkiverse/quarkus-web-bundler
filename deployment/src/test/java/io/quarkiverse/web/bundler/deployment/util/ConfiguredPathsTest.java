package io.quarkiverse.web.bundler.deployment.util;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.join;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.removeLeadingSlash;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.removeTrailingSlash;
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
        assertEquals("hello/foo", join("hello/", "foo"));
        assertEquals("hello/foo/", join("hello/", "foo/"));
        assertEquals("http://hello/foo/", join("http://hello", "/foo/"));
        assertEquals("http://hello/foo/", join("http://hello", "foo/"));
    }

}
