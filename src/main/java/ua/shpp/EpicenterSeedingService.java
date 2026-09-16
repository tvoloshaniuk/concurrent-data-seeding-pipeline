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

    }

    // Fills Shop, ItemType and Item - the three small/sequential tables ShopEntry depends on.
    private CatalogDimensions fillFoundationTables(DtoValidator validator) {
        '
    }

    /**
     * Fills the final, largest table (ShopEntry, 3M+ rows) via the parallel pipeline.
     * Producer (generation) vs consumer (insertion).
     */
    private void fillShopEntryTable(CatalogDimensions dimensions, DtoValidator validator) throws InterruptedException {

    }

    /**
     * Runs before the 3M-row pipeline, so an unusable itemType costs seconds instead of minutes.
     */
    private void verifyItemTypeIsSearchable() {

    }

    private void findAndLogTopShop(String phase) {

    }

    private static DataSource initDatasource(AppConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.dbUrl());
        ds.setUser(config.dbUser());
        ds.setPassword(config.dbPassword());
        return ds;
    }
}
