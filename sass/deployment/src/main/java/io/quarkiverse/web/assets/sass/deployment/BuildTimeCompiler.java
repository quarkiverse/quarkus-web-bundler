package io.quarkiverse.web.assets.sass.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import de.larsgrefer.sass.embedded.SassCompiler;
import de.larsgrefer.sass.embedded.SassCompilerFactory;
import de.larsgrefer.sass.embedded.importer.CustomImporter;
import sass.embedded_protocol.EmbeddedSass.InboundMessage.ImportResponse.ImportSuccess;
import sass.embedded_protocol.EmbeddedSass.OutboundMessage.CompileResponse.CompileSuccess;

public class BuildTimeCompiler implements BiFunction<String[], BiConsumer<String, String>, String> {

    @Override
    public String apply(String[] args, BiConsumer<String, String> dependencyCollector) {
        Path absolutePath = Path.of(args[0]);
        String relativePath = args[1];
        Path resourcesAbsoluteRootPath = Path.of(args[2]);
        System.err.println("COMPILING FROM DEPLOYMENT MODULE: " + relativePath);
        String result = convertScss(absolutePath, relativePath, resourcesAbsoluteRootPath, dependencyCollector);
        System.err.println("Result: " + result);
        return result;
    }

    public static String convertScss(Path absolutePath, String relativePath,
            Path resourcesAbsoluteRootPath,
            BiConsumer<String, String> dependencyCollector) {

        try (SassCompiler sassCompiler = SassCompilerFactory.bundled()) {
            Path parent = absolutePath.getParent();
            sassCompiler.registerImporter(new CustomImporter() {

                @Override
                public String canonicalize(String url, boolean fromImport) throws Exception {
                    System.err.println("canonicalize " + url + " fromImport: " + fromImport);
                    // add extension if missing
                    if (!url.toLowerCase().endsWith(".scss")) {
                        url += ".scss";
                    }
                    Path resolved = parent.resolve(url);
                    // prefix with _ for partials
                    if (!resolved.getFileName().toString().startsWith("_")) {
                        resolved = resolved.getParent().resolve("_" + resolved.getFileName().toString());
                    }
                    return "sass:" + resolved;
                }

                @Override
                public ImportSuccess handleImport(String url) throws Exception {
                    System.err.println("handleImport: " + url);
                    if (url.startsWith("sass:")) {
                        Path path = Path.of(url.substring(5));
                        Path relativeImport = resourcesAbsoluteRootPath.relativize(path);
                        dependencyCollector.accept(relativeImport.toString(), relativePath);
                        String contents = Files.readString(path, StandardCharsets.UTF_8);
                        return ImportSuccess.newBuilder().setContents(contents).buildPartial();
                    }
                    return null;
                }
            });
            String contents = Files.readString(absolutePath, StandardCharsets.UTF_8);
            CompileSuccess compileSuccess = sassCompiler.compileScssString(contents);

            //get compiled css
            return compileSuccess.getCss();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SassCompilationFailedException e) {
            // FIXME: provide better error reporting mechanism to display on front page in dev mode
            throw new RuntimeException(e);
        }
    }

}
