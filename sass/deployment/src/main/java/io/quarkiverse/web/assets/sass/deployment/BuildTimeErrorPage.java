package io.quarkiverse.web.assets.sass.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.jboss.logging.Logger;

import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Escaper;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.ValueResolver;
import io.quarkus.runtime.TemplateHtmlBuilder;
import sass.embedded_protocol.EmbeddedSass.OutboundMessage.CompileResponse.CompileFailure;
import sass.embedded_protocol.EmbeddedSass.SourceSpan;

public class BuildTimeErrorPage implements Function<Throwable, String> {
    private static final Logger LOG = Logger.getLogger(BuildTimeErrorPage.class);

    private static final String PROBLEM_TEMPLATE = ""
            + "<h3>#{problemIndex} {title}</h3>\n"
            + "<div style=\"margin-bottom:0.5em;\">{description}</div>\n"
            + "<div style=\"font-family:monospace;font-size:1em;background-color:#2E3436;color:white;padding:1em;margin-bottom:2em;\">\n"
            + "{#if realLines.get(0) > 1}<span style=\"color:silver;\">...</span><br>{/if}\n"
            + "{#for line in sourceLines}\n"
            // highlight the error line - start
            + "{#if lineNumber is realLines.get(line_index)}<div style=\"background-color:#555753;\">{/if}\n"
            // line number
            + "<span style=\"color:silver;\">{realLines.get(line_index).pad}</span>\n"
            // line content
            + " {line}\n"
            // highlight the error line - end
            + "{#if lineNumber is realLines.get(line_index)}</div>{#else}<br>{/if}\n"
            // point to error
            + "{#if lineNumber is realLines.get(line_index)}{space.pad}<span style=\"color:red;\">{#for i in lineCharacterStart}={/for}^</span><br>{/if}\n"
            + "{/for}\n"
            + "{#if endLinesSkipped}<span style=\"color:silver;\">...</span>{/if}\n"
            + "</div>";

    @Override
    public String apply(Throwable exception) {
        Escaper escaper = Escaper.builder().add('"', "&quot;").add('\'', "&#39;")
                .add('&', "&amp;").add('<', "&lt;").add('>', "&gt;").build();
        Template problemTemplate = Engine.builder().addDefaults().addValueResolver(new ReflectionValueResolver())
                .addValueResolver(new ValueResolver() {

                    public boolean appliesTo(EvalContext context) {
                        return context.getName().equals("pad");
                    }

                    @Override
                    public CompletionStage<Object> resolve(EvalContext context) {
                        return CompletableFuture.completedFuture(htmlPadRight(context.getBase().toString(), 5));
                    }
                })
                .build()
                .parse(PROBLEM_TEMPLATE);
        TemplateHtmlBuilder builder;
        List<SassCompilationFailedException> problems;
        Throwable[] suppressed = exception.getSuppressed();
        if (suppressed.length == 0) {
            problems = Collections.singletonList((SassCompilationFailedException) exception);
        } else {
            problems = (List) Arrays.asList(suppressed);
        }

        String problemsFound = "Found " + problems.size() + " SASS problems";
        builder = new TemplateHtmlBuilder("Error restarting Quarkus", problemsFound, problemsFound);

        // Attempt to sort problems by file, line, column
        problems.sort(new Comparator<SassCompilationFailedException>() {
            @Override
            public int compare(SassCompilationFailedException t1, SassCompilationFailedException t2) {
                SourceSpan span1 = t1.getCompileFailure().getSpan();
                SourceSpan span2 = t2.getCompileFailure().getSpan();
                int ret = span1.getUrl().compareTo(span2.getUrl());
                if (ret != 0)
                    return ret;
                // same file
                return Integer.compare(span1.getStart().getOffset(), span2.getStart().getOffset());
            }
        });

        for (ListIterator<SassCompilationFailedException> it = problems.listIterator(); it.hasNext();) {
            SassCompilationFailedException problem = it.next();
            builder.append(getProblemInfo(it.previousIndex() + 1, problem, problemTemplate, escaper));
        }
        return builder.toString();
    }

    String getProblemInfo(int index, SassCompilationFailedException problem, Template problemTemplate, Escaper escaper) {
        CompileFailure compileFailure = problem.getCompileFailure();

        String templateId = compileFailure.getSpan().getUrl();
        int lineNumber = compileFailure.getSpan().getStart().getLine() + 1;
        int lineCharacterStart = compileFailure.getSpan().getStart().getColumn() + 1;

        String title = templateId + ":" + lineNumber + ":" + lineCharacterStart;
        String description = compileFailure.getMessage();

        List<String> sourceLines = new ArrayList<>();
        try (BufferedReader in = getBufferedReader(templateId)) {
            String line = null;
            while ((line = in.readLine()) != null) {
                sourceLines.add(escaper.escape(line).replace(" ", "&nbsp;"));
            }
        } catch (Exception e) {
            LOG.warn("Unable to read the template source: " + templateId, e);
        }

        List<Integer> realLines = new ArrayList<>();
        boolean endLinesSkipped = false;
        if (sourceLines.size() > 15) {
            // Line with error plus few surrounding lines
            int fromIndex = lineNumber > 7 ? (lineNumber - 8) : 0;
            int toIndex = (lineNumber + 7) > sourceLines.size() ? sourceLines.size() : lineNumber + 7;
            for (int j = fromIndex; j < toIndex; j++) {
                // e.g. [10,11,12]
                realLines.add(j + 1);
            }
            sourceLines = sourceLines.subList(fromIndex, toIndex);
            endLinesSkipped = toIndex != sourceLines.size();
        } else {
            for (int j = 0; j < sourceLines.size(); j++) {
                // [1,2,3]
                realLines.add(j + 1);
            }
        }

        return problemTemplate
                .data("problemIndex", index)
                .data("title", title)
                .data("description", description)
                .data("sourceLines", sourceLines)
                .data("lineNumber", lineNumber)
                .data("lineCharacterStart", lineCharacterStart)
                .data("realLines", realLines)
                .data("endLinesSkipped", endLinesSkipped)
                .data("space", " ")
                .render();
    }

    static String htmlPadRight(String s, int n) {
        return String.format("%-" + n + "s", s).replace(" ", "&nbsp;");
    }

    private BufferedReader getBufferedReader(String filePath) throws IOException {
        System.err.println("source url: " + filePath);
        if (filePath != null) {
            // import urls will have sass: scheme before absolute path
            if (filePath.startsWith("sass:")) {
                filePath = filePath.substring(5);
            }
            return Files.newBufferedReader(Path.of(filePath));
        }
        throw new IllegalStateException("Template source not available");
    }
}
