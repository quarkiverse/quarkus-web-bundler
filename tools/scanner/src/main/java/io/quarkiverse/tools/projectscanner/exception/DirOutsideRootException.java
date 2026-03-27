package io.quarkiverse.tools.projectscanner.exception;

public class DirOutsideRootException extends RuntimeException {

    public DirOutsideRootException(String message) {
        super(message);
    }

}