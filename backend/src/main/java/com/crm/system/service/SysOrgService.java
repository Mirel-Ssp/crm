package com.crm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.system.dto.OrgSaveRequest;
import com.crm.system.entity.SysOrg;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysOrgMapper;
import com.crm.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 组织管理（SYS-DV-01）
 * - 树形查询 / 增改删
 * - 删除约束：存在未删子节点或成员 → 60002
 * - parent 变更防环：新父不得是自身或其子树节点（否则递归 CTE 死循环）
 * - 结构变更影响全员可见集合，evict 全部用户态（org 量级小，正确性优先）
 */
@Service
@RequiredArgsConstructor
public class SysOrgService {

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final UserStateService userStateService;

    /** 全量组织树（dev 量级直接内存组树） */
    public List<Map<String, Object>> tree() {
        List<SysOrg> all = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .orderByAsc(SysOrg::getSort));
        return buildTree(all, 0L);
    }

    private List<Map<String, Object>> buildTree(List<SysOrg> all, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SysOrg o : all) {
            if (parentId.equals(o.getParentId())) {
                nodes.add(Map.of(
                        "id", o.getId(),
                        "parentId", o.getParentId(),
                        "name", o.getName(),
                        "leaderId", o.getLeaderId() == null ? 0 : o.getLeaderId(),
                        "sort", o.getSort() == null ? 0 : o.getSort(),
                        "children", buildTree(all, o.getId())));
            }
        }
        return nodes;
    }

    @Transactional
    public Long create(OrgSaveRequest req) {
        if (req.getParentId() != null && req.getParentId() != 0
                && orgMapper.selectById(req.getParentId()) == null) {
            throw new BizException(ResultCode.ORG_NOT_FOUND);
        }
        SysOrg org = new SysOrg();
        org.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        org.setName(req.getName());
        org.setLeaderId(req.getLeaderId());
        org.setSort(req.getSort());
        orgMapper.insert(org);
        return org.getId();
    }

    @Transactional
    public void update(Long id, OrgSaveRequest req) {
        SysOrg org = requireOrg(id);
        if (StringUtils.hasText(req.getName())) {
            org.setName(req.getName());
        }
        org.setLeaderId(req.getLeaderId());
        if (req.getSort() != null) {
            org.setSort(req.getSort());
        }
        // parent 变更：防环校验（新父不能是自身或自身子树内的节点）
        if (req.getParentId() != null && !req.getParentId().equals(org.getParentId())) {
            if (req.getParentId().equals(id)) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "上级组织不能是自身");
            }
            List<Long> subtree = orgMapper.selectSubtreeOrgIds(id);
            if (subtree.contains(req.getParentId())) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "上级组织不能是自身的下级节点");
            }
            org.setParentId(req.getParentId());
        }
        orgMapper.updateById(org);
        evictAll();
    }

    @Transactional
    public void delete(Long id) {
        requireOrg(id);
        Long children = orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ResultCode.ORG_HAS_CHILDREN_OR_MEMBERS);
        }
        Long members = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOrgId, id));
        if (members != null && members > 0) {
            throw new BizException(ResultCode.ORG_HAS_CHILDREN_OR_MEMBERS);
        }
        orgMapper.deleteById(id);
        evictAll();
    }

    private SysOrg requireOrg(Long id) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw new BizException(ResultCode.ORG_NOT_FOUND);
        }
        return org;
    }

    /** 组织结构变更影响全员可见集合：清空全部用户态缓存 */
    private void evictAll() {
        userMapper.selectList(new LambdaQueryWrapper<SysUser>().select(SysUser::getId))
                .forEach(u -> userStateService.evict(u.getId()));
    }
}
