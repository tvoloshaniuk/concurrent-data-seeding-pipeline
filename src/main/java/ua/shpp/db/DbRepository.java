package ua.shpp.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ItemDto;
import ua.shpp.dto.ItemTypeDto;
import ua.shpp.dto.ShopDto;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DbRepository {
    private static final Logger log = LoggerFactory.getLogger(DbRepository.class);
    private final DataSource dataSource;

    public DbRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void batchInsertShops(List<ShopDto> shops) {
        //todo
    }

    public void runDdl(String sql) {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            log.debug("DDL statement {} execute", sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void batchInsertItemTypes(List<ItemTypeDto> itemTypes) {
    }

    public void batchInsertItems(List<ItemDto> items) {

    }

    //todo Q: чи правильно що в main batchInsertShops та batchInsertItemTypes це окремі методи якщо наповнення в них однакове і можна декомпозувати в один?
    // я не знаю. з одного боку так, для консистентності. бо методи бід кожну наступу окрему таблицю відрізнятимуться.
    //              а з іншого боку - ні. бо дублювання коду, який можна було б декомпозувати в один метод.
}