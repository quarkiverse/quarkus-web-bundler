package io.quarkiverse.tools.projectscanner;

/**
 * Thrown when {@link DuplicateStrategy#FAIL} is used and duplicate paths are found
 * from different sources during scanning.
 */
public class DuplicatePathException extends RuntimeException {

    private final String path;
    private final ProjectFile.Origin existingOrigin;
    private final ProjectFile.Origin duplicateOrigin;

    public DuplicatePathException(String path, ProjectFile.Origin existingOrigin, ProjectFile.Origin duplicateOrigin) {
        super("Duplicate path found: '%s' (origins: %s and %s)".formatted(path, existingOrigin, duplicateOrigin));
        this.path = path;
        this.existingOrigin = existingOrigin;
        this.duplicateOrigin = duplicateOrigin;
    }

    public String path() {
        return path;
    }

    public ProjectFile.Origin existingOrigin() {
        return existingOrigin;
    }

    public ProjectFile.Origin duplicateOrigin() {
        return duplicateOrigin;
    }
}
