package io.quarkiverse.web.bundler.sass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.InboundMessage.ImportResponse.ImportSuccess;
import com.sass_lang.embedded_protocol.OutputStyle;
import com.sass_lang.embedded_protocol.Syntax;

import de.larsgrefer.sass.embedded.CompileSuccess;
import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import de.larsgrefer.sass.embedded.SassCompiler;
import de.larsgrefer.sass.embedded.SassCompilerFactory;
import de.larsgrefer.sass.embedded.importer.CustomImporter;

public class SassBuildTimeCompiler implements BiFunction<String[], BiConsumer<String, String>, String> {

    @Override
    public String apply(String[] args, BiConsumer<String, String> dependencyCollector) {
        Path sassFile = Path.of(args[0]);
        Path rootPath = Path.of(args[2]);
        return convertScss(sassFile, rootPath, dependencyCollector);
    }

    /**
     * Returns true if the given filename (not path) ends with .sass case-insensitive
     */
    public static boolean isSassFile(String filename) {
        String lc = filename.toLowerCase();
        return lc.endsWith(".sass");
    }

    public static String convertScss(Path sassFile,
            Path rootPath,
            BiConsumer<String, String> dependencyCollector) {
        final String relativePath = sassFile.relativize(rootPath).toString();
        // scss files depend on themselves
        dependencyCollector.accept(relativePath, relativePath);
        boolean isSass = isSassFile(sassFile.getFileName().toString());

        try (SassCompiler sassCompiler = SassCompilerFactory.bundled()) {
            Path parent = sassFile.getParent();
            sassCompiler.registerImporter(new CustomImporter() {

                @Override
                public String canonicalize(String url, boolean fromImport) throws Exception {
                    // add extension if missing
                    String extension = isSass ? ".sass" : ".scss";
                    if (!url.toLowerCase().endsWith(extension)) {
                        url += extension;
                    }
                    Path resolved = parent.resolve(url);
                    // prefix with _ for partials
                    if (!resolved.getFileName().toString().startsWith("_")) {
                        resolved = resolved.getParent().resolve("_" + resolved.getFileName());
                    }
                    return "sass:" + resolved;
                }

                @Override
                public ImportSuccess handleImport(String url) throws Exception {
                    if (url.startsWith("sass:")) {
                        Path path = Path.of(url.substring(5));
                        Path relativeImport = rootPath.relativize(path);
                        dependencyCollector.accept(relativeImport.toString(), relativePath);
                        String contents = Files.readString(path, StandardCharsets.UTF_8);
                        return ImportSuccess.newBuilder().setContents(contents)
                                .setSyntax(isSass ? Syntax.INDENTED : Syntax.SCSS).buildPartial();
                    }
                    return null;
                }
            });
            String contents = Files.readString(sassFile, StandardCharsets.UTF_8);
            InboundMessage.CompileRequest.StringInput stringInput = InboundMessage.CompileRequest.StringInput.newBuilder()
                    .setSource(contents)
                    .setUrl(sassFile.toString())
                    .setSyntax(isSass ? Syntax.INDENTED : Syntax.SCSS)
                    .build();
            CompileSuccess compileSuccess = sassCompiler.compileString(stringInput, OutputStyle.EXPANDED);

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
