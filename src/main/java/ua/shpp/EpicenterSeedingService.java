package ua.shpp;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.validation.DtoValidator;
import ua.shpp.pipeline.ProducerConsumerPipeline;
import ua.shpp.utils.CatalogDimensions;
import ua.shpp.utils.FoundationTablesPopulator;
import ua.shpp.utils.ResourceLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;

/**
 * Owns the whole seed-and-search flow: create schema, fill the four tables (Shop, ItemType,
 * Item, ShopEntry), build indexes, then search for the top shop. main() just constructs one
 * instance and calls execute().
 */
public class EpicenterSeedingService {
    private static final Logger log = LoggerFactory.getLogger(EpicenterSeedingService.class);

    private final AppConfig config;
    private final DbRepository dbRepository;

    public EpicenterSeedingService(AppConfig config) {
        this(config, new DbRepository(initDatasource(config)));
    }

    EpicenterSeedingService(AppConfig config, DbRepository dbRepository) {
        this.config = config;
        this.dbRepository = dbRepository;
    }

    public void execute() throws InterruptedException {
        try (DtoValidator validator = new DtoValidator()) {
            if (shouldSeed()) {
                seed(validator);
            }
            verifyItemTypeIsSearchable();
            findAndLogTopShop("after indexes");
        }
    }

    private boolean shouldSeed() {
        if (config.recreateSchema()) {
            return true;
        }
        if (dbRepository.hasShopEntries()) {
            log.info("recreate.schema=false and ShopEntry already holds data - skipping generation");
            return false;
        }
        log.info("recreate.schema=false but ShopEntry is empty - seeding anyway"); //todo
        return true;
    }

    //fill tables & create indexes
    private void seed(DtoValidator validator) throws InterruptedException {
        dbRepository.runDdl(ResourceLoader.readText("schema.sql"));

        CatalogDimensions dimensions = fillFoundationTables(validator);
        verifyItemTypeIsSearchable();
        fillShopEntryTable(dimensions, validator);

        findAndLogTopShop("before indexes");

        // Create indexes after data population, not before -- to improve performance
        dbRepository.runDdl(ResourceLoader.readText("post_load_indexes.sql"));
    }

    // Fills Shop, ItemType and Item - the three small/sequential tables ShopEntry depends on.
    private CatalogDimensions fillFoundationTables(DtoValidator validator) {
        try (
                InputStream shopAddresses = ResourceLoader.stream("shops.csv");
                InputStream itemTypes = ResourceLoader.stream("item_types.csv")
        ) {
            FoundationTablesPopulator populator = new FoundationTablesPopulator(dbRepository, config, validator);
            return populator.populate(shopAddresses, itemTypes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fills the final, largest table (ShopEntry, 3M+ rows) via the parallel pipeline.
     * Producer (generation) vs consumer (insertion).
     */
    private void fillShopEntryTable(CatalogDimensions dimensions, DtoValidator validator)
            throws InterruptedException {
        long startMillis = System.currentTimeMillis();
        new ProducerConsumerPipeline()
                .execute(dbRepository, validator, config, dimensions);
        log.info("ShopEntry generation+insertion took {} ms", System.currentTimeMillis() - startMillis);
    }

    /**
     * Runs before the 3M-row pipeline, so an unusable itemType costs seconds instead of minutes.
     */
    private void verifyItemTypeIsSearchable() {
        if (!dbRepository.existsItemType(config.itemType())) {
            throw new IllegalArgumentException(String.format(
                    "Unknown itemType '%s'. ItemType names are base categories from item_types.csv "
                            + "with a numeric suffix 1..%d appended - try '%s 1'.",
                    config.itemType(), config.typeIncreaseCoefficient(), config.itemType()));
        }
        if (!dbRepository.existsItemTypeWithItems(config.itemType())) {
            throw new IllegalArgumentException(String.format(
                    "itemType '%s' exists but holds no items, so no shop can stock it. The item "
                            + "catalogue is smaller than the type list - lower typeIncreaseCoefficient "
                            + "or raise shopEntryTarget.",
                    config.itemType()));
        }
    }

    private void findAndLogTopShop(String phase) {
        long startMillis = System.currentTimeMillis();
        String topShop = dbRepository.findShopWithMaxItems(config.itemType());
        long searchMillis = System.currentTimeMillis() - startMillis;
        // todo Expected as an unreachable case because of the previous "fail fast" check
        if (topShop == null) {
            log.warn(
                    "No shop found for itemType '{}' ({}) after {} ms, even though the type exists. "
                            + "Check the ShopEntryGenerator/FoundationTablesPopulator invariants.",
                    config.itemType(), phase, searchMillis);
            return;
        }
        log.info("Top Shop ({}): {} (found in {} ms)", phase, topShop, searchMillis);
    }

    private static DataSource initDatasource(AppConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.dbUrl());
        ds.setUser(config.dbUser());
        ds.setPassword(config.dbPassword());
        return ds;
    }
}
