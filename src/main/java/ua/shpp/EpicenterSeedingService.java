package ua.shpp;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.hibernateValidator.DtoValidator;
import ua.shpp.pipeline.ProducerConsumerPipeline;
import ua.shpp.utils.DataPopulator;
import ua.shpp.utils.ResourceLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;

/**
 * Owns the whole seed-and-search flow: create schema, fill the four tables (Shop, ItemType,
 * Item, ShopEntry), build indexes, then search for the top shop. main() just constructs one
 * instance and calls execute() - every actual step lives here as an instance method.
 */
public class EpicenterSeedingService {
    private static final Logger log = LoggerFactory.getLogger(EpicenterSeedingService.class);

    private final AppConfig config;
    private final DbRepository dbRepository;

    public EpicenterSeedingService(AppConfig config) {
        this(config, new DbRepository(initDatasource(config)));
    }

    /**
     * Package-private so tests can inject a stand-in repository: the public constructor wires
     * a real PGSimpleDataSource, which would make every test of this class need a database.
     */
    EpicenterSeedingService(AppConfig config, DbRepository dbRepository) {
        this.config = config;
        this.dbRepository = dbRepository;
    }

    /**
     * Owns the validator's lifetime because its useful life is exactly one run: created here,
     * shared by the populator and every consumer thread, and closed on the way out even if a
     * step throws. Nothing outside this method can be left holding a closed factory.
     */
    public void execute() throws InterruptedException {
        try (DtoValidator validator = new DtoValidator()) {
            dbRepository.runDdl(ResourceLoader.readText("schema.sql"));

            DataPopulator.PopulationSummary summary = fillFoundationTables(validator);
            verifyItemTypeExists();
            fillShopEntryTable(summary, validator);

            findAndLogTopShop("before indexes");

            // Create indexes after data population, not before -- to improve performance
            dbRepository.runDdl(ResourceLoader.readText("post_load_indexes.sql"));

            findAndLogTopShop("after indexes");
        }
    }

    // Fills Shop, ItemType and Item - the three small/sequential tables ShopEntry depends on.
    private DataPopulator.PopulationSummary fillFoundationTables(DtoValidator validator) {
        try (
                InputStream shopAddresses = ResourceLoader.stream("shops.csv");
                InputStream itemTypes = ResourceLoader.stream("item_types.csv")
        ) {
            DataPopulator populator = new DataPopulator(dbRepository, config, validator);
            return populator.fillFoundationTables(shopAddresses, itemTypes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fills the final, largest table (ShopEntry, 3M+ rows) via the parallel pipeline.
     * Producer (generation) vs consumer (insertion) timing/throughput is broken out inside
     * ProducerConsumerPipeline itself, since only it knows each phase's real start/end - this
     * is just the combined wall-clock total for the whole step.
     */
    private void fillShopEntryTable(DataPopulator.PopulationSummary summary, DtoValidator validator)
            throws InterruptedException {
        long startMillis = System.currentTimeMillis();
        new ProducerConsumerPipeline()
                .execute(dbRepository, validator, config, summary.shopCount(), summary.itemCatalogSize());
        log.info("ShopEntry generation+insertion took {} ms", System.currentTimeMillis() - startMillis);
    }

    private void verifyItemTypeExists() {
        if (!dbRepository.existsItemType(config.itemType())) {
            throw new IllegalArgumentException(String.format(
                    "Unknown itemType '%s'. ItemType names are base categories from item_types.csv "
                            + "with a numeric suffix 1..%d appended - try '%s 1'.",
                    config.itemType(), config.typeIncreaseCoefficient(), config.itemType()));
        }
    }

    private void findAndLogTopShop(String phase) {
        long startMillis = System.currentTimeMillis();
        String topShop = dbRepository.findShopWithMaxItems(config.itemType());
        long searchMillis = System.currentTimeMillis() - startMillis;
        // Expected as an unreachable case because of the previous "fail fast" check
        if (topShop == null) {
            log.warn(
                    "No shop found for itemType '{}' ({}) after {} ms, even though the type exists. "
                            + "Check the ShopEntryGenerator/DataPopulator invariants.",
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
