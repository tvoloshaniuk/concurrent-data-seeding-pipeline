package ua.shpp.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;
import ua.shpp.dto.ShopEntryDto;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DbRepository {
    private static final Logger log = LoggerFactory.getLogger(DbRepository.class);

    private final DataSource dataSource;

    public DbRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** todo
     * Suppression: sql is always the content of a trusted, static classpath resource
     * (schema.sql, post_load_indexes.sql), never external/user input; DDL statements also
     * don't support PreparedStatement parameters.
     */
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

    public int batchInsertShopEntries(List<ShopEntryDto> entries) {
        /* todo
          (item_id, shop_id) names the exact UNIQUE constraint from schema.sql - if that
          constraint is ever renamed, Postgres fails loudly here instead of silently
          targeting the wrong index.
         */
        String sql = "INSERT INTO ShopEntry(item_id, shop_id, item_count) VALUES (?, ?, ?) "
                + "ON CONFLICT (item_id, shop_id) DO NOTHING";
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            for (ShopEntryDto entry : entries) {
                statement.setInt(1, entry.itemId());
                statement.setInt(2, entry.shopId());
                statement.setInt(3, entry.itemCount());
                statement.addBatch();
            }
            return countInserted(statement.executeBatch());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Counts rows actually inserted (excludes duplicates ON CONFLICT skipped).
     */
    int countInserted(int[] results) {
        int inserted = 0;
        for (int result : results) {
            if (result == Statement.SUCCESS_NO_INFO || result == 1) {
                inserted++;
            }
        }
        return inserted;
    }

    // Check if ShopEntry table contains any data
    public boolean hasShopEntries() {
        String sql = "SELECT 1 FROM ShopEntry LIMIT 1";
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()
        ) {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean existsItemType(String name) {
        String sql = "SELECT 1 FROM ItemType WHERE name = ? LIMIT 1";
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Separate from existsItemType because the two failures need different advice: an unknown name is
     * a typo, while a known name with no items means the type list outgrew the item catalogue and the
     * last types were left empty.
     */
    public boolean existsItemTypeWithItems(String name) {
        String sql = """
                SELECT 1
                FROM ItemType it
                JOIN Item i ON i.type_id = it.id
                WHERE it.name = ?
                LIMIT 1
                """;
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String findShopWithMaxItems(String itemType) {
        String sql = """
                SELECT s.address
                FROM (
                    SELECT se.shop_id, SUM(se.item_count) AS total
                    FROM ShopEntry se
                    JOIN Item i ON i.id = se.item_id
                    JOIN ItemType it ON it.id = i.type_id
                    WHERE it.name = ?
                    GROUP BY se.shop_id
                    ORDER BY total DESC
                    LIMIT 1
                ) top
                JOIN Shop s ON s.id = top.shop_id
                """;
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, itemType);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("address") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
