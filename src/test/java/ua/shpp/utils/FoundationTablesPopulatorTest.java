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

// ByteArrayInputStream holds no OS handle and its close() is a no-op.
@SuppressWarnings("resource")
class FoundationTablesPopulatorTest {
    private static final String THREE_SHOPS = "address\nКиїв 1\nЛьвів 2\nОдеса 3";
    private static final String TWO_TYPES = "name\nПлитка\nПосуд";

    private final DbRepository dbRepository = mock(DbRepository.class);
    // Real, not mocked: validation rules are what these tests rely on to filter the rows.
    private final DtoValidator validator = new DtoValidator();

    @Test
    void populate_reportsShopCountAndDerivedCatalogSize() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2), validator);

        CatalogDimensions dimensions =
                populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(3, dimensions.shopCount());
        // ceilDiv(30, 3) - rounded up so the pipeline never plans fewer rows than the target
        assertEquals(10, dimensions.itemCatalogSize());
    }

    @Test
    void populate_roundsCatalogSizeUpWhenTargetIsNotDivisible() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(31, 2), validator);

        CatalogDimensions dimensions =
                populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(11, dimensions.itemCatalogSize());
    }

    @Test
    void populate_insertsEveryShopFromCsv() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2), validator);

        populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShopDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertShops(captor.capture());
        assertEquals(List.of(new ShopDto("Київ 1"), new ShopDto("Львів 2"), new ShopDto("Одеса 3")),
                captor.getValue());
    }

    @Test
    void populate_expandsEachBaseCategoryWithNumericSuffix() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 3), validator);

        populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemTypeDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertItemTypes(captor.capture());
        assertEquals(List.of(
                new ItemTypeDto("Плитка 1"), new ItemTypeDto("Плитка 2"), new ItemTypeDto("Плитка 3"),
                new ItemTypeDto("Посуд 1"), new ItemTypeDto("Посуд 2"), new ItemTypeDto("Посуд 3")
        ), captor.getValue());
    }

    @Test
    void populate_givesEveryTypeAtLeastOneItem() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 3), validator);

        populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

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
    void populate_throwsWhenShopsCsvHasNoRows() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2), validator);
        InputStream emptyShops = csv("address\n");
        InputStream types = csv(TWO_TYPES);

        assertThrows(IllegalStateException.class, () -> populator.populate(emptyShops, types));
    }

    @Test
    void populate_throwsWhenItemTypesCsvHasNoRows() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2), validator);
        InputStream shops = csv(THREE_SHOPS);
        InputStream emptyTypes = csv("name\n");

        assertThrows(IllegalStateException.class, () -> populator.populate(shops, emptyTypes));
    }

    @Test
    void populate_acceptsASingleShop() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2), validator);

        CatalogDimensions dimensions =
                populator.populate(csv("address\nКиїв 1"), csv(TWO_TYPES));

        assertEquals(1, dimensions.shopCount());
        assertEquals(30, dimensions.itemCatalogSize());
    }

    @Test
    void populate_stillDeliversTheFullCatalogueWhenInvalidGenerationIsOn() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2, 50), validator);

        CatalogDimensions dimensions =
                populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertItems(captor.capture());
        assertEquals(dimensions.itemCatalogSize(), captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(validator::isValid));
    }

    @Test
    void appConfig_rejectsAnInvalidRateOf100() {
        assertThrows(IllegalArgumentException.class, () -> config(30, 2, 100));
    }

    // 99 is the highest rate the loop must still cope with, and it must return a full catalogue.
    @Test
    void populate_stillFillsTheCatalogueAtTheHighestAllowedInvalidRate() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(30, 2, 99), validator);

        CatalogDimensions dimensions = populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(10, dimensions.itemCatalogSize());
    }

    @Test
    void populate_stillFillsTheCatalogueWhenThereAreMoreTypesThanItems() {
        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config(6, 5), validator);

        CatalogDimensions dimensions = populator.populate(csv(THREE_SHOPS), csv(TWO_TYPES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertItems(captor.capture());
        assertEquals(2, dimensions.itemCatalogSize());
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(item -> item.typeId() >= 1 && item.typeId() <= 10));
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
