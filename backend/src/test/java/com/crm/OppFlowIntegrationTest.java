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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试（OPP-TS-01）：商机建档 → 阶段推进/回退留痕 → 成交联动客户生命周期 WON
 * → 漏斗统计 → 丢单原因必填与统计 → 终态不可删除
 * 运行前提：本地 PG 已启动（dev profile）。@Transactional 自动回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OppFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private long customerId;
    private long oppId;

    private String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(bodyOf(result)).get("data").asLong();
    }

    private void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        token = objectMapper.readTree(bodyOf(result)).get("data").get("accessToken").asText();
        Assertions.assertFalse(token.isBlank());
    }

    @Test
    @Order(1)
    @DisplayName("建档 → 推进/回退 → 非法流转拦截 → 成交联动 → 漏斗 → 丢单 → 终态保护")
    void fullOppFlow() throws Exception {
        login();

        // 1. 建客户 + 建商机（CRM-O1）
        customerId = idOf(mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"OPP集成测试客户-" + System.nanoTime() + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn());
        oppId = idOf(mockMvc.perform(post("/api/opps")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + customerId + ",\"name\":\"OPP集成测试商机\",\"amount\":100000,"
                                + "\"currency\":\"CNY\",\"expectedDate\":\"2026-12-31\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn());
        Assertions.assertTrue(oppId > 0);

        // 2. 阶段推进 1→3（CRM-O2）
        mockMvc.perform(post("/api/opps/" + oppId + "/stage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStage\":3,\"reason\":\"需求明确\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 3. 回退 3→2 亦允许（留痕）
        mockMvc.perform(post("/api/opps/" + oppId + "/stage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStage\":2}"))
                .andExpect(jsonPath("$.code").value(0));

        // 4. 非法流转拦截：阶段越界 + 终态阶段必须走 win/lose
        mockMvc.perform(post("/api/opps/" + oppId + "/stage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStage\":9}"))
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(post("/api/opps/" + oppId + "/stage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStage\":6}"))
                .andExpect(jsonPath("$.code").value(40000));

        // 5. 成交（CRM-O4）→ 商机 WON + 客户生命周期联动 WON
        mockMvc.perform(post("/api/opps/" + oppId + "/win")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"年度框架签约\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/customers/" + customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.customer.lifecycleStatus").value("WON"));

        // 6. 终态商机不可编辑/删除（保留统计口径）
        mockMvc.perform(delete("/api/opps/" + oppId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40000));

        // 7. 漏斗（CRM-O3/O5）：stage 6 数量≥1，赢率 100%
        MvcResult funnel = mockMvc.perform(get("/api/opps/funnel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode stage6 = null;
        for (JsonNode n : objectMapper.readTree(bodyOf(funnel)).get("data")) {
            if (n.get("stage").asInt() == 6) {
                stage6 = n;
            }
        }
        Assertions.assertNotNull(stage6);
        Assertions.assertTrue(stage6.get("count").asLong() >= 1, "漏斗 stage6 应含已成交商机");
        Assertions.assertEquals(100, stage6.get("winRate").asInt());

        // 8. 丢单：原因必填（空 → 40000），补原因后 LOST + 原因统计可见
        long opp2 = idOf(mockMvc.perform(post("/api/opps")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + customerId + ",\"name\":\"OPP丢单测试\",\"amount\":5000}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn());
        mockMvc.perform(post("/api/opps/" + opp2 + "/lose")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(post("/api/opps/" + opp2 + "/lose")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PRICE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/opps/loss-stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.reason=='PRICE')].count").isNotEmpty());

        // 9. 商机列表（数据范围 + 回填）可见该客户商机
        mockMvc.perform(get("/api/opps")
                        .header("Authorization", "Bearer " + token)
                        .param("customerId", String.valueOf(customerId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
    }
}
