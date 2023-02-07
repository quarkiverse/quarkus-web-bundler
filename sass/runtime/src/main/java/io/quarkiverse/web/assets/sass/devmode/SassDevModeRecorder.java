package io.quarkiverse.web.assets.sass.devmode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SassDevModeRecorder {

    // source file -> depends on source files
    static volatile Map<String, List<String>> reverseDependencies = new HashMap<>();
    static volatile Path buildDir;

    public void clearHotReloadContext() {
        reverseDependencies.clear();
    }

    public void addDependency(String source, String affectedFile) {
        System.err.println("source file " + source + " will affect " + affectedFile);
        List<String> sources = reverseDependencies.get(source);
        if (sources == null) {
            sources = new ArrayList<>();
            reverseDependencies.put(source, sources);
        }
        sources.add(affectedFile);
        System.err.println("deps: " + reverseDependencies);
    }

    public void setBuildDir(String buildDir) {
        SassDevModeRecorder.buildDir = Path.of(buildDir);
    }
}
