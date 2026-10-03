package ua.shpp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.generation.ItemGenerator;
import ua.shpp.validation.DtoValidator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Math.ceilDiv;

public class FoundationTablesPopulator {
    private static final Logger log = LoggerFactory.getLogger(FoundationTablesPopulator.class);

    DbRepository dbRepository;
    AppConfig config;
    DtoValidator validator;
    private final ItemGenerator itemGenerator;

    public FoundationTablesPopulator(DbRepository dbRepository, AppConfig config, DtoValidator validator) {
        this.dbRepository = dbRepository;
        this.config = config;
        this.validator = validator;
        this.itemGenerator = new ItemGenerator(config.invalidRatePercent());
    }

    public void populate(InputStream shopsCsvStream, InputStream itemTypesCsvStream) throws IOException {
        // fill shop
        List<ShopDto> shopAddresses = loadShopAddresses(shopsCsvStream);
        dbRepository.batchInsertShops(shopAddresses);
        // fill itemType
        List<ItemTypeDto> itemTypes = loadAndExpandItemTypes(itemTypesCsvStream);
        dbRepository.batchInsertItemTypes(itemTypes);
        // fill item
        int itemCatalogSize = ceilDiv(config.shopEntryTarget(), shopAddresses.size());
        List<ItemDto> items = generateItems(itemCatalogSize, itemTypes.size());
        dbRepository.batchInsertItems(items);
    }

    private List<ShopDto> loadShopAddresses(InputStream shopsCsvStream) {
        List<ShopDto> shopAddresses = CsvColumnReader.readFirstColumn(shopsCsvStream).stream()
                .map(ShopDto::new)
                .filter(validator::isValid)
                .toList();
        if (shopAddresses.isEmpty()) {
            throw new IllegalStateException("No valid shops found");
        }
        return shopAddresses;
    }


    private List<ItemTypeDto> loadAndExpandItemTypes(InputStream itemTypesCsvStream) {
        List<String> baseNames = CsvColumnReader.readFirstColumn(itemTypesCsvStream);
        if (baseNames.isEmpty()) {
            throw new IllegalStateException("item_types.csv contains no base categories");
        }

        List<ItemTypeDto> itemTypes = new ArrayList<>();
        for (String baseName : baseNames) {
            for (int suffix = 1; suffix <= config.typeIncreaseCoefficient(); suffix++) {
                itemTypes.add(new ItemTypeDto(baseName + " " + suffix));
            }
        }

        List<ItemTypeDto> validItemTypes = itemTypes.stream()
                .filter(validator::isValid)
                .toList();
        if (validItemTypes.isEmpty()) {
            throw new IllegalStateException("item_types.csv contains no valid base categories");
        }
        warnAboutDropped(itemTypes.size(), validItemTypes.size());

        return validItemTypes;
    }

    private void warnAboutDropped(int read, int kept) {
        log.warn(
                "item_types.csv was expanded to {} types, but after validation kept only {} of them. Others were filtered out"
                , read, kept
        );
    }

    private List<ItemDto> generateItems(int itemCatalogSize, int typeCatalogSize) {
        if (itemCatalogSize <= 0) {
            throw new IllegalStateException("itemCatalogSize must be positive");
        }
        if (typeCatalogSize > itemCatalogSize) {
            throw new IllegalStateException("typeCatalogSize must not exceed itemCatalogSize");
        }

        List<ItemDto> items = new ArrayList<>(itemCatalogSize);
        int position = 1;
        while (position <= itemCatalogSize) {
            ItemDto item = new ItemDto(
                    itemGenerator.generateItemName(position),
                    position <= typeCatalogSize ? position : 1 + ThreadLocalRandom.current().nextInt(typeCatalogSize)
            );
            if (validator.isValid(item)) {
                items.add(item);
            } else {
                log.warn("Generated invalid item: {}", item);
            }
            position++;
        }
        return items;
    }

}
