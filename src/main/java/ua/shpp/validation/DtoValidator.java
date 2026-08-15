package ua.shpp.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Checks validity of dto before insertion into DB tables in following cases:
 *  1) fundamental data, needed for generation of random (fail-fast if invalid);
 *      - reading of valid shops and itemTypes from CSV files;
 *      - generation of valid items;
 *  2) generated shopEntries by Producer and validated in Consumer;
 * One instance of validator is used in the whole application, including multithreaded pipelines,
 * so it is created once in try-with-resources and closed on application shutdown.
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
