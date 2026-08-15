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
 * Fills the tables that can be populated in a single sequential pass (no parallel pipeline needed):
 *  Shop and ItemType - directly from CSV, Item - CSV categories plus its own generation.
 * ShopEntry (3M+ rows) is out of scope here - it gets its own parallel pipeline
 *  (ShopEntryGenerator + ProducerConsumerPipeline).
 */
public class DataPopulator {
    private static final Logger log = LoggerFactory.getLogger(DataPopulator.class);

    private final DbRepository dbRepository;
    private final AppConfig config;
    private final DtoValidator validator;
    private final ItemGenerator itemGenerator;
    private final Random random = new Random();

    public DataPopulator(DbRepository dbRepository, AppConfig config, DtoValidator validator) {
        this.dbRepository = dbRepository;
        this.config = config;
        this.validator = validator;
        this.itemGenerator = new ItemGenerator(config.invalidRatePercent());
    }

    public CatalogDimensions fillFoundationTables(InputStream shopAddressesStream, InputStream itemTypesStream) {
        List<ShopDto> shops = readShops(shopAddressesStream);
        dbRepository.batchInsertShops(shops);

        List<ItemTypeDto> itemTypes = readAndExpandItemTypes(itemTypesStream);
        dbRepository.batchInsertItemTypes(itemTypes);

        int itemCatalogSize = Math.ceilDiv(config.shopEntryTarget(), shops.size());
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
     * Generous enough that no realistic invalidRatePercent trips it, tight enough that a rate of
     * 100 fails in bounded time instead of looping forever.
     */
    private static int maxAttemptsFor(int itemCatalogSize) {
        return itemCatalogSize * 100 + 1000;
    }

    /**
     * Filtering used to happen in silence, which defeats the point of validating at all - a bad
     * row would simply vanish and nobody would learn about it.
     */
    private void warnAboutDropped(String source, int read, int kept) {
        if (kept != read) {
            log.warn("{}: {} of {} entries failed validation and were dropped", source, read - kept, read);
        }
    }

    /**
     * Keeps generating until exactly itemCatalogSize items pass validation, rather than
     * generating that many and filtering afterwards. The difference matters: Item.id is a SERIAL,
     * so dropping a candidate after the fact would leave the table with fewer ids than
     * ShopEntryGenerator later enumerates, and every ShopEntry row pointing at a missing id would
     * violate the foreign key. Retrying instead keeps the count exact, which is what lets
     * invalidRatePercent be raised without touching plannedRows or the row target.
     * <p>
     * The first itemTypeCount accepted items take types in order, guaranteeing at least one item
     * per type; numbering follows accepted items, so a rejected candidate is retried at the same
     * position and no type loses its guaranteed item.
     */
    private List<ItemDto> generateItems(int itemCatalogSize, int itemTypeCount) {
        if (itemCatalogSize < itemTypeCount) {
            throw new IllegalStateException(
                    "itemCatalogSize (" + itemCatalogSize + ") must be >= itemTypeCount (" + itemTypeCount
                            + ") to cover every type - increase shopEntryTarget or decrease typeIncreaseCoefficient");
        }

        int maxAttempts = maxAttemptsFor(itemCatalogSize);
        List<ItemDto> items = new ArrayList<>(itemCatalogSize);
        int attempts = 0;
        while (items.size() < itemCatalogSize) {
            if (++attempts > maxAttempts) {
                throw new IllegalStateException("Generated " + attempts + " candidates but only "
                        + items.size() + " of the required " + itemCatalogSize + " were valid - "
                        + "invalidRatePercent=" + config.invalidRatePercent() + " is too high");
            }
            int position = items.size() + 1;
            int typeId = position <= itemTypeCount ? position : 1 + random.nextInt(itemTypeCount);
            ItemDto candidate = itemGenerator.generate(position, typeId);
            if (validator.isValid(candidate)) {
                items.add(candidate);
            }
        }
        warnAboutDropped("generated items", attempts, items.size());
        return items;
    }
}
