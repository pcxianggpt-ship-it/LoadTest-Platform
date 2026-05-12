package com.loadtest.platform.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SQLiteConfig {

    @Bean
    public ApplicationRunner sqlitePragmaInitializer(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
            jdbcTemplate.execute("PRAGMA journal_mode=WAL");
            jdbcTemplate.execute("PRAGMA busy_timeout=5000");
        };
    }
}
