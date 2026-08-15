package ua.shpp.exceptions;

/**
 * A classpath resource the application cannot start without is missing or unreadable - schema.sql,
 * config.properties, one of the CSVs. Named rather than a bare RuntimeException so a caller can
 * tell "the jar was packaged wrong" apart from every other runtime failure.
 */
public class ResourceLoadException extends RuntimeException {
    public ResourceLoadException(String message) {
        super(message);
    }

    public ResourceLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
