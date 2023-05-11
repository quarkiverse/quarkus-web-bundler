package io.quarkiverse.web.bundler.deployment;

public enum Globs {

    SCRIPTS("*.{js,jsx,ts,tsx,css,scss,sass,json,svg,gif,png,jpg,woff,woff2,eot,ttf}"),
    STYLES("*.{css,scss,sass}"),
    QUTE_TAGS("*.html"),
    ALL("*.");

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
