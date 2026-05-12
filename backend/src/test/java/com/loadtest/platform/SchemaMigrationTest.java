package com.loadtest.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class SchemaMigrationTest {

    private static final Path DB_PATH = Path.of("target", "schema-migration-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesMvpTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select name from sqlite_master where type = 'table'",
                String.class
        );

        assertThat(tables).contains(
                "projects",
                "jmeter_servers",
                "project_datasources",
                "test_tasks",
                "test_task_steps",
                "test_executions",
                "test_execution_steps",
                "test_results",
                "test_result_metrics",
                "test_reports"
        );
    }
}
