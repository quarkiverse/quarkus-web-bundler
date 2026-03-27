package io.quarkiverse.tools.stringpaths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StringPathsTest {

    @Test
    void isRooted() {
        assertThat(StringPaths.isRooted("/foo")).isTrue();
        assertThat(StringPaths.isRooted("\\foo")).isTrue();
        assertThat(StringPaths.isRooted("C:\\foo")).isTrue();
        assertThat(StringPaths.isRooted("D:/bar")).isTrue();
        assertThat(StringPaths.isRooted("foo/bar")).isFalse();
        assertThat(StringPaths.isRooted("foo")).isFalse();
        assertThat(StringPaths.isRooted("")).isFalse();
    }

    @Test
    void toUnixPath() {
        assertThat(StringPaths.toUnixPath("foo\\bar\\baz")).isEqualTo("foo/bar/baz");
        assertThat(StringPaths.toUnixPath("foo/bar")).isEqualTo("foo/bar");
        assertThat(StringPaths.toUnixPath("")).isEqualTo("");
    }

    @Test
    void prefixWithSlash() {
        assertThat(StringPaths.prefixWithSlash("foo")).isEqualTo("/foo");
        assertThat(StringPaths.prefixWithSlash("/foo")).isEqualTo("/foo");
        assertThat(StringPaths.prefixWithSlash("")).isEqualTo("/");
    }

    @Test
    void surroundWithSlashes() {
        assertThat(StringPaths.surroundWithSlashes("foo")).isEqualTo("/foo/");
        assertThat(StringPaths.surroundWithSlashes("/foo/")).isEqualTo("/foo/");
        assertThat(StringPaths.surroundWithSlashes("foo/")).isEqualTo("/foo/");
    }

    @Test
    void addTrailingSlash() {
        assertThat(StringPaths.addTrailingSlash("foo")).isEqualTo("foo/");
        assertThat(StringPaths.addTrailingSlash("foo/")).isEqualTo("foo/");
        assertThat(StringPaths.addTrailingSlash("")).isEqualTo("/");
    }

    @Test
    void addTrailingSlashIfNoExt() {
        assertThat(StringPaths.addTrailingSlashIfNoExt("foo")).isEqualTo("foo/");
        assertThat(StringPaths.addTrailingSlashIfNoExt("foo/")).isEqualTo("foo/");
        assertThat(StringPaths.addTrailingSlashIfNoExt("foo.html")).isEqualTo("foo.html");
        assertThat(StringPaths.addTrailingSlashIfNoExt("dir/foo.js")).isEqualTo("dir/foo.js");
    }

    @Test
    void join() {
        assertThat(StringPaths.join("foo", "bar")).isEqualTo("foo/bar");
        assertThat(StringPaths.join("foo/", "bar")).isEqualTo("foo/bar");
        assertThat(StringPaths.join("foo", "/bar")).isEqualTo("foo/bar");
        assertThat(StringPaths.join("foo/", "/bar")).isEqualTo("foo/bar");
        assertThat(StringPaths.join("foo", null)).isEqualTo("foo");
        assertThat(StringPaths.join("", "bar")).isEqualTo("bar");
    }

    @Test
    void removeLeadingSlash() {
        assertThat(StringPaths.removeLeadingSlash("/foo")).isEqualTo("foo");
        assertThat(StringPaths.removeLeadingSlash("foo")).isEqualTo("foo");
        assertThat(StringPaths.removeLeadingSlash("/")).isEqualTo("");
    }

    @Test
    void removeTrailingSlash() {
        assertThat(StringPaths.removeTrailingSlash("foo/")).isEqualTo("foo");
        assertThat(StringPaths.removeTrailingSlash("foo")).isEqualTo("foo");
        assertThat(StringPaths.removeTrailingSlash("/")).isEqualTo("");
    }

    @Test
    void stripPrefix() {
        assertThat(StringPaths.stripPrefix("web/app/index.js", "web")).isEqualTo("app/index.js");
        assertThat(StringPaths.stripPrefix("web/app/index.js", "web/")).isEqualTo("app/index.js");
        assertThat(StringPaths.stripPrefix("other/index.js", "web")).isEqualTo("other/index.js");
        assertThat(StringPaths.stripPrefix("web", "web")).isEqualTo("web");
    }

    @Test
    void removeExtension() {
        assertThat(StringPaths.removeExtension("foo.js")).isEqualTo("foo");
        assertThat(StringPaths.removeExtension("foo.bar.js")).isEqualTo("foo.bar");
        assertThat(StringPaths.removeExtension("foo")).isEqualTo("foo");
        assertThat(StringPaths.removeExtension("dir/foo.js")).isEqualTo("dir/foo");
    }

    @Test
    void fileExtension() {
        assertThat(StringPaths.fileExtension("foo.js")).isEqualTo("js");
        assertThat(StringPaths.fileExtension("foo.bar.js")).isEqualTo("js");
        assertThat(StringPaths.fileExtension("foo")).isNull();
        assertThat(StringPaths.fileExtension("dir/foo.ts")).isEqualTo("ts");
    }

    @Test
    void fileName() {
        assertThat(StringPaths.fileName("dir/foo.js")).isEqualTo("foo.js");
        assertThat(StringPaths.fileName("foo.js")).isEqualTo("foo.js");
        assertThat(StringPaths.fileName("a/b/c")).isEqualTo("c");
        assertThat(StringPaths.fileName("dir/")).isEqualTo("");
    }

    @Test
    void slugify() {
        assertThat(StringPaths.slugify("Hello World!", false, false)).isEqualTo("Hello-World");
        assertThat(StringPaths.slugify("foo/bar.baz", true, true)).isEqualTo("foo/bar.baz");
        assertThat(StringPaths.slugify("foo/bar.baz", false, false)).isEqualTo("foo-bar-baz");
        assertThat(StringPaths.slugify("foo/bar.baz", true, false)).isEqualTo("foo/bar-baz");
        assertThat(StringPaths.slugify("foo/bar.baz", false, true)).isEqualTo("foo-bar.baz");
        assertThat(StringPaths.slugify("a--b", false, false)).isEqualTo("a-b");
    }

    @Test
    void subPathFrom() {
        // At start
        assertThat(StringPaths.subPathFrom("content/posts/hello.md", "content")).isEqualTo("posts/hello.md");
        // In middle
        assertThat(StringPaths.subPathFrom("roq/content/posts/hello.md", "content")).isEqualTo("posts/hello.md");
        // Deeper nesting
        assertThat(StringPaths.subPathFrom("a/b/content/posts/hello.md", "content")).isEqualTo("posts/hello.md");
        // First match wins when segment appears more than once
        assertThat(StringPaths.subPathFrom("content/content/x.md", "content")).isEqualTo("content/x.md");
        assertThat(StringPaths.subPathFrom("roq/content/content/y.md", "content")).isEqualTo("content/y.md");
        assertThat(StringPaths.subPathFrom("content/other/content/z.md", "content")).isEqualTo("other/content/z.md");
        // Not a full segment (hyphenated prefix)
        assertThat(StringPaths.subPathFrom("my-content/posts/hello.md", "content")).isEqualTo("my-content/posts/hello.md");
        // Not a full segment (starts with segment name)
        assertThat(StringPaths.subPathFrom("contentious/hello.md", "content")).isEqualTo("contentious/hello.md");
        // Not a full segment (suffix substring)
        assertThat(StringPaths.subPathFrom("roq/not-content/hello.md", "content")).isEqualTo("roq/not-content/hello.md");
        // Not found
        assertThat(StringPaths.subPathFrom("hello.md", "content")).isEqualTo("hello.md");
        // Entire path equals segment
        assertThat(StringPaths.subPathFrom("content", "content")).isEqualTo("");
        // Trailing slash only
        assertThat(StringPaths.subPathFrom("content/", "content")).isEqualTo("");
        // At end
        assertThat(StringPaths.subPathFrom("roq/content", "content")).isEqualTo("");
        // Segment with trailing slash input
        assertThat(StringPaths.subPathFrom("x/content/y", "content/")).isEqualTo("y");
    }

    @Test
    void nullInputsThrow() {
        assertThatThrownBy(() -> StringPaths.isRooted(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.toUnixPath(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.prefixWithSlash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.addTrailingSlash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.join(null, "b")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.removeLeadingSlash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.removeTrailingSlash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.stripPrefix(null, "p")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.stripPrefix("p", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.removeExtension(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.fileExtension(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.fileName(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.slugify(null, false, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.subPathFrom(null, "s")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StringPaths.subPathFrom("p", null)).isInstanceOf(NullPointerException.class);
    }
}
