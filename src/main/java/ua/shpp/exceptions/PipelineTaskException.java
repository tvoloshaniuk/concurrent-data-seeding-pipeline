package ua.shpp.exceptions;

/**
 * Runtime wrapper for ExecutionException (in Callable threads of executor services)
 */
public class PipelineTaskException extends RuntimeException {
    public PipelineTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
