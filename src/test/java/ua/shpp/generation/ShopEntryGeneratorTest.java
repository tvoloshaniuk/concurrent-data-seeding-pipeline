package ua.shpp.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.validation.DtoValidator;

import java.util.IntSummaryStatistics;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopEntryGeneratorTest {
    private static final int SHOP_COUNT = 57;
    private static final int ITEM_CATALOG_SIZE = 100;
    private static final int MAX_STOCK_QUANTITY = 500;
    private static final DtoValidator VALIDATOR = new DtoValidator();

    @Test
    void generateForShop_returnsExactlyOneEntryPerCatalogItem() {
        List<ShopEntryDto> entries = generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        assertEquals(ITEM_CATALOG_SIZE, entries.size());
    }

    @Test
    void generateForShop_coversEveryItemIdExactlyOnce() {
        List<ShopEntryDto> entries = generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        List<Integer> itemIds = entries.stream().map(ShopEntryDto::itemId).sorted().toList();
        assertEquals(java.util.stream.IntStream.rangeClosed(1, ITEM_CATALOG_SIZE).boxed().toList(), itemIds);
    }

    @Test
    void generateForShop_stampsRequestedShopIdOnEveryEntry() {
        int shopId = 42;

        List<ShopEntryDto> entries = generator().generateForShop(shopId, SHOP_COUNT, ITEM_CATALOG_SIZE);

        assertTrue(entries.stream().allMatch(entry -> entry.shopId() == shopId));
    }

    @Test
    void generateForShop_keepsItemCountWithinConfiguredBounds() {
        List<ShopEntryDto> entries = generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        IntSummaryStatistics counts = entries.stream().mapToInt(ShopEntryDto::itemCount).summaryStatistics();

        assertTrue(counts.getMin() >= 0, "itemCount went below 0: " + counts.getMin());
        assertTrue(counts.getMax() <= MAX_STOCK_QUANTITY, "itemCount exceeded the bound: " + counts.getMax());
    }

    @Test
    void generateForShop_allowsBoundaryShopIds() {
        assertEquals(ITEM_CATALOG_SIZE, generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE).size());
        assertEquals(ITEM_CATALOG_SIZE, generator().generateForShop(SHOP_COUNT, SHOP_COUNT, ITEM_CATALOG_SIZE).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, SHOP_COUNT + 1})
    void generateForShop_rejectsShopIdOutsideRange(int shopId) {
        ShopEntryGenerator generator = generator();

        assertThrows(IllegalArgumentException.class,
                () -> generator.generateForShop(shopId, SHOP_COUNT, ITEM_CATALOG_SIZE));
    }

    @Test
    void generateForShop_producesOnlyValidEntriesWhenInvalidGenerationIsOff() {
        List<ShopEntryDto> entries = generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        assertTrue(entries.stream().allMatch(VALIDATOR::isValid));
    }

    @Test
    void generateForShop_addsInvalidEntriesWithoutLosingAnyValidOne() {
        ShopEntryGenerator generator = new ShopEntryGenerator(MAX_STOCK_QUANTITY, 100);

        List<ShopEntryDto> entries = generator.generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        assertEquals(ITEM_CATALOG_SIZE * 2, entries.size());
        assertEquals(ITEM_CATALOG_SIZE, entries.stream().filter(VALIDATOR::isValid).count());
    }

    @Test
    void generateForShop_stillCoversEveryItemIdWhenInvalidGenerationIsOn() {
        ShopEntryGenerator generator = new ShopEntryGenerator(MAX_STOCK_QUANTITY, 100);

        List<Integer> validItemIds = generator.generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE).stream()
                .filter(VALIDATOR::isValid)
                .map(ShopEntryDto::itemId)
                .sorted()
                .toList();

        assertEquals(java.util.stream.IntStream.rangeClosed(1, ITEM_CATALOG_SIZE).boxed().toList(), validItemIds);
    }

    private static ShopEntryGenerator generator() {
        return new ShopEntryGenerator(MAX_STOCK_QUANTITY, 0);
    }
}
