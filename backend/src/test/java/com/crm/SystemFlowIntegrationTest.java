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
 * 集成测试：系统管理全链路（SYS-DV-01/05）
 * 组织树（含删除约束）→ 成员（重名/保护/角色分配/密码重置）→ 角色（权限点分配）→ 字典（防重）
 * 无权限用户访问系统管理 → 40300
 * @Transactional 回滚，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("组织（删除约束）→ 成员（重名/保护/分配角色/重置密码）→ 角色（权限点）→ 字典（防重）")
    void systemFullFlow() throws Exception {
        token = login("admin", "admin123");
        String suffix = String.valueOf(System.nanoTime());

        // ---- 组织：建父 → 建子 → 删父应被拒（60002）→ 删子 → 删父成功 ----
        MvcResult orgRes = mockMvc.perform(post("/api/system/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"name\":\"IT-" + suffix + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long orgId = objectMapper.readTree(orgRes.getResponse().getContentAsString()).get("data").asLong();

        MvcResult childRes = mockMvc.perform(post("/api/system/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + orgId + ",\"name\":\"RnD-" + suffix + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long childOrgId = objectMapper.readTree(childRes.getResponse().getContentAsString()).get("data").asLong();

        mockMvc.perform(delete("/api/system/orgs/" + orgId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(60002));
        mockMvc.perform(delete("/api/system/orgs/" + childOrgId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/system/orgs/" + orgId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        // ---- 成员：创建（带角色）→ 重名 40900 → 重置密码 → 内置管理员禁删 60202 ----
        String username = "it_user_" + suffix;
        MvcResult userRes = mockMvc.perform(post("/api/system/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgId\":1,\"username\":\"" + username + "\",\"password\":\"Sales@2026\","
                                + "\"realName\":\"IT\",\"roleIds\":[3]}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long userId = objectMapper.readTree(userRes.getResponse().getContentAsString()).get("data").asLong();
        Assertions.assertTrue(userId > 0);

        mockMvc.perform(post("/api/system/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgId\":1,\"username\":\"" + username + "\",\"password\":\"Sales@2026\","
                                + "\"realName\":\"IT2\"}"))
                .andExpect(jsonPath("$.code").value(40900));

        mockMvc.perform(put("/api/system/users/" + userId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass@2026\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/system/users/1").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(60202));

        // ---- 角色：创建 → 分配权限点（101/102）→ 校验回读 → 删除 ----
        MvcResult roleRes = mockMvc.perform(post("/api/system/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"IT_ROLE_" + suffix + "\",\"name\":\"IT\",\"dataScope\":\"SELF\",\"remark\":\"\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long roleId = objectMapper.readTree(roleRes.getResponse().getContentAsString()).get("data").asLong();

        mockMvc.perform(put("/api/system/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[101,102]}"))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult roleList = mockMvc.perform(get("/api/system/roles").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode roles = objectMapper.readTree(roleList.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode r : roles) {
            if (r.get("id").asLong() == roleId) {
                Assertions.assertEquals(2, r.get("permissionIds").size(), "角色应含 2 个权限点");
                found = true;
            }
        }
        Assertions.assertTrue(found);

        mockMvc.perform(delete("/api/system/roles/" + roleId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        // ---- 字典：创建 → 同类型同编码防重 60402 → 更新 → 删除 ----
        MvcResult dictRes = mockMvc.perform(post("/api/dicts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"it_dict\",\"code\":\"K" + suffix + "\",\"value\":\"opt\",\"sort\":1}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long dictId = objectMapper.readTree(dictRes.getResponse().getContentAsString()).get("data").asLong();

        mockMvc.perform(post("/api/dicts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"it_dict\",\"code\":\"K" + suffix + "\",\"value\":\"opt2\",\"sort\":2}"))
                .andExpect(jsonPath("$.code").value(60402));

        mockMvc.perform(put("/api/dicts/" + dictId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"it_dict\",\"code\":\"K" + suffix + "\",\"value\":\"opt-upd\",\"sort\":2}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/dicts/" + dictId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(2)
    @DisplayName("SALES 角色用户访问系统管理接口 → 40300（方法级越权）")
    void salesCannotAccessSystem() throws Exception {
        token = login("admin", "admin123");
        String suffix = String.valueOf(System.nanoTime());

        // 建一个仅 SALES 角色（id=3，无 system:* 权限点）的成员
        mockMvc.perform(post("/api/system/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgId\":1,\"username\":\"it_sales_" + suffix + "\",\"password\":\"Sales@2026\","
                                + "\"realName\":\"S\",\"roleIds\":[3]}"))
                .andExpect(jsonPath("$.code").value(0));

        String salesToken = login("it_sales_" + suffix, "Sales@2026");
        mockMvc.perform(get("/api/system/users").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
