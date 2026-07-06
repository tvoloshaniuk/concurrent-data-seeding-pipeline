package ua.shpp;

import org.postgresql.ds.PGSimpleDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class App {
    public static void main(String[] args) {
//        databaseJDBCHelloWorld();
        createTablesFromScript("schema.sql");

        AppConfig config = AppConfig.load();
        InputStream shopAddressesData = ResourceLoader.stream("shop_addresses.csv");
        InputStream itemTypesData = ResourceLoader.stream("item_types.csv");
        fillInTablesWithGeneration(config, shopAddressesData, itemTypesData);
        findShopAdressWithTheBiggestCountOfItemsOfType(config.itemType());
    }

    private static void findShopAdressWithTheBiggestCountOfItemsOfType(String s) {
    }

    private static void fillInTablesWithGeneration(AppConfig config, InputStream shopAddressesData,
                                                   InputStream itemTypesData) {

    }

    private static void createTablesFromScript(String filename) {
        String initTablesScript = ResourceLoader.loadText(filename);

    }

    private static void databaseJDBCHelloWorld() {
        final String url =
                "jdbc:postgresql://localhost:5432/bird_encyclopedia?user=postgres&password=123";
        final PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);

        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM birds";
            try (PreparedStatement prepSt = conn.prepareStatement(sql)) {
                ResultSet rs = prepSt.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    System.out.printf("id: %d, name: %s, description: %s%n", id, name, description);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}






















