package ua.shpp.hibernateValidator;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

public final class ValidatorUtil {
    /**
     * ValidatorFactory is intentionally kept open for the whole application lifetime (static singleton),
     * not closed via try-with-resources: Validator instances obtained from it are used throughout the app,
     * and it's cleaned up on JVM exit, same as other process-lifetime resources.
     */
    @SuppressWarnings("resource")
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ValidatorUtil() {
    }

    public static boolean isValid(Object value) {
        return validator.validate(value).isEmpty();
    }
}
