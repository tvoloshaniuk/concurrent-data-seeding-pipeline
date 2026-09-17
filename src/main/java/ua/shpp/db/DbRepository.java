package ua.shpp.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DbRepository {
    private static final Logger log = LoggerFactory.getLogger(DbRepository.class);
    private final DataSource dataSource;

    public DbRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void runDdl(String filename) {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            log.debug("DDL statement {} execute", filename);
            statement.execute(filename);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}