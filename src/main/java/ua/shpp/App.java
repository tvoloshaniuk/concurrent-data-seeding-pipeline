package ua.shpp;

import net.datafaker.Faker;
import net.datafaker.providers.base.BaseProviders;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        generateItemTypes();
//        databaseJDBCHelloWorld();

//        AppConfig config = AppConfig.load();
//        DataSource dataSource = initDatasource(config);
//        DbRepository dbRepository = new DbRepository(dataSource);
//
//        dbRepository.runDdl(ResourceLoader.readText("schema.sql"));
//        try (
//                InputStream shopAddresses = ResourceLoader.stream("shops.csv");
//                InputStream itemTypes = ResourceLoader.stream("item_types.csv")
//        ) {
//            DataPopulator populator = new DataPopulator(dbRepository, config);
//            populator.fillInTables(shopAddresses, itemTypes);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        String topShop = dbRepository.findShopWithMaxItems(config.itemType());
//        log.info("Top Shop: {}", topShop);
    }

    private static void generateItemTypes() {
        Faker uaFaker = new Faker(new Locale("uk"));
        String department = uaFaker.commerce().department();
        String address = uaFaker.address().fullAddress();
        for (int i = 1; i <= 5000; i++) {
            // Згенерує: "Electronics-1", "Tools-2" // <--------------------
            log.info(uaFaker.commerce().department() + "-" + i);
        }
    }

    private static DataSource initDatasource(AppConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(config.dbUrl());
        ds.setUser(config.dbUser());
        ds.setPassword(config.dbPassword());
        return ds;
    }
}






















