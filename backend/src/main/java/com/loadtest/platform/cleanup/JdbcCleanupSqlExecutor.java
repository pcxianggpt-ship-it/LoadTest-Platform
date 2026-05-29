package com.loadtest.platform.cleanup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class JdbcCleanupSqlExecutor implements CleanupSqlExecutor {

    @Override
    public int execute(BusinessDatabase database, String sql) {
        Properties properties = new Properties();
        if (database.getUsername() != null) {
            properties.put("user", database.getUsername());
        }
        if (database.getPasswordEncrypted() != null) {
            properties.put("password", database.getPasswordEncrypted());
        }
        try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), properties);
                Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }
}
