package io.quarkiverse.tools.projectscanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkiverse.tools.projectscanner.exception.DirOutsideRootException;
import io.quarkiverse.tools.projectscanner.util.ProjectUtils;

class ResolveSubDirTest {

    static final Path ROOT = Path.of("src/test/resources").toAbsolutePath().normalize();

    @Test
    void resolvesExistingSubDir() {
        Path result = ProjectUtils.resolveSubDir(ROOT, "web");
        assertThat(result).isNotNull();
        assertThat(result).isDirectory();
        assertThat(result).startsWith(ROOT);
    }

    @Test
    void resolvesNestedSubDir() {
        Path result = ProjectUtils.resolveSubDir(ROOT, "web/app");
        assertThat(result).isNotNull();
        assertThat(result).isDirectory();
    }

    @Test
    void returnsNullForNonExistentDir() {
        Path result = ProjectUtils.resolveSubDir(ROOT, "does-not-exist");
        assertThat(result).isNull();
    }

    @Test
    void throwsOnDotDotEscape() {
        assertThatThrownBy(() -> ProjectUtils.resolveSubDir(ROOT, "../../etc"))
                .isInstanceOf(DirOutsideRootException.class);
    }

    @Test
    void throwsOnAbsolutePath() {
        assertThatThrownBy(() -> ProjectUtils.resolveSubDir(ROOT, "/absolute/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void throwsOnNull() {
        assertThatThrownBy(() -> ProjectUtils.resolveSubDir(ROOT, (String) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnEmpty() {
        assertThatThrownBy(() -> ProjectUtils.resolveSubDir(ROOT, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnBlank() {
        assertThatThrownBy(() -> ProjectUtils.resolveSubDir(ROOT, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
