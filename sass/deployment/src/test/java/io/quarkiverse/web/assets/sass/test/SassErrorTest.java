package io.quarkiverse.web.assets.sass.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import io.quarkus.test.QuarkusUnitTest;

public class SassErrorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsManifestResource(new StringAsset(".inverse\n"
                            + "  background-color: base.$primary-color\n"
                            + "  ERREUR:\n"
                            + ""),
                            "resources/styles.sass"))
            .assertException(t -> {
                Assertions.assertTrue(t.getCause() instanceof SassCompilationFailedException);
                Assertions.assertTrue(t.getMessage().contains("ERREUR"));
                Assertions.assertTrue(t.getMessage().contains("META-INF/resources/styles.sass 3:10"));
            });

    @Test
    public void placeholder() {
    }
}
