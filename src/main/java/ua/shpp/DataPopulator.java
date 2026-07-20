package ua.shpp;

import ua.shpp.config.AppConfig;
import ua.shpp.csv.CsvColumnReader;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.hibernateValidator.ValidatorUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// Fills the tables that can be populated in a single sequential pass (no parallel
// pipeline needed): Shop and ItemType — directly from CSV, Item — CSV categories plus
// its own generation. ShopEntry (3M+ rows) is deliberately out of scope here — it gets
// its own parallel pipeline (ShopEntryGenerator + ProducerConsumerPipeline), because its
// volume and usage pattern are completely different.
public class DataPopulator {
    private final DbRepository dbRepository;
    private final AppConfig config;
    private final Random random = new Random();

    public DataPopulator(DbRepository dbRepository, AppConfig config) {
        this.dbRepository = dbRepository;
        this.config = config;
    }

    public void fillInTables(InputStream shopAddressesStream, InputStream itemTypesStream) {
        List<ShopDto> shops = readShops(shopAddressesStream);
        List<ItemTypeDto> itemTypes = readItemTypes(itemTypesStream);

        dbRepository.batchInsertShops(shops);
        dbRepository.batchInsertItemTypes(itemTypes);

        List<ItemDto> items = generateItems(shops.size(), itemTypes.size());
        dbRepository.batchInsertItems(items);
    }

    private static List<ShopDto> readShops(InputStream stream) {
        List<ShopDto> shops = CsvColumnReader.readSingleColumn(stream).stream()
                .map(ShopDto::new)
                .filter(ValidatorUtil::isValid)
                .toList();
        if (shops.size() <= 1) {
            throw new IllegalStateException("shops.csv has too few rows: " + shops.size());
        }
        return shops;
    }

    private List<ItemTypeDto> readItemTypes(InputStream stream) {
        List<String> baseNames = CsvColumnReader.readSingleColumn(stream);
        if (baseNames.size() <= 1) {
            throw new IllegalStateException("item_types.csv has too few base categories: " + baseNames.size());
        }

        int typeIncreaseCoefficient = config.typeIncreaseCoefficient();
        List<ItemTypeDto> itemTypes = new ArrayList<>(baseNames.size() * typeIncreaseCoefficient);
        for (String baseName : baseNames) {
            for (int variant = 1; variant <= typeIncreaseCoefficient; variant++) {
                itemTypes.add(new ItemTypeDto(baseName + " " + variant));
            }
        }
        return itemTypes.stream()
                .filter(ValidatorUtil::isValid)
                .toList();
    }

    // itemCatalogSize = ceil(shopEntryTarget / shopCount): enough items so the full
    // deterministic enumeration in ShopEntryGenerator reliably reaches the ShopEntry target.
    // The first itemTypeCount items are assigned types in order, guaranteeing at least one
    // item per type; the rest get a fully random type.
    private List<ItemDto> generateItems(int shopCount, int itemTypeCount) {
        int itemCatalogSize = ceilDiv(config.shopEntryTarget(), shopCount);
        if (itemCatalogSize < itemTypeCount) {
            throw new IllegalStateException(
                    "itemCatalogSize (" + itemCatalogSize + ") must be >= itemTypeCount (" + itemTypeCount
                            + ") to cover every type — increase shopEntryTarget or decrease typeIncreaseCoefficient");
        }

        List<ItemDto> items = new ArrayList<>(itemCatalogSize);
        for (int i = 1; i <= itemCatalogSize; i++) {
            String name = UUID.randomUUID().toString();
            int typeId = i <= itemTypeCount ? i : 1 + random.nextInt(itemTypeCount);
            items.add(new ItemDto(i, name, typeId));
        }
        return items.stream()
                .filter(ValidatorUtil::isValid)
                .toList();
    }

    private static int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
