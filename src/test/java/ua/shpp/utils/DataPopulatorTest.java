package ua.shpp.utils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.validation.DtoValidator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataPopulatorTest {
    private static final String THREE_SHOPS = "address\nКиїв 1\nЛьвів 2\nОдеса 3";
    private static final String TWO_TYPES = "name\nПлитка\nПосуд";

    private final DbRepository dbRepository = mock(DbRepository.class);
    // Real, not mocked: validation rules are what these tests rely on to filter the rows.
    private final DtoValidator validator = new DtoValidator();

    @Test
    void fillFoundationTables_reportsShopCountAndDerivedCatalogSize() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);

        CatalogDimensions dimensions =
                populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(3, dimensions.shopCount());
        // ceilDiv(30, 3) - rounded up so the pipeline never plans fewer rows than the target
        assertEquals(10, dimensions.itemCatalogSize());
    }

    @Test
    void fillFoundationTables_roundsCatalogSizeUpWhenTargetIsNotDivisible() {
        DataPopulator populator = new DataPopulator(dbRepository, config(31, 2), validator);

        CatalogDimensions dimensions =
                populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(11, dimensions.itemCatalogSize());
    }

    @Test
    void fillFoundationTables_insertsEveryShopFromCsv() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);

        populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShopDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertShops(captor.capture());
        assertEquals(List.of(new ShopDto("Київ 1"), new ShopDto("Львів 2"), new ShopDto("Одеса 3")),
                captor.getValue());
    }

    /* Every base category is multiplied by typeIncreaseCoefficient with a numeric suffix -
    the reason a plain "Плитка" never matches anything in the final search. */
    @Test
    void fillFoundationTables_expandsEachBaseCategoryWithNumericSuffix() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 3), validator);

        populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemTypeDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertItemTypes(captor.capture());
        assertEquals(List.of(
                new ItemTypeDto("Плитка 1"), new ItemTypeDto("Плитка 2"), new ItemTypeDto("Плитка 3"),
                new ItemTypeDto("Посуд 1"), new ItemTypeDto("Посуд 2"), new ItemTypeDto("Посуд 3")
        ), captor.getValue());
    }

    /* The first itemTypeCount items take types in order so no type is left without an item,
    which is what makes the final search guaranteed to find something for any valid type. */
    @Test
    void fillFoundationTables_givesEveryTypeAtLeastOneItem() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 3), validator);

        populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertItems(captor.capture());
        List<ItemDto> items = captor.getValue();
        assertEquals(10, items.size());
        for (int typeId = 1; typeId <= 6; typeId++) {
            int expected = typeId;
            assertTrue(items.stream().anyMatch(item -> item.typeId() == expected),
                    "no item generated for typeId " + expected);
        }
    }

    @Test
    void fillFoundationTables_throwsWhenShopsCsvHasNoRows() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);
        InputStream emptyShops = csv("address\n");
        InputStream types = csv(TWO_TYPES);

        assertThrows(IllegalStateException.class, () -> populator.fillFoundationTables(emptyShops, types));
    }

    @Test
    void fillFoundationTables_throwsWhenItemTypesCsvHasNoRows() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);
        InputStream shops = csv(THREE_SHOPS);
        InputStream emptyTypes = csv("name\n");

        assertThrows(IllegalStateException.class, () -> populator.fillFoundationTables(shops, emptyTypes));
    }

    /* A single shop is a legitimate setup, not a broken file: itemCatalogSize is derived from
    shopCount, so it simply grows to 30 and the row target still holds. */
    @Test
    void fillFoundationTables_acceptsASingleShop() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);

        CatalogDimensions dimensions =
                populator.fillFoundationTables(csv("address\nКиїв 1"), csv(TWO_TYPES));

        assertEquals(1, dimensions.shopCount());
        assertEquals(30, dimensions.itemCatalogSize());
    }

    /* The whole reason generation retries instead of filtering: invalid generation must not shrink the
    catalogue. Fewer items than itemCatalogSize would leave ShopEntryGenerator enumerating item
    ids the database never assigned, and every such row would break the foreign key. */
    @Test
    void fillFoundationTables_stillDeliversTheFullCatalogueWhenInvalidGenerationIsOn() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2, 50), validator);

        CatalogDimensions dimensions =
                populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertItems(captor.capture());
        assertEquals(dimensions.itemCatalogSize(), captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(validator::isValid));
    }

    // A 100% invalid rate can never fill the catalogue, so it fails with a bounded number of tries.
    @Test
    void fillFoundationTables_throwsWhenEveryCandidateIsInvalid() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2, 100), validator);
        InputStream shops = csv(THREE_SHOPS);
        InputStream types = csv(TWO_TYPES);

        assertThrows(IllegalStateException.class, () -> populator.fillFoundationTables(shops, types));
    }

    /* Too many types for too small a catalog would leave types with no items at all, so this
    fails loudly instead of producing a data set where some searches silently find nothing. */
    @Test
    void fillFoundationTables_throwsWhenCatalogTooSmallToCoverEveryType() {
        DataPopulator populator = new DataPopulator(dbRepository, config(6, 5), validator);
        InputStream shops = csv(THREE_SHOPS);
        InputStream types = csv(TWO_TYPES);

        assertThrows(IllegalStateException.class, () -> populator.fillFoundationTables(shops, types));
    }

    private static AppConfig config(int shopEntryTarget, int typeIncreaseCoefficient) {
        return config(shopEntryTarget, typeIncreaseCoefficient, 0);
    }

    private static AppConfig config(int shopEntryTarget, int typeIncreaseCoefficient, int invalidRatePercent) {
        // Credentials only have to be non-blank - nothing here ever opens a connection.
        return new AppConfig("jdbc:unused-by-unit-test", "unused", "unused",
                5000, 2, 4, 500,
                shopEntryTarget, typeIncreaseCoefficient, 500, invalidRatePercent, true, "Плитка 1");
    }

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
