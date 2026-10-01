package ua.shpp.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.dto.ShopEntryDto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidatorTest {
    private final DtoValidator validator = new DtoValidator();

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    /* ShopEntry */
    @ParameterizedTest
    @CsvSource({
            "0, 1, 0",
            "-1, 1, 0",
            "1, 0, 0",
            "1, -1, 0",
            "1, 1, -1"
    })
    void isValid_rejectsShopEntryOutsideAllowedRanges(int itemId, int shopId, int itemCount) {
        assertFalse(validator.isValid(new ShopEntryDto(itemId, shopId, itemCount)));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1, 0",
            "5, 5, 5"
    })
    void isValid_acceptsShopEntryInsideAllowedRanges(int itemId, int shopId, int itemCount) {
        assertTrue(validator.isValid(new ShopEntryDto(itemId, shopId, itemCount)));
    }

    /* DTO names */
    @ParameterizedTest
    @NullSource // @NotBlank
    @ValueSource(strings = {
            "", // @NotBlank
            " ", // @NotBlank
            "A1", // @Size(min = 3)
            "abc1", // @Pattern(regexp = "^\\p{Lu}.*")
            "Abc" // @Pattern(regexp = ".*[0-9]+.*")
    })
    void isValid_rejectsDtosWithInvalidName(String name) {
        assertFalse(validator.isValid(new ShopDto(name)));
        assertFalse(validator.isValid(new ItemTypeDto(name)));
        assertFalse(validator.isValid(new ItemDto(name, 1)));
    }

    @Test
    void isValid_acceptsDtosWithValidNames() {
        assertTrue(validator.isValid(new ShopDto("Київ 1")));
        assertTrue(validator.isValid(new ItemTypeDto("Побутова техніка 1")));
        assertTrue(validator.isValid(new ItemDto("Item 1", 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void isValid_rejectsItemWithNonPositiveTypeId(int typeId) {
        assertFalse(validator.isValid(new ItemDto("Item 1", typeId)));
    }
}
