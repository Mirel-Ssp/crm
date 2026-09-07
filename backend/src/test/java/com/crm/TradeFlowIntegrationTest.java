package com.crm;

import com.crm.trade.service.TradeRemitService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 交易全链路集成测试（批次5 质量沉淀：订单 → 审批 → 汇款 → 多对多核销）：
 * 1. 全链路推进：低额自动确认 / 超阈值人工审批、核销原子推进订单与汇款状态
 * 2. 双向防超核：超汇款余额 / 超订单剩余应付 均拒绝（REM-DV-02）
 * 3. 并发核销同一汇款：FOR UPDATE + 余额条件更新（CAS 乐观锁）确保恰好一个成功，
 *    失败方回滚且不残留结算明细（40900）
 * 运行前提：本地 PG 已启动（dev profile）。
 * 注：并发用例需真实提交，不能挂测试事务（@Transactional），数据以唯一前缀隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradeFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRemitService remitService;

    private String token;
    private long uid;

    private void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        token = body.get("data").get("accessToken").asText();
        uid = body.get("data").get("userInfo").get("id").asLong();
        Assertions.assertFalse(token.isBlank());
    }

    // ---------------- 数据准备 helpers（HTTP 真实/测试事务内执行） ----------------

    private long createCustomer(String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"level\":\"VIP\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asLong();
    }

    /** 建标的并上架（上架后才可下单） */
    private long createAndListItem(String code) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/trade/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"集成标的" + code + "\","
                                + "\"category\":\"STOCK\",\"referencePrice\":100,"
                                + "\"feeRate\":0,\"minQuantity\":1}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asLong();
        mockMvc.perform(post("/api/trade/items/" + id + "/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        return id;
    }

    /** 创建订单并返回 {id, status}（低额自动确认，超 100000 阈值进入人工审批） */
    private JsonNode createOrder(long customerId, long itemId, String quantity, String price) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/trade/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + customerId + ",\"itemId\":" + itemId
                                + ",\"direction\":\"BUY\",\"quantity\":" + quantity
                                + ",\"price\":" + price + "}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asLong();
        return orderDetail(id);
    }

    private JsonNode orderDetail(long id) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/trade/orders/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data");
    }

    private long registerAndConfirmRemit(String remitNo, long customerId, String amount) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/trade/remit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remitNo\":\"" + remitNo + "\",\"customerId\":" + customerId
                                + ",\"amount\":" + amount
                                + ",\"remittedAt\":\"2026-09-05T10:00:00\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asLong();
        mockMvc.perform(post("/api/trade/remit/" + id + "/confirm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        return id;
    }

    private JsonNode remitDetail(long id) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/trade/remit/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data");
    }

    /** 发起核销并返回业务码（0=成功） */
    private int writeOff(long remitId, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/trade/remit/" + remitId + "/write-off")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("code").asInt();
    }

    // ---------------- 用例 1：全链路 ----------------

    @Test
    @Order(1)
    @Transactional
    @DisplayName("全链路：低额自动确认 → 超阈值审批 → 汇款确认 → 多对多核销 → 状态原子推进")
    void fullTradeFlow() throws Exception {
        login();
        String tag = "IT5F" + System.nanoTime();
        long customerId = createCustomer("全链路客户-" + tag);
        long itemId = createAndListItem(tag);

        // 订单 A：200 < 100000 阈值 → 自动确认
        JsonNode orderA = createOrder(customerId, itemId, "2", "100");
        long orderAId = orderA.get("id").asLong();
        Assertions.assertEquals("CONFIRMED", orderA.get("status").asText(), "低额订单应自动确认");

        // 订单 C：200000 > 100000 阈值 → 人工审批 → CONFIRMED
        JsonNode orderC = createOrder(customerId, itemId, "2000", "100");
        long orderCId = orderC.get("id").asLong();
        Assertions.assertEquals("PENDING_CONFIRM", orderC.get("status").asText(), "超阈值订单应进入人工审批");
        mockMvc.perform(post("/api/trade/orders/" + orderCId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"审批同意\"}"))
                .andExpect(jsonPath("$.code").value(0));
        Assertions.assertEquals("CONFIRMED", orderDetail(orderCId).get("status").asText());

        // 汇款 500 → 确认到账
        long remitId = registerAndConfirmRemit(tag, customerId, "500");

        // 防超核（订单侧）：核销 300 > 订单 A 应付 200 → 30001
        Assertions.assertEquals(30001, writeOff(remitId, "[{\"orderId\":" + orderAId + ",\"amount\":300}]"),
                "核销超出订单剩余应付应拒绝");

        // 多对多核销：A 全额 200 + C 部分 300
        Assertions.assertEquals(0, writeOff(remitId,
                "[{\"orderId\":" + orderAId + ",\"amount\":200},{\"orderId\":" + orderCId + ",\"amount\":300}]"),
                "合法核销应成功");

        // 订单状态原子推进：A → FULL_DEALT，C → PARTIAL_DEALT
        Assertions.assertEquals("FULL_DEALT", orderDetail(orderAId).get("status").asText());
        Assertions.assertEquals("PARTIAL_DEALT", orderDetail(orderCId).get("status").asText());

        // 汇款核销完：WRITTEN_OFF + 2 条结算明细
        JsonNode remit = remitDetail(remitId);
        Assertions.assertEquals("WRITTEN_OFF", remit.get("status").asText());
        Assertions.assertEquals(0, new BigDecimal("500").compareTo(new BigDecimal(remit.get("writtenOffAmount").asText())));
        Assertions.assertEquals(2, remit.get("settlements").size());

        // 汇款已核销完，再核销 → 状态拒绝（40000）
        Assertions.assertEquals(40000, writeOff(remitId, "[{\"orderId\":" + orderCId + ",\"amount\":100}]"),
                "已核销完的汇款不可再核销");
    }

    // ---------------- 用例 2：双向防超核 ----------------

    @Test
    @Order(2)
    @Transactional
    @DisplayName("防超核：超汇款余额拒绝 → 部分核销推进 → 超订单剩余应付拒绝")
    void overWriteOffRejected() throws Exception {
        login();
        String tag = "IT5O" + System.nanoTime();
        long customerId = createCustomer("防超核客户-" + tag);
        long itemId = createAndListItem(tag);

        long order1Id = createOrder(customerId, itemId, "1", "100").get("id").asLong(); // 应付 100
        long order2Id = createOrder(customerId, itemId, "1", "50").get("id").asLong(); // 应付 50（minQuantity=1，降价不减量）
        long remitId = registerAndConfirmRemit(tag, customerId, "100");

        // 超订单剩余应付：汇款余额充足（80 ≤ 100），但订单 2 应付仅 50 → 30001（订单侧防线）
        Assertions.assertEquals(30001, writeOff(remitId, "[{\"orderId\":" + order2Id + ",\"amount\":80}]"),
                "核销超出订单剩余应付应拒绝");

        // 超汇款余额：预校验 60+50 > 100 → 30001（余额侧防线）
        Assertions.assertEquals(0, writeOff(remitId, "[{\"orderId\":" + order1Id + ",\"amount\":60}]"));
        Assertions.assertEquals(30001, writeOff(remitId, "[{\"orderId\":" + order1Id + ",\"amount\":50}]"),
                "累计核销超出汇款余额应拒绝");

        // 合法核销剩余 40（订单 1：60+40=100 全额）→ 汇款 WRITTEN_OFF
        Assertions.assertEquals(0, writeOff(remitId, "[{\"orderId\":" + order1Id + ",\"amount\":40}]"));
        JsonNode remit = remitDetail(remitId);
        Assertions.assertEquals("WRITTEN_OFF", remit.get("status").asText());
        Assertions.assertEquals("FULL_DEALT", orderDetail(order1Id).get("status").asText());
        Assertions.assertEquals("CONFIRMED", orderDetail(order2Id).get("status").asText(), "被拒绝核销的订单状态不应变化");
    }

    // ---------------- 用例 3：并发核销（真实提交，无测试事务） ----------------

    @Test
    @Order(3)
    @DisplayName("并发核销同一汇款：FOR UPDATE + 条件更新保证恰好一个成功（40900），失败方零残留")
    void concurrentWriteOffConflict() throws Exception {
        login();
        String tag = "IT5C" + System.nanoTime();
        long customerId = createCustomer("并发核销客户-" + tag);
        long itemId = createAndListItem(tag);

        long order1Id = createOrder(customerId, itemId, "1", "100").get("id").asLong();  // 应付 100
        long order2Id = createOrder(customerId, itemId, "1", "100").get("id").asLong();  // 应付 100
        long remitId = registerAndConfirmRemit(tag, customerId, "100");                  // 余额 100

        // 两线程并发各核销 80（不同订单、同一汇款），均通过余额预校验
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> writeOffDirect(remitId, order1Id, start)),
                    pool.submit(() -> writeOffDirect(remitId, order2Id, start)));
            start.countDown(); // 同时放行两个工作线程（缺失会导致双方永远 await，Future 超时）
            List<Integer> codes = futures.stream().map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            // 恰好一个成功一个 40900（数据冲突回滚），杜绝超核
            Assertions.assertTrue(codes.contains(0), "应恰好一个并发核销成功：codes=" + codes);
            Assertions.assertTrue(codes.contains(40900), "另一并发核销应返回 40900 数据冲突：codes=" + codes);
        } finally {
            pool.shutdownNow();
        }

        // 真实库验证：汇款已核 80（失败方零残留）
        JsonNode remit = remitDetail(remitId);
        Assertions.assertEquals("PARTIALLY_WRITTEN_OFF", remit.get("status").asText());
        Assertions.assertEquals(0, new BigDecimal("80").compareTo(new BigDecimal(remit.get("writtenOffAmount").asText())));
        Assertions.assertEquals(1, remit.get("settlements").size(), "失败方结算明细应随事务回滚");

        // 失败方订单未被推进：剩余应付未变（成功方核的是另一张订单）
        long paidOrder1 = new BigDecimal(orderDetail(order1Id).get("paidAmount").asText()).intValue();
        long paidOrder2 = new BigDecimal(orderDetail(order2Id).get("paidAmount").asText()).intValue();
        Assertions.assertEquals(80, Math.max(paidOrder1, paidOrder2), "成功方订单已核 80");
        Assertions.assertEquals(0, Math.min(paidOrder1, paidOrder2), "失败方订单不应有核销残留");
    }

    /** 并发线程直调 service（代理事务生效），返回业务码：0 / 40900 */
    private int writeOffDirect(long remitId, long orderId, CountDownLatch start) {
        try {
            start.await();
            com.crm.trade.dto.WriteOffRequest req = new com.crm.trade.dto.WriteOffRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("80"));
            remitService.writeOff(uid, remitId, List.of(req));
            return 0;
        } catch (com.crm.common.exception.BizException e) {
            return e.getCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
