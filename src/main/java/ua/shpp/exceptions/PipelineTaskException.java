package ua.shpp.exceptions;

public class PipelineTaskException extends RuntimeException {
    public PipelineTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
