package ua.shpp.hibernateValidator;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Wraps one ValidatorFactory for the length of a single run. Building a factory is expensive and
 * the Validator it hands out is thread-safe, so every producer and consumer thread shares this
 * instance instead of building its own per batch.
 * <p>
 * Deliberately an instance rather than a static holder: a static factory lives as long as the
 * class loader, which outlasts any single run, and whoever closed it would be closing it for
 * every other application sharing that JVM. As an ordinary AutoCloseable it is created and
 * closed by the code that owns the run, so nothing outside that scope is affected.
 */
public final class DtoValidator implements AutoCloseable {
    private final ValidatorFactory validatorFactory;
    private final Validator validator;

    public DtoValidator() {
        this.validatorFactory = Validation.buildDefaultValidatorFactory();
        this.validator = validatorFactory.getValidator();
    }

    public boolean isValid(Object value) {
        return validator.validate(value).isEmpty();
    }

    @Override
    public void close() {
        validatorFactory.close();
    }
}
