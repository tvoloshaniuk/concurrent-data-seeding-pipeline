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

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        AppConfig config = AppConfig.load(args);
        DataSource dataSource = initDatasource(config);
        DbRepository dbRepository = new DbRepository(dataSource);

        dbRepository.runDdl(ResourceLoader.readText("schema.sql"));
        try (
                InputStream shopAddresses = ResourceLoader.stream("shops.csv");
                InputStream itemTypes = ResourceLoader.stream("item_types.csv")
        ) {
            DataPopulator populator = new DataPopulator(dbRepository, config);
            populator.fillInTables(shopAddresses, itemTypes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Create indexes after data population, not before -- to improve performance
        dbRepository.runDdl(ResourceLoader.readText("post_load_indexes.sql"));

//        String topShop = dbRepository.findShopWithMaxItems(config.itemType());
//        log.info("Top Shop: {}", topShop);
    }

    private static DataSource initDatasource(AppConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.dbUrl());
        ds.setUser(config.dbUser());
        ds.setPassword(config.dbPassword());
        return ds;
    }
}






















