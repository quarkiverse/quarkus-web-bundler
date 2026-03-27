package io.quarkiverse.tools.projectscanner;

/**
 * Strategy for handling duplicate paths (same relative path from different sources).
 * <p>
 * When the same file path exists from multiple sources (e.g. a local project file
 * and a dependency JAR), this strategy determines which version is kept.
 * Source priority is: {@code LOCAL_PROJECT_FILE > APPLICATION_RESOURCE > DEPENDENCY_RESOURCE}.
 */
public enum DuplicateStrategy {

    /**
     * Fail with an exception if duplicate paths are found.
     */
    FAIL,

    /**
     * Keep the application version (highest priority wins).
     * Priority: {@code LOCAL_PROJECT_FILE > APPLICATION_RESOURCE > DEPENDENCY_RESOURCE}.
     */
    PREFER_APP,

    /**
     * Keep the dependency version (lowest priority wins).
     * Priority: {@code DEPENDENCY_RESOURCE > APPLICATION_RESOURCE > LOCAL_PROJECT_FILE}.
     */
    PREFER_DEPENDENCY
}