package ua.shpp;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.pipeline.ProducerConsumerPipeline;
import ua.shpp.utils.CatalogDimensions;
import ua.shpp.utils.FoundationTablesPopulator;
import ua.shpp.utils.ResourceLoader;
import ua.shpp.validation.DtoValidator;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

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

    // Constructor for testing, so we can inject a mock DbRepository
    EpicenterSeedingService(AppConfig config, DbRepository dbRepository) {
        this.config = config;
        this.dbRepository = dbRepository;
    }

    public void execute() throws InterruptedException, IOException, SQLException {
        try (DtoValidator validator = new DtoValidator()) {
            if (config.recreateSchema()) {
                log.info("recreate.schema=true. Attempt to seed (with previous drop of all tables)...");
                dbRepository.runDdl(ResourceLoader.readText("drop_all_tables.sql"));
                seed(validator);
                log.info("Seed completed");
            }
            verifyItemTypeIsSearchable(); //todo Q: do I need a test for this?
            if (config.recreateIndexes()) {
                log.info("recreate.indexes=true. Attempt to drop indexes...");
                dbRepository.runDdl(ResourceLoader.readText("drop_post_load_indexes.sql"));
                log.info("Indexes dropped");

                findAndLogTopShop();

                log.info("recreate.indexes=true. Attempt to create indexes...");
                dbRepository.runDdl(ResourceLoader.readText("post_load_indexes.sql"));
                log.info("Indexes created");
            }
            findAndLogTopShop(); //todo Q: do I need a test for this?
        }
    }

    //fill tables
    private void seed(DtoValidator validator) throws InterruptedException, IOException, SQLException {
        dbRepository.runDdl(ResourceLoader.readText("schema.sql"));
        CatalogDimensions dimensions = fillFoundationTables(validator);
        fillShopEntryTable(dimensions, validator);
    }

    // Fills Shop, ItemType and Item - the three small/sequential tables ShopEntry depends on.
    private CatalogDimensions fillFoundationTables(DtoValidator validator) throws IOException, SQLException {
        FoundationTablesPopulator foundationTablesPopulator =
                new FoundationTablesPopulator(dbRepository, config, validator);
        try (
                InputStream shopsCsvStream = ResourceLoader.stream("shops.csv");
                InputStream itemTypesCsvStream = ResourceLoader.stream("item_types.csv")
        ) {
            return foundationTablesPopulator.populate(shopsCsvStream, itemTypesCsvStream);
        }

    }

    /**
     * Fills the final, largest table (ShopEntry, 3M+ rows) via the parallel pipeline.
     * Producer (generation) -> queue -> consumer (insertion).
     */
    private void fillShopEntryTable(CatalogDimensions dimensions, DtoValidator validator) throws InterruptedException {
        ProducerConsumerPipeline.execute(dimensions, validator, config);
    }

    /**
     * Runs before the 3M-row pipeline, so an unusable itemType costs seconds instead of minutes.
     */
    private void verifyItemTypeIsSearchable() {

    }

    private void findAndLogTopShop() {
        //todo
    }

    private static DataSource initDatasource(AppConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.dbUrl());
        ds.setUser(config.dbUser());
        ds.setPassword(config.dbPassword());
        return ds;
    }
}
