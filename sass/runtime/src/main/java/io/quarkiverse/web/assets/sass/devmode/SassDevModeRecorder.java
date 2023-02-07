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
    // warning that hot reload might leave some values empty instead of removing them
    static volatile Map<String, List<String>> reverseDependencies = new HashMap<>();
    static volatile Path buildDir;

    public void clearHotReloadContext() {
        reverseDependencies.clear();
    }

    public void addDependency(String source, String affectedFile) {
        addHotReloadDependency(source, affectedFile);
    }

    public void setBuildDir(String buildDir) {
        SassDevModeRecorder.buildDir = Path.of(buildDir);
    }

    public static void resetDependencies(String affectedFile) {
        // remove the affected file from all targets
        reverseDependencies.replaceAll((source, affectedFiles) -> {
            affectedFiles.remove(affectedFile);
            return affectedFiles;
        });
    }

    public static void addHotReloadDependency(String source, String affectedFile) {
        List<String> affectedFiles = reverseDependencies.get(source);
        if (affectedFiles == null) {
            affectedFiles = new ArrayList<>();
            reverseDependencies.put(source, affectedFiles);
        }
        affectedFiles.add(affectedFile);
    }

    /**
     * Returns true if the given filename (not path) does not start with _
     * and ends with either .sass or .scss case-insensitive
     */
    public static boolean isCompiledSassFile(String filename) {
        if (filename.startsWith("_")) {
            return false;
        }
        String lc = filename.toLowerCase();
        return lc.endsWith(".scss") || lc.endsWith(".sass");
    }

    /**
     * Returns true if the given filename (not path) ends with .sass case-insensitive
     */
    public static boolean isSassFile(String filename) {
        String lc = filename.toLowerCase();
        return lc.endsWith(".sass");
    }
}
