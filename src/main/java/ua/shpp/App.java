package ua.shpp;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class App {
    public static void main(String[] args) {
//        databaseJDBCHelloWorld();

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






















