package ua.shpp.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.shpp.dto.ShopDto;
import ua.shpp.dto.ShopEntryDto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidatorTest {
    private final DtoValidator validator = new DtoValidator();

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

    /* Shop */
    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "  ", "\t", "\n", //notBlank
            "12", "a1", "!1", "?1", "@1", "#1", // Size < 3
            "aaa1", "bbb1", "ccc1", "ddd1", // Pattern ^\p{Ll}
            "Abc", "Def", "Ghi", "Jkl" // Pattern .*0-

    })
    void isValid_rejectsShopWithInvalidAddress(String missingKey) {
        assertFalse(validator.isValid(new ShopDto(missingKey)));
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "Abc-1", "Def0", "Ghi1", "Jkl2" // valid
    })
    void isValid_acceptsShopWithValidAddress(String validKey) {
        assertTrue(validator.isValid(new ShopDto(validKey)));
    }


    /* ItemType */


    /* Item */





}