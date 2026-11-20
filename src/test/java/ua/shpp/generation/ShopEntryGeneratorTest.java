package ua.shpp.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.shpp.dto.ShopEntryDto;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopEntryGeneratorTest {
    /* Only valid */

    @Test
    void generateForShop_returnsCatalogSizeEntriesWhenInvalidRateIsZero() {
        int invalidRatePercent = 0;
        int maxStockQuantity = 100;
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity);
        int shopId = 1;
        int shopCount = 5;
        int itemCatalogSize = 10;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(itemCatalogSize, generatedEntries.size());
    }

    @Test
    void generateForShop_generatesEveryValidItemIdExactlyOnceInAscendingOrder() {
        int invalidRatePercent = 0;
        int maxStockQuantity = 100;
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity);
        int shopId = 2;
        int shopCount = 5;
        int itemCatalogSize = 10;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(itemCatalogSize, generatedEntries.size());
        for (int i = 0; i < generatedEntries.size(); i++) {
            ShopEntryDto entry = generatedEntries.get(i);
            assertEquals(i + 1, entry.itemId());
            assertEquals(shopId, entry.shopId());
            assertTrue(entry.itemCount() >= 0 && entry.itemCount() <= maxStockQuantity);
        }
    }

    @Test
    void generateForShop_generatesZeroItemCountsWhenMaxStockQuantityIsZero() {
        int invalidRatePercent = 0;
        int maxStockQuantity = 0;
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity);
        int shopId = 4;
        int shopCount = 5;
        int itemCatalogSize = 10;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(itemCatalogSize, generatedEntries.size());
        for (ShopEntryDto entry : generatedEntries) {
            assertEquals(0, entry.itemCount());
        }
    }

    /* Valid + invalid */

    @ParameterizedTest
    @CsvSource({
            "3, 0, 3",
            "3, 1, 4",
            "3, 25, 4",
            "3, 50, 5",
            "4, 50, 6",
            "3, 100, 6"
    })
    void generateForShop_returnsExpectedTotalCountForConfiguredRate(
            int itemCatalogSize, int invalidRatePercent, int expectedTotalCount
    ) {
        int maxStockQuantity = 100;
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity);
        int shopId = 3;
        int shopCount = 5;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(expectedTotalCount, generatedEntries.size());
    }

    @Test
    void generateForShop_setsItemIdToZeroAndKeepsOtherFieldsValid() {
        //arrange
        int invalidRatePercent = 100;
        int maxStockQuantity = 0;
        Random rand = mock(Random.class);
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity, rand);

        //3 because algorithm uses 0,1,2 to determine which of 3 fields to make out of valid range
        when(rand.nextInt(3)).thenReturn(0);  //0 means itemId will be invalid in all iterations

        int shopId = 3;
        int shopCount = 5;
        int itemCatalogSize = 1;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(2, generatedEntries.size());
        assertTrue(generatedEntries.contains(new ShopEntryDto(1, shopId, 0)));
        assertTrue(generatedEntries.contains(new ShopEntryDto(0, shopId, 0))); //invalid itemid=0
    }

    @Test
    void generateForShop_setsShopIdToZeroAndKeepsOtherFieldsValid() {
        //arrange
        int invalidRatePercent = 100;
        int maxStockQuantity = 0;
        Random rand = mock(Random.class);
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity, rand);

        //3 because algorithm uses 0,1,2 to determine which of 3 fields to make out of valid range
        when(rand.nextInt(3)).thenReturn(1);  //1 means shopId will be invalid in all iterations

        int shopId = 3;
        int shopCount = 5;
        int itemCatalogSize = 1;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(2, generatedEntries.size());
        assertTrue(generatedEntries.contains(new ShopEntryDto(1, shopId, 0)));
        assertTrue(generatedEntries.contains(new ShopEntryDto(1, 0, 0))); //invalid shopId=0
    }

    @Test
    void generateForShop_setsItemCountToMinusOneAndKeepsOtherFieldsValid() {
        //arrange
        int invalidRatePercent = 100;
        int maxStockQuantity = 0;
        Random rand = mock(Random.class);
        ShopEntryGenerator generator = new ShopEntryGenerator(invalidRatePercent, maxStockQuantity, rand);

        //3 because algorithm uses 0,1,2 to determine which of 3 fields to make out of valid range
        when(rand.nextInt(3)).thenReturn(2);  //2 means itemCount will be invalid in all iterations

        int shopId = 3;
        int shopCount = 5;
        int itemCatalogSize = 1;
        //act
        List<ShopEntryDto> generatedEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        //assert
        assertEquals(2, generatedEntries.size());
        assertTrue(generatedEntries.contains(new ShopEntryDto(1, shopId, 0)));
        assertTrue(generatedEntries.contains(new ShopEntryDto(1, shopId, -1))); //invalid itemCount=-1
    }



}