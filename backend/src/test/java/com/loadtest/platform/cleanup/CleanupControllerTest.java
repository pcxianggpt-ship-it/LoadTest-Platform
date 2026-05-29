package com.loadtest.platform.cleanup;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CleanupControllerTest {

    private static final Path DB_PATH = Path.of("target", "cleanup-controller-test.db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        Files.deleteIfExists(DB_PATH);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsBusinessDatabases() throws Exception {
        Long projectId = createProject();
        Long mysqlId = createBusinessDatabase(projectId, "mysql", "jdbc:mysql://10.0.0.8:3306/orderdb");
        createBusinessDatabase(projectId, "oracle", "jdbc:oracle:thin:@10.0.0.9:1521/ORCLPDB1");

        mockMvc.perform(get("/api/projects/{projectId}/business-databases", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].databaseType").value("mysql"))
                .andExpect(jsonPath("$.data[1].databaseType").value("oracle"));

        String updateBody = """
                {
                  "name": "order-mysql-updated",
                  "databaseType": "mysql",
                  "jdbcUrl": "jdbc:mysql://10.0.0.8:3306/orderdb",
                  "username": "tester",
                  "passwordEncrypted": "secret2",
                  "status": "inactive"
                }
                """;
        mockMvc.perform(put("/api/projects/{projectId}/business-databases/{databaseId}", projectId, mysqlId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("order-mysql-updated"))
                .andExpect(jsonPath("$.data.status").value("inactive"));
    }

    @Test
    void createsUpdatesAndDeletesCleanupPlans() throws Exception {
        Long projectId = createProject();
        Long databaseId = createBusinessDatabase(projectId, "mysql", "jdbc:mysql://10.0.0.8:3306/orderdb");
        Long planId = createCleanupPlan(projectId, databaseId);

        mockMvc.perform(get("/api/projects/{projectId}/cleanup-plans", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("订单数据清理"))
                .andExpect(jsonPath("$.data[0].sqlStatements.length()").value(2));

        String updateBody = """
                {
                  "name": "订单数据清理-编辑",
                  "description": "更新清理规则",
                  "businessDatabaseId": %d,
                  "enabled": false,
                  "sqlStatements": [
                    "delete from t_order where test_flag = 1"
                  ]
                }
                """.formatted(databaseId);
        mockMvc.perform(put("/api/projects/{projectId}/cleanup-plans/{planId}", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单数据清理-编辑"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.sqlStatements.length()").value(1));

        mockMvc.perform(delete("/api/projects/{projectId}/cleanup-plans/{planId}", projectId, planId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/cleanup-plans", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private Long createProject() throws Exception {
        String body = """
                {
                  "name": "订单系统压测",
                  "environmentName": "test"
                }
                """;
        return extractId(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long createBusinessDatabase(Long projectId, String type, String jdbcUrl) throws Exception {
        String body = """
                {
                  "name": "order-%s",
                  "databaseType": "%s",
                  "jdbcUrl": "%s",
                  "username": "tester",
                  "passwordEncrypted": "secret",
                  "status": "active"
                }
                """.formatted(type, type, jdbcUrl);
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/business-databases", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long createCleanupPlan(Long projectId, Long databaseId) throws Exception {
        String body = """
                {
                  "name": "订单数据清理",
                  "description": "删除压测订单并恢复金额",
                  "businessDatabaseId": %d,
                  "enabled": true,
                  "sqlStatements": [
                    "delete from t_order where test_flag = 1",
                    "update account set amount = 100 where user_id = 1"
                  ]
                }
                """.formatted(databaseId);
        return extractId(mockMvc.perform(post("/api/projects/{projectId}/cleanup-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long extractId(String response) {
        String marker = "\"id\":";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf(",", start);
        return Long.parseLong(response.substring(start, end));
    }
}
