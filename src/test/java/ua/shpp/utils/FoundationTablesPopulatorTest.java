package ua.shpp.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.validation.DtoValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class FoundationTablesPopulatorTest {

    private static final String SHOPS_CSV = """
            address
            "Дніпро, Запорізьке шосе, 62-К"
            "Київ, вул. Кришталева, 6"
            "Одеса, пр-т Небесної Сотні, 99"
            """;
    private static final String ITEM_TYPES_CSV = """
            name
            Будівельні матеріали
            Електротехніка
            Сантехніка
            """;

    /* populate Shop */

    @Test
    void populate_passesExpectedShopsToRepository() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.shopEntryTarget()).thenReturn(9);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ShopDto>> insertShopsCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertShops(insertShopsCaptor.capture());
        assertEquals(
                List.of(new ShopDto("Дніпро, Запорізьке шосе, 62-К"),
                        new ShopDto("Київ, вул. Кришталева, 6"),
                        new ShopDto("Одеса, пр-т Небесної Сотні, 99")
                )
                , insertShopsCaptor.getValue()
        );
    }

    @Test
    void populate_usesOnlyValidShopsForInsertionAndItemCatalogSize() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.shopEntryTarget()).thenReturn(12);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);
        String shopsCsv = """
                address
                "Дніпро, Запорізьке шосе, 62-К"
                invalid
                "Київ, вул. Кришталева, 6"
                """;
        InputStream shopsStream = new ByteArrayInputStream(shopsCsv.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ShopDto>> insertShopsCaptor = ArgumentCaptor.captor();
        verify(dbRepository).batchInsertShops(insertShopsCaptor.capture());
        assertEquals(List.of(
                new ShopDto("Дніпро, Запорізьке шосе, 62-К"),
                new ShopDto("Київ, вул. Кришталева, 6")
        ), insertShopsCaptor.getValue());

        ArgumentCaptor<List<ItemDto>> insertItemsCaptor = ArgumentCaptor.captor();
        verify(dbRepository).batchInsertItems(insertItemsCaptor.capture());
        assertEquals(6, insertItemsCaptor.getValue().size());
    }

    @Test
    void populate_throwsBeforeInsertingShopsWhenAllShopsAreInvalid() {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);
        InputStream shopsStream = new ByteArrayInputStream("address\ninvalid\n".getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> populator.populate(shopsStream, itemTypesStream));
        verify(dbRepository, never()).batchInsertShops(anyList());
    }

    /* populate ItemType */

    @ParameterizedTest
    @MethodSource("itemTypeInputs")
    void populate_passesExpectedItemTypesToRepository(int coefficient, List<ItemTypeDto> expectedItemTypes) throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(coefficient);
        when(config.shopEntryTarget()).thenReturn(18);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ItemTypeDto>> insertItemTypesCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertItemTypes(insertItemTypesCaptor.capture());
        assertEquals(expectedItemTypes, insertItemTypesCaptor.getValue());

    }
    static Stream<Arguments> itemTypeInputs() {
        return Stream.of(
                //val1 - expanding coefficient; val2 - expected result
                Arguments.of(
                        1,
                        List.of(
                                new ItemTypeDto("Будівельні матеріали 1"),
                                new ItemTypeDto("Електротехніка 1"),
                                new ItemTypeDto("Сантехніка 1")
                        )
                ),
                Arguments.of(
                        2,
                        List.of(
                                new ItemTypeDto("Будівельні матеріали 1"),
                                new ItemTypeDto("Будівельні матеріали 2"),
                                new ItemTypeDto("Електротехніка 1"),
                                new ItemTypeDto("Електротехніка 2"),
                                new ItemTypeDto("Сантехніка 1"),
                                new ItemTypeDto("Сантехніка 2")
                        )
                )
        );
    }

    @Test
    void populate_insertsOnlyValidExpandedItemTypes() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        when(config.shopEntryTarget()).thenReturn(6);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);
        String itemTypesCsv = """
                name
                Будівельні матеріали
                invalid
                Сантехніка
                """;
        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(itemTypesCsv.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ItemTypeDto>> insertItemTypesCaptor = ArgumentCaptor.captor();
        verify(dbRepository).batchInsertItemTypes(insertItemTypesCaptor.capture());
        assertEquals(List.of(
                new ItemTypeDto("Будівельні матеріали 1"),
                new ItemTypeDto("Сантехніка 1")
        ), insertItemTypesCaptor.getValue());
    }

    @Test
    void populate_throwsBeforeInsertingItemTypesWhenItemTypesCsvIsEmpty() {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);
        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream("name\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> populator.populate(shopsStream, itemTypesStream));
        verify(dbRepository, never()).batchInsertItemTypes(anyList());
    }

    @Test
    void populate_throwsBeforeInsertingItemTypesWhenAllExpandedTypesAreInvalid() {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);
        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream("name\ninvalid\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> populator.populate(shopsStream, itemTypesStream));
        verify(dbRepository, never()).batchInsertItemTypes(anyList());
    }

    /* populate Item */

    @Test
    void populate_passesItemCountCalculatedFromValidShopsToRepository() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(2);
        when(config.shopEntryTarget()).thenReturn(18);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ItemDto>> insertItemsCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertItems(insertItemsCaptor.capture());
        assertEquals(6, insertItemsCaptor.getValue().size());
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6),
                insertItemsCaptor.getValue().stream()
                        .map(ItemDto::typeId)
                        .sorted()
                        .toList()
        );


    }

    @Test
    void populate_roundsUpItemCountWhenTargetNotDivisibleByShopCount() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        when(config.shopEntryTarget()).thenReturn(17);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ItemDto>> insertItemsCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertItems(insertItemsCaptor.capture());
        assertEquals(6, insertItemsCaptor.getValue().size());
    }

    @Test
    void populate_usesOnlyExistingTypesWhenItemsOutnumberTypes() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        when(config.shopEntryTarget()).thenReturn(15);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ItemDto>> insertItemsCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertItems(insertItemsCaptor.capture());
        List<Integer> typeIds = insertItemsCaptor.getValue().stream()
                .map(ItemDto::typeId)
                .toList();
        assertEquals(5, typeIds.size());
        assertEquals(List.of(1, 2, 3), typeIds.stream().distinct().sorted().toList());
    }

    @Test
    void populate_throwsBeforeInsertingItemsWhenTypesOutnumberItemCatalog() {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(2);
        when(config.shopEntryTarget()).thenReturn(15);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> populator.populate(shopsStream, itemTypesStream));
        verify(dbRepository, never()).batchInsertItems(anyList());
    }

    @Test
    void populate_insertsOnlyItemsAcceptedByValidator() throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        when(config.shopEntryTarget()).thenReturn(12);
        DtoValidator validator = mock(DtoValidator.class);
        when(validator.isValid(any())).thenReturn(true);
        when(validator.isValid(any(ItemDto.class))).thenReturn(true, true, true, false);

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(SHOPS_CSV.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(ITEM_TYPES_CSV.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        verify(validator, times(4)).isValid(any(ItemDto.class));
        ArgumentCaptor<List<ItemDto>> insertItemsCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertItems(insertItemsCaptor.capture());
        assertEquals(3, insertItemsCaptor.getValue().size());
    }















}
