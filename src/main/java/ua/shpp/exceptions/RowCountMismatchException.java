package ua.shpp.exceptions;

/**
 * Thrown when the pipeline finished without failing yet still inserted fewer ShopEntry rows
 * than the configured target. Separate from PipelineTaskException because no task died -
 * rows were merely lost along the way, which only the final count can reveal.
 */
public class RowCountMismatchException extends IllegalStateException {
    public RowCountMismatchException(String s) {
        super(s);
    }
}
