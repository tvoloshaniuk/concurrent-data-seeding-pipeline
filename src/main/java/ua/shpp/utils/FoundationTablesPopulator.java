package ua.shpp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.validation.DtoValidator;

import ua.shpp.generation.ItemGenerator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * Fills the tables that can be populated in a single thread (no parallel pipeline needed):
 *  Shop and ItemType - directly from CSV, Item - CSV categories plus its own generation.
 * ShopEntry (3M+ rows) is out of scope here - it gets its own parallel pipeline
 *  (ShopEntryGenerator + ProducerConsumerPipeline).
 */
public class FoundationTablesPopulator {
    private static final Logger log = LoggerFactory.getLogger(FoundationTablesPopulator.class);

    private final DbRepository dbRepository;
    private final AppConfig config;
    private final DtoValidator validator;
    private final ItemGenerator itemGenerator;
    private final Random random = new Random();

    public FoundationTablesPopulator(DbRepository dbRepository, AppConfig config, DtoValidator validator) {
        this.dbRepository = dbRepository;
        this.config = config;
        this.validator = validator;
        this.itemGenerator = new ItemGenerator(config.invalidRatePercent());
    }

    public CatalogDimensions populate(InputStream shopAddressesStream, InputStream itemTypesStream) {
        List<ShopDto> shops = readShops(shopAddressesStream);
        dbRepository.batchInsertShops(shops);

        List<ItemTypeDto> itemTypes = readAndExpandItemTypes(itemTypesStream);
        dbRepository.batchInsertItemTypes(itemTypes);

        int itemCatalogSize = Math.ceilDiv(config.shopEntryTarget(), shops.size()); //3_000_000 / shopsN
        List<ItemDto> items = generateItems(itemCatalogSize, itemTypes.size());
        dbRepository.batchInsertItems(items);

        return new CatalogDimensions(shops.size(), itemCatalogSize);
    }

    private List<ShopDto> readShops(InputStream stream) {
        List<String> addresses = CsvColumnReader.readFirstColumn(stream);
        List<ShopDto> shops = addresses.stream()
                .map(ShopDto::new)
                .filter(validator::isValid)
                .toList();
        warnAboutDropped("shops.csv", addresses.size(), shops.size());
        if (shops.isEmpty()) {
            throw new IllegalStateException("shops.csv contains no valid rows");
        }
        return shops;
    }

    private List<ItemTypeDto> readAndExpandItemTypes(InputStream stream) {
        List<String> baseNames = CsvColumnReader.readFirstColumn(stream);
        if (baseNames.isEmpty()) {
            throw new IllegalStateException("item_types.csv contains no base categories");
        }

        int typeIncreaseCoefficient = config.typeIncreaseCoefficient();
        List<ItemTypeDto> itemTypes = new ArrayList<>(baseNames.size() * typeIncreaseCoefficient);
        for (String baseName : baseNames) {
            for (int suffix = 1; suffix <= typeIncreaseCoefficient; suffix++) {
                itemTypes.add(new ItemTypeDto(baseName + " " + suffix));
            }
        }
        List<ItemTypeDto> validTypes = itemTypes.stream()
                .filter(validator::isValid)
                .toList();
        warnAboutDropped("item_types.csv", itemTypes.size(), validTypes.size());
        return validTypes;
    }

    /**
     * Filtering used to happen in silence, which defeats the point of validating at all - a bad
     * row would simply vanish and nobody would learn about it.
     */
    private void warnAboutDropped(String source, int read, int kept) {
        if (kept != read) {
            log.warn("{}: {} of {} entries failed validation and were dropped", source, read - kept, read); //todo Q може краще змінити на info, бо це не помилка, а просто інфо про те що деякі дані не валідні
        }
    }

    /**
     * Returns the data to insert into the Item table.
     * Keeps generating until exactly itemCatalogSize items pass validation and are returned.
     * @param itemCatalogSize how many valid items to return - ceilDiv(shopEntryTarget, shopCount)
     * @param typeCatalogSize how many ItemType rows are there to assign type_id to the items
     */
    private List<ItemDto> generateItems(int itemCatalogSize, int typeCatalogSize) {
        warnAboutUncoveredTypes(itemCatalogSize, typeCatalogSize);

        List<ItemDto> items = new ArrayList<>(itemCatalogSize);
        int candidatesGenerated = 0;
        while (items.size() < itemCatalogSize) {
            candidatesGenerated++;
            int position = items.size() + 1;
            ItemDto candidate = itemGenerator.generate(position, typeIdFor(position, typeCatalogSize));
            if (validator.isValid(candidate)) {
                items.add(candidate);
            }
        }
        warnAboutDropped("generated items", candidatesGenerated, items.size());
        return items;
    }

    /**
     * Defines type for the item at the given position in the catalogue. The first typeCatalogSize items take
     * their type in order, guaranteeing every type at least one item; the rest are spread at random.
     */
    private int typeIdFor(int position, int typeCatalogSize) {
        return position <= typeCatalogSize ? position : 1 + random.nextInt(typeCatalogSize);
    }

    private void warnAboutUncoveredTypes(int itemCatalogSize, int typeCatalogSize) {
        if (itemCatalogSize < typeCatalogSize) {
            log.warn("Item catalogue holds {} items but there are {} types - the last {} types get no "
                            + "item and cannot be searched. Lower typeIncreaseCoefficient or raise shopEntryTarget.",
                    itemCatalogSize, typeCatalogSize, typeCatalogSize - itemCatalogSize);
        }
    }
}
