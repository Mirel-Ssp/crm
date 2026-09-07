package com.crm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.system.dto.DictSaveRequest;
import com.crm.system.entity.SysDict;
import com.crm.system.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 数据字典（SYS-DV-05）：业务下拉枚举统一管理
 * 读接口登录即可（业务下拉共用）；写接口需 system:dict
 */
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictMapper dictMapper;

    /** 按类型取字典项（sort 升序） */
    public List<Map<String, Object>> listByType(String type) {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                        .eq(StringUtils.hasText(type), SysDict::getDictType, type)
                        .orderByAsc(SysDict::getSort))
                .stream().map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "dictType", d.getDictType(),
                        "code", d.getCode(),
                        "value", d.getValue(),
                        "sort", d.getSort() == null ? 0 : d.getSort()))
                .toList();
    }

    @Transactional
    public Long create(DictSaveRequest req) {
        checkDuplicate(req.getDictType(), req.getCode(), null);
        SysDict dict = new SysDict();
        dict.setDictType(req.getDictType());
        dict.setCode(req.getCode());
        dict.setValue(req.getValue());
        dict.setSort(req.getSort());
        dictMapper.insert(dict);
        return dict.getId();
    }

    @Transactional
    public void update(Long id, DictSaveRequest req) {
        SysDict dict = requireDict(id);
        checkDuplicate(req.getDictType(), req.getCode(), id);
        dict.setDictType(req.getDictType());
        dict.setCode(req.getCode());
        dict.setValue(req.getValue());
        dict.setSort(req.getSort());
        dictMapper.updateById(dict);
    }

    @Transactional
    public void delete(Long id) {
        requireDict(id);
        dictMapper.deleteById(id);
    }

    private void checkDuplicate(String type, String code, Long excludeId) {
        Long dup = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getCode, code)
                .ne(excludeId != null, SysDict::getId, excludeId));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.DICT_DUPLICATED);
        }
    }

    private SysDict requireDict(Long id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BizException(ResultCode.DICT_NOT_FOUND);
        }
        return dict;
    }
}
