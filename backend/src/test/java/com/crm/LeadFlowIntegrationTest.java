package com.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 集成测试：线索最小闭环（CRM-L）
 * 建档入公共池 → 手机号防重 → 公共池可见 → 领取 → 转客户（事务内建客户）→ CONVERTED 禁删
 * 分配 → 作废分支。
 * @Transactional 回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeadFlowIntegrationTest {

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
        token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private long createLead(String company, String phone) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"contactName\":\"L\","
                                + "\"contactPhone\":\"" + phone + "\",\"source\":\"WEBSITE\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data").asLong();
    }

    @Test
    @Order(1)
    @DisplayName("建档 → 手机号防重 40000 → 领取 → 转客户（客户同步创建）→ CONVERTED 禁删")
    void leadConvertFlow() throws Exception {
        login();
        String suffix = String.valueOf(System.nanoTime());
        String company = "IT-Lead-" + suffix;
        String phone = "139" + suffix.substring(suffix.length() - 8);

        // 建档入公共池（PENDING，owner=null）
        long leadId = createLead(company, phone);
        org.junit.jupiter.api.Assertions.assertTrue(leadId > 0);

        // 同手机号再建档 → 40000
        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"X\",\"contactName\":\"L\",\"contactPhone\":\"" + phone + "\"}"))
                .andExpect(jsonPath("$.code").value(40000));

        // 公共池可见
        mockMvc.perform(get("/api/leads").header("Authorization", "Bearer " + token).param("pool", "public"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").isNumber());

        // 领取 → 状态 CLAIMED、负责人已归属（非公共池）
        mockMvc.perform(post("/api/leads/" + leadId + "/claim").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult leadRes = mockMvc.perform(get("/api/leads/" + leadId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.lead.status").value("CLAIMED"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String ownerName = objectMapper.readTree(leadRes.getResponse().getContentAsString())
                .get("data").get("ownerName").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals("公共池", ownerName, "领取后负责人不应再是公共池");

        // 转客户 → 返回客户 ID，客户可查
        MvcResult convRes = mockMvc.perform(post("/api/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long customerId = objectMapper.readTree(convRes.getResponse().getContentAsString()).get("data").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(customerId > 0);

        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.customer.name").value(company));

        // 线索已 CONVERTED，删除被拒（40000）
        mockMvc.perform(delete("/api/leads/" + leadId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @Order(2)
    @DisplayName("分配（数据范围内成员）→ 作废 → 状态 INVALID")
    void leadAssignAndInvalidateFlow() throws Exception {
        login();
        String suffix = String.valueOf(System.nanoTime());
        long leadId = createLead("IT-Assign-" + suffix, "138" + suffix.substring(suffix.length() - 8));

        // 可分配成员列表（admin 为 ALL 范围，至少含 admin 本人）
        MvcResult listRes = mockMvc.perform(get("/api/leads/assignable").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode users = objectMapper.readTree(listRes.getResponse().getContentAsString()).get("data");
        org.junit.jupiter.api.Assertions.assertFalse(users.isEmpty(), "ALL 范围应有可分配成员");
        long targetUserId = users.get(0).get("id").asLong();

        // 分配 → ASSIGNED
        mockMvc.perform(post("/api/leads/" + leadId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + targetUserId + "}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/leads/" + leadId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.lead.status").value("ASSIGNED"));

        // 作废 → INVALID
        mockMvc.perform(post("/api/leads/" + leadId + "/invalidate").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/leads/" + leadId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.lead.status").value("INVALID"));

        // 未登录访问 → 401
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized());
    }
}
