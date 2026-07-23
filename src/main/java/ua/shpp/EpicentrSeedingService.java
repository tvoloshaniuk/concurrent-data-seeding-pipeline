package ua.shpp;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.utils.ResourceLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;

/**
 * Owns the whole seed-and-search flow: create schema, fill the four tables (Shop, ItemType,
 * Item, ShopEntry), build indexes, then search for the top shop. main() just constructs one
 * instance and calls run() - every actual step lives here as an instance method.
 */
public class EpicentrSeedingService {
    private static final Logger log = LoggerFactory.getLogger(EpicentrSeedingService.class);

    private final AppConfig config;
    private final DbRepository dbRepository;

    public EpicentrSeedingService(AppConfig config) {
        this.config = config;
        this.dbRepository = new DbRepository(initDatasource(config));
    }

    public void run() throws InterruptedException {
        dbRepository.runDdl(ResourceLoader.readText("schema.sql"));

        DataPopulator.PopulationSummary summary = fillFoundationTables();
        fillShopEntryTable(summary);

        // Create indexes after data population, not before -- to improve performance
        dbRepository.runDdl(ResourceLoader.readText("post_load_indexes.sql"));

        findAndLogTopShop();
    }

    // Fills Shop, ItemType and Item - the three small/sequential tables ShopEntry depends on.
    private DataPopulator.PopulationSummary fillFoundationTables() {
        try (
                InputStream shopAddresses = ResourceLoader.stream("shops.csv");
                InputStream itemTypes = ResourceLoader.stream("item_types.csv")
        ) {
            DataPopulator populator = new DataPopulator(dbRepository, config);
            return populator.fillFoundationTables(shopAddresses, itemTypes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Fills the final, largest table (ShopEntry, 3M+ rows) via the parallel pipeline.
    private void fillShopEntryTable(DataPopulator.PopulationSummary summary) throws InterruptedException {
        long startMillis = System.currentTimeMillis();
        new ProducerConsumerPipeline().run(dbRepository, config, summary.shopCount(), summary.itemCatalogSize());
        log.info("ShopEntry generation+insertion took {} ms", System.currentTimeMillis() - startMillis);
    }

    private void findAndLogTopShop() {
        long startMillis = System.currentTimeMillis();
        String topShop = dbRepository.findShopWithMaxItems(config.itemType());
        log.info("Top Shop: {} (found in {} ms)", topShop, System.currentTimeMillis() - startMillis);
    }

    private static DataSource initDatasource(AppConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.dbUrl());
        ds.setUser(config.dbUser());
        ds.setPassword(config.dbPassword());
        return ds;
    }
}
