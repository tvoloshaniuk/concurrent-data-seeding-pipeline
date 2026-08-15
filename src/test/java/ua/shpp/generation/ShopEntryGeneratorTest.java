package ua.shpp.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.validation.DtoValidator;

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

    /* The whole row-count guarantee rests on this: every itemId appears once and only once,
    which is what makes duplicate (item_id, shop_id) pairs impossible without a DB check. */
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

    // 0 is a legitimate value (listed, out of stock), so the lower bound is inclusive too.
    /* The IDE reads @Min(0) as a promise and calls the lower bound redundant, but the annotation
    only declares - nothing enforces it at construction, and this same generator emits -1 on
    purpose once the invalid rate is on. Both bounds are therefore checked for real. */
    @SuppressWarnings("DataFlowIssue")
    @Test
    void generateForShop_keepsItemCountWithinConfiguredBounds() {
        List<ShopEntryDto> entries = generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        assertTrue(entries.stream()
                .allMatch(entry -> entry.itemCount() >= 0 && entry.itemCount() <= MAX_STOCK_QUANTITY));
    }

    @Test
    void generateForShop_allowsBoundaryShopIds() {
        assertEquals(ITEM_CATALOG_SIZE, generator().generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE).size());
        assertEquals(ITEM_CATALOG_SIZE, generator().generateForShop(SHOP_COUNT, SHOP_COUNT, ITEM_CATALOG_SIZE).size());
    }

    /* shopId's upper bound cannot be a @Max on ShopEntryDto because it depends on the row
    count of shops.csv, so this guard is the only thing enforcing it. */
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

    /* At 100% every itemId gets one invalid row ALONGSIDE its valid one, never instead of it -
    that is what keeps the valid count at exactly itemCatalogSize whatever the rate is, so
    plannedRows and the row target stay untouched. */
    @Test
    void generateForShop_addsInvalidEntriesWithoutLosingAnyValidOne() {
        ShopEntryGenerator generator = new ShopEntryGenerator(MAX_STOCK_QUANTITY, 100);

        List<ShopEntryDto> entries = generator.generateForShop(1, SHOP_COUNT, ITEM_CATALOG_SIZE);

        assertEquals(ITEM_CATALOG_SIZE * 2, entries.size());
        assertEquals(ITEM_CATALOG_SIZE, entries.stream().filter(VALIDATOR::isValid).count());
    }

    // Every itemId must still appear among the valid rows, otherwise a ShopEntry row would be lost.
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
