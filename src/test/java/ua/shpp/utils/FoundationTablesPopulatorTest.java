package ua.shpp.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
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
import static org.mockito.Mockito.*;

class FoundationTablesPopulatorTest {


    public static Stream<Arguments> csvInputs() {
        return Stream.of(
                Arguments.of(
                        """
                                address
                                "Дніпро, Запорізьке шосе, 62-К"
                                "Київ, вул. Кришталева, 6"
                                "Одеса, пр-т Небесної Сотні, 99"
                                """,
                        """
                                name
                                Будівельні матеріали
                                Електротехніка
                                Сантехніка
                                """
                )
        );
    }

    @ParameterizedTest
    @MethodSource("csvInputs")
    void populate_passesExpectedShopsToRepository(String shopsCsvText, String itemTypesCsvText) throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(1);
        DtoValidator validator = new DtoValidator();


        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(shopsCsvText.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(itemTypesCsvText.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ShopDto>> insertShopsCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertShops(insertShopsCaptor.capture());
        assertEquals(
                List.of(new ShopDto("Дніпро, Запорізьке шосе, 62-К"),
                        new ShopDto("Київ, вул. Кришталева, 6"),
                        new ShopDto("Одеса, пр-т Небесної Сотні, 99")
                ),
                insertShopsCaptor.getValue()
        );

    }
    @ParameterizedTest
    @MethodSource("csvInputs")
    void populate_passesSixItemTypesToRepository_whenCoefficientIs2And3CsvEntries(String shopsCsvText, String itemTypesCsvText) throws IOException {
        DbRepository dbRepository = mock(DbRepository.class);
        AppConfig config = mock(AppConfig.class);
        when(config.typeIncreaseCoefficient()).thenReturn(2);
        DtoValidator validator = new DtoValidator();

        FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);

        InputStream shopsStream = new ByteArrayInputStream(shopsCsvText.getBytes(StandardCharsets.UTF_8));
        InputStream itemTypesStream = new ByteArrayInputStream(itemTypesCsvText.getBytes(StandardCharsets.UTF_8));

        populator.populate(shopsStream, itemTypesStream);

        ArgumentCaptor<List<ItemTypeDto>> insertItemTypesCaptor = ArgumentCaptor.captor();
        verify(dbRepository, times(1)).batchInsertItemTypes(insertItemTypesCaptor.capture());
        assertEquals(
                List.of(new ItemTypeDto("Будівельні матеріали 1"),
                        new ItemTypeDto("Будівельні матеріали 2"),
                        new ItemTypeDto("Електротехніка 1"),
                        new ItemTypeDto("Електротехніка 2"),
                        new ItemTypeDto("Сантехніка 1"),
                        new ItemTypeDto("Сантехніка 2")
                ),
                insertItemTypesCaptor.getValue()
        );

    }
}