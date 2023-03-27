package io.quarkiverse.web.assets.deployment;

public enum Globs {

    SCRIPTS("*.{js,ts,jsx}"),
    STYLES("*.{css,scss,sass}"),
    QUTE_TAGS("*.html"),
    ALL("*.*");

    private final String fileGlob;
    private final String glob;

    Globs(String fileGlob) {
        this.fileGlob = fileGlob;
        this.glob = "**/" + fileGlob;
    }

    public String fileGlob() {
        return fileGlob;
    }

    public String glob() {
        return glob;
    }

    public String glob(String dir) {
        return dir + glob;
    }
}
