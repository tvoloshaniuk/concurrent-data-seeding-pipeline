package ua.shpp.exceptions;

public class RowCountMismatchException extends IllegalStateException {
    public RowCountMismatchException(String s) {
        super(s);
    }
}
