package ua.shpp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.validation.DtoValidator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FoundationTablesPopulator {
    private static final Logger log = LoggerFactory.getLogger(FoundationTablesPopulator.class);
    DbRepository dbRepository;
    AppConfig config;
    DtoValidator validator;

    public FoundationTablesPopulator(DbRepository dbRepository, AppConfig config, DtoValidator validator) {
        this.dbRepository = dbRepository;
        this.config = config;
        this.validator = validator;
    }

    public void populate(InputStream shopsCsvStream, InputStream itemTypesCsvStream) throws IOException {
        // fill shop
        List<ShopDto> shopAddresses = loadShopAddresses(shopsCsvStream);
        dbRepository.batchInsertShops(shopAddresses);
        // fill itemType
        List<ItemTypeDto> itemTypes = loadAndExpandItemTypes(itemTypesCsvStream);

        dbRepository.batchInsertItemTypes(itemTypes);
        // fill item
        List<ItemDto> items = generateItems();
        dbRepository.batchInsertItems(items);
    }

    private List<ShopDto> loadShopAddresses(InputStream shopsCsvStream) {
        return CsvColumnReader.readFirstColumn(shopsCsvStream).stream()
                .map(ShopDto::new)
                .filter(validator::isValid)
                .toList();
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
        warnAboutDropped("item_types.csv", itemTypes.size(), validItemTypes.size());

        return validItemTypes;
    }

    private void warnAboutDropped(String source, int read, int kept) {
        log.warn(
                "{} was expanded to {} types, but after validation kept only {} of them. Others were filtered out"
                , source, read, kept
        );
    }

    private List<ItemDto> generateItems() {
        // todo
        return null;
    }

}