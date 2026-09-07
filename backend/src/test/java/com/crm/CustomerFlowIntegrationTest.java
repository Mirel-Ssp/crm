package com.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试：登录 → 客户 CRUD → 跟进 → 工作台 全链路
 * 运行前提：本地 PG 已启动（dev profile）。
 * @Transactional + MockMvc 同线程：测试数据自动回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    private void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        token = body.get("data").get("accessToken").asText();
        Assertions.assertFalse(token.isBlank());
    }

    @Test
    @Order(1)
    @DisplayName("登录 → 新增客户 → 列表可见 → 编辑 → 删除")
    void fullCustomerFlow() throws Exception {
        login();
        String uniqueName = "集成测试客户-" + System.nanoTime();

        // 新增
        String createBody = "{\"name\":\"" + uniqueName + "\",\"level\":\"VIP\",\"industry\":\"IT\",\"remark\":\"it-test\"}";
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").asLong();

        // 列表按关键词可见
        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", uniqueName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));

        // 详情
        mockMvc.perform(get("/api/customers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.name").value(uniqueName));

        // 编辑
        mockMvc.perform(put("/api/customers/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + uniqueName + "-edit\",\"level\":\"NORMAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 重名校验：再建同名应失败（业务码 40000）
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + uniqueName + "-edit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        // 软删除后详情应 404
        mockMvc.perform(delete("/api/customers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/customers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @Order(2)
    @DisplayName("未登录访问受保护接口 → 401")
    void anonymousBlocked() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("新增跟进待办 → 工作台 KPI 反映")
    void followupAndWorkbench() throws Exception {
        login();
        String uniqueName = "跟进客户-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + uniqueName + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long customerId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").asLong();

        mockMvc.perform(post("/api/followups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relType\":\"CUSTOMER\",\"relId\":" + customerId +
                                ",\"content\":\"电话回访\",\"status\":\"TODO\"," +
                                "\"nextFollowupAt\":\"2026-09-04T10:00:00\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/workbench/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allTodo").isNumber())
                .andExpect(jsonPath("$.data.myCustomers").isNumber());
    }
}
