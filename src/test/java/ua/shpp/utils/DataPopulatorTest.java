package ua.shpp.utils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.hibernateValidator.DtoValidator;

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

        DataPopulator.PopulationSummary summary =
                populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(3, summary.shopCount());
        // ceilDiv(30, 3) - rounded up so the pipeline never plans fewer rows than the target
        assertEquals(10, summary.itemCatalogSize());
    }

    @Test
    void fillFoundationTables_roundsCatalogSizeUpWhenTargetIsNotDivisible() {
        DataPopulator populator = new DataPopulator(dbRepository, config(31, 2), validator);

        DataPopulator.PopulationSummary summary =
                populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES));

        assertEquals(11, summary.itemCatalogSize());
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
    void fillFoundationTables_throwsWhenShopsCsvHasTooFewRows() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);

        assertThrows(IllegalStateException.class,
                () -> populator.fillFoundationTables(csv("address\nКиїв 1"), csv(TWO_TYPES)));
    }

    @Test
    void fillFoundationTables_throwsWhenItemTypesCsvHasTooFewRows() {
        DataPopulator populator = new DataPopulator(dbRepository, config(30, 2), validator);

        assertThrows(IllegalStateException.class,
                () -> populator.fillFoundationTables(csv(THREE_SHOPS), csv("name\nПлитка")));
    }

    /* Too many types for too small a catalog would leave types with no items at all, so this
    fails loudly instead of producing a data set where some searches silently find nothing. */
    @Test
    void fillFoundationTables_throwsWhenCatalogTooSmallToCoverEveryType() {
        DataPopulator populator = new DataPopulator(dbRepository, config(6, 5), validator);

        assertThrows(IllegalStateException.class,
                () -> populator.fillFoundationTables(csv(THREE_SHOPS), csv(TWO_TYPES)));
    }

    private static AppConfig config(int shopEntryTarget, int typeIncreaseCoefficient) {
        // Credentials only have to be non-blank - nothing here ever opens a connection.
        return new AppConfig("jdbc:unused-by-unit-test", "unused", "unused",
                5000, 2, 4, 500, "Плитка 1",
                shopEntryTarget, typeIncreaseCoefficient, 500);
    }

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
