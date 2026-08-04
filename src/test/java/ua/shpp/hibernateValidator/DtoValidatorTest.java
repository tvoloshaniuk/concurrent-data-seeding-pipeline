package ua.shpp.hibernateValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.dto.ShopEntryDto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidatorTest {
    private final DtoValidator validator = new DtoValidator();


    @Test
    void isValid_acceptsWellFormedShopEntry() {
        assertTrue(validator.isValid(new ShopEntryDto(1, 1, 0)));
    }

    /* itemCount 0 is valid on purpose (listed but out of stock), while ids must be positive -
    that asymmetry is the whole point of @Min(0) vs @Positive here. */
    @ParameterizedTest
    @CsvSource({
            "0, 1, 5",
            "-1, 1, 5",
            "1, 0, 5",
            "1, -1, 5",
            "1, 1, -1"
    })
    void isValid_rejectsShopEntryOutsideAllowedRanges(int itemId, int shopId, int itemCount) {
        assertFalse(validator.isValid(new ShopEntryDto(itemId, shopId, itemCount)));
    }

    @Test
    void isValid_acceptsWellFormedShop() {
        assertTrue(validator.isValid(new ShopDto("Київ, вул. Берковецька 6К")));
    }

    @Test
    void isValid_rejectsBlankShopAddress() {
        assertFalse(validator.isValid(new ShopDto("   ")));
    }

    @Test
    void isValid_rejectsShopAddressLongerThanColumn() {
        assertFalse(validator.isValid(new ShopDto("x".repeat(256))));
    }

    @Test
    void isValid_acceptsWellFormedItemType() {
        assertTrue(validator.isValid(new ItemTypeDto("Сантехніка 1")));
    }

    @Test
    void isValid_rejectsBlankItemTypeName() {
        assertFalse(validator.isValid(new ItemTypeDto("")));
    }

    @Test
    void isValid_acceptsWellFormedItem() {
        assertTrue(validator.isValid(new ItemDto(1, "0f8c1e2a-3b4d-5e6f-7a8b-9c0d1e2f3a4b", 1)));
    }

    @Test
    void isValid_rejectsItemNameShorterThanMinimum() {
        assertFalse(validator.isValid(new ItemDto(1, "ab", 1)));
    }
}
