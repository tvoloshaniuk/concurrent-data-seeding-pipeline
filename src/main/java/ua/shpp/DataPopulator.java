package ua.shpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataPopulator {
    private static final int TYPE_VARIANTS_PER_BASE = 1000; // 20 базових категорій * 1000 = 20 000 ItemType

    private final DbRepository dbRepository;

    public DataPopulator(DbRepository dbRepository, AppConfig config) {
        this.dbRepository = dbRepository;
    }

    public void fillInTables(InputStream shopAddressesStream, InputStream itemTypesStream) {
        List<ShopDto> shops = readShops(shopAddressesStream);
        List<ItemTypeDto> itemTypes = readItemTypes(itemTypesStream);

        dbRepository.batchInsertShops(shops);
        dbRepository.batchInsertItemTypes(itemTypes);
    }

    private static List<ShopDto> readShops(InputStream stream) {
        return readLines(stream).stream()
                .map(DataPopulator::csvValue)
                .filter(value -> !value.equalsIgnoreCase("address"))
                .map(ShopDto::new)
                .filter(ValidatorUtil::isValid)
                .toList();
    }

    private static List<ItemTypeDto> readItemTypes(InputStream stream) {
        List<String> baseNames = readLines(stream).stream()
                .map(DataPopulator::csvValue)
                .filter(value -> !value.equalsIgnoreCase("name"))
                .toList();

        List<ItemTypeDto> itemTypes = new ArrayList<>(baseNames.size() * TYPE_VARIANTS_PER_BASE);
        for (String baseName : baseNames) {
            for (int variant = 1; variant <= TYPE_VARIANTS_PER_BASE; variant++) {
                itemTypes.add(new ItemTypeDto(baseName + " " + variant));
            }
        }
        return itemTypes.stream()
                .filter(ValidatorUtil::isValid)
                .toList();
    }

    private static List<String> readLines(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String csvValue(String line) {
        if (line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"")) {
            return line.substring(1, line.length() - 1);
        }
        return line;
    }
}
