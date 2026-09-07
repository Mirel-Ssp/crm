package com.crm.customer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.Result;
import com.crm.common.exception.BizException;
import com.crm.common.security.CurrentUser;
import com.crm.customer.entity.CrmContact;
import com.crm.customer.mapper.CrmContactMapper;
import com.crm.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户联系人接口（CRM-C1 详情维护）
 */
@Tag(name = "客户联系人 CRM-C")
@RestController
@RequestMapping("/api/customers/{customerId}/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final CrmContactMapper contactMapper;
    private final CustomerService customerService;

    @Operation(summary = "联系人列表")
    @GetMapping
    public Result<List<CrmContact>> list(@CurrentUser Long uid, @PathVariable Long customerId) {
        customerService.requireVisible(uid, customerId);
        return Result.ok(contactMapper.selectList(new LambdaQueryWrapper<CrmContact>()
                .eq(CrmContact::getCustomerId, customerId)
                .orderByDesc(CrmContact::getIsPrimary)
                .orderByAsc(CrmContact::getId)));
    }

    @Operation(summary = "新增联系人")
    @PostMapping
    public Result<Long> create(@CurrentUser Long uid, @PathVariable Long customerId,
                               @Valid @RequestBody ContactRequest req) {
        customerService.requireVisible(uid, customerId);
        // 主联系人唯一：设为主联系人时先清旧
        if (Boolean.TRUE.equals(req.getIsPrimary())) {
            contactMapper.selectList(new LambdaQueryWrapper<CrmContact>()
                            .eq(CrmContact::getCustomerId, customerId)
                            .eq(CrmContact::getIsPrimary, 1))
                    .forEach(old -> {
                        old.setIsPrimary(0);
                        contactMapper.updateById(old);
                    });
        }
        CrmContact c = new CrmContact();
        c.setCustomerId(customerId);
        c.setName(req.getName());
        c.setPosition(req.getPosition());
        c.setPhone(req.getPhone());
        c.setEmail(req.getEmail());
        c.setWechat(req.getWechat());
        c.setIsPrimary(Boolean.TRUE.equals(req.getIsPrimary()) ? 1 : 0);
        c.setRemark(req.getRemark());
        contactMapper.insert(c);
        return Result.ok(c.getId());
    }

    @Operation(summary = "删除联系人（软删除）")
    @DeleteMapping("/{contactId}")
    public Result<Void> delete(@CurrentUser Long uid, @PathVariable Long customerId,
                               @PathVariable Long contactId) {
        customerService.requireVisible(uid, customerId);
        CrmContact c = contactMapper.selectById(contactId);
        if (c == null || !c.getCustomerId().equals(customerId)) {
            throw new BizException(com.crm.common.api.ResultCode.NOT_FOUND, "联系人不存在");
        }
        contactMapper.deleteById(contactId);
        return Result.ok();
    }

    /** 联系人请求体 */
    @Data
    static class ContactRequest {
        @NotBlank(message = "联系人姓名不能为空")
        private String name;
        private String position;
        private String phone;
        private String email;
        private String wechat;
        private Boolean isPrimary;
        private String remark;
    }
}
