package ua.shpp.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DbRepository {
    private static final Logger log = LoggerFactory.getLogger(DbRepository.class);

    private final DataSource dataSource;

    public DbRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // suppression: sql is always the content of a trusted, static classpath resource (schema.sql, post_load_indexes.sql)
    //, never external/user input; DDL statements also don't support PreparedStatement parameters.
    @SuppressWarnings({"SqlSourceToSinkFlow"})
    public void runDdl(String sql) {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            log.debug("Executing DDL: {}", sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void batchInsertShops(List<ShopDto> shops) {
        String sql = "INSERT INTO Shop(address) VALUES (?)";
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            for (ShopDto shop : shops) {
                statement.setString(1, shop.address());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void batchInsertItemTypes(List<ItemTypeDto> types) {
        String sql = "INSERT INTO ItemType(name) VALUES (?)";
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            for (ItemTypeDto type : types) {
                statement.setString(1, type.name());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void batchInsertItems(List<ItemDto> items) {
        // ON CONFLICT DO NOTHING is a defensive safety net for Item(name) UNIQUE — names are
        // UUIDs, so a real collision is astronomically unlikely, not the normal path.
        String sql = "INSERT INTO Item(name, type_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            for (ItemDto item : items) {
                statement.setString(1, item.name());
                statement.setInt(2, item.typeId());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String findShopWithMaxItems(String s) {
        return null;
    }
}
