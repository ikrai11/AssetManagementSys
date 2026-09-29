package com.ikrai.project.service.org.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.StocktakeDao;
import com.ikrai.project.dao.StocktakeItemDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.StocktakeDO;
import com.ikrai.project.dataobject.StocktakeItemDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.OrgSaveDTO;
import com.ikrai.project.service.org.OrgService;
import com.ikrai.project.vo.OrgNodeVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrgServiceImpl implements OrgService {

    private final SysDeptDao sysDeptDao;
    private final SysLocationDao sysLocationDao;
    private final SysUserDao sysUserDao;
    private final AssetDao assetDao;
    private final StocktakeDao stocktakeDao;
    private final StocktakeItemDao stocktakeItemDao;
    private final AuditLogDao auditLogDao;

    public OrgServiceImpl(SysDeptDao sysDeptDao,
                          SysLocationDao sysLocationDao,
                          SysUserDao sysUserDao,
                          AssetDao assetDao,
                          StocktakeDao stocktakeDao,
                          StocktakeItemDao stocktakeItemDao,
                          AuditLogDao auditLogDao) {
        this.sysDeptDao = sysDeptDao;
        this.sysLocationDao = sysLocationDao;
        this.sysUserDao = sysUserDao;
        this.assetDao = assetDao;
        this.stocktakeDao = stocktakeDao;
        this.stocktakeItemDao = stocktakeItemDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    public List<OrgNodeVO> listDepts(AuthUser operator) {
        return list(operator, Kind.DEPT);
    }

    @Override
    @Transactional
    public OrgNodeVO saveDept(AuthUser operator, OrgSaveDTO dto) {
        return save(operator, Kind.DEPT, dto);
    }

    @Override
    @Transactional
    public OrgNodeVO updateDept(AuthUser operator, Long id, OrgSaveDTO dto) {
        return update(operator, Kind.DEPT, id, dto);
    }

    @Override
    @Transactional
    public void removeDept(AuthUser operator, Long id) {
        remove(operator, Kind.DEPT, id);
    }

    @Override
    public List<OrgNodeVO> listLocations(AuthUser operator) {
        return list(operator, Kind.LOCATION);
    }

    @Override
    @Transactional
    public OrgNodeVO saveLocation(AuthUser operator, OrgSaveDTO dto) {
        return save(operator, Kind.LOCATION, dto);
    }

    @Override
    @Transactional
    public OrgNodeVO updateLocation(AuthUser operator, Long id, OrgSaveDTO dto) {
        return update(operator, Kind.LOCATION, id, dto);
    }

    @Override
    @Transactional
    public void removeLocation(AuthUser operator, Long id) {
        remove(operator, Kind.LOCATION, id);
    }

    private List<OrgNodeVO> list(AuthUser operator, Kind kind) {
        requireAdmin(operator);
        List<Node> nodes = load(kind);
        return toVos(kind, nodes);
    }

    private OrgNodeVO save(AuthUser operator, Kind kind, OrgSaveDTO dto) {
        requireAdmin(operator);
        List<Node> nodes = load(kind);
        String name = normalizeName(dto.getName(), kind);
        assertUniqueName(name, null, nodes, kind);
        assertParent(null, dto.getParentId(), nodes, kind);
        boolean enabled = !Boolean.FALSE.equals(dto.getEnabled());
        Long id = insert(kind, name, dto.getParentId(), enabled);
        writeAudit(operator, kind, "CREATE", name, "新增" + kind.label + "「" + name + "」" + parentText(dto.getParentId(), nodes));
        return findVo(kind, id);
    }

    private OrgNodeVO update(AuthUser operator, Kind kind, Long id, OrgSaveDTO dto) {
        requireAdmin(operator);
        List<Node> nodes = load(kind);
        Node current = require(kind, id, nodes);
        String name = normalizeName(dto.getName(), kind);
        assertUniqueName(name, id, nodes, kind);
        assertParent(id, dto.getParentId(), nodes, kind);
        boolean enabled = dto.getEnabled() == null ? enabled(current) : dto.getEnabled();
        String summary = changeSummary(kind, current, name, dto.getParentId(), enabled, nodes);
        updateRow(kind, id, name, dto.getParentId(), enabled);
        if (summary != null) {
            writeAudit(operator, kind, "UPDATE", name, summary);
        }
        return findVo(kind, id);
    }

    private void remove(AuthUser operator, Kind kind, Long id) {
        requireAdmin(operator);
        List<Node> nodes = load(kind);
        Node current = require(kind, id, nodes);
        if (referenced(kind, id)) {
            throw new BusinessException("该" + kind.label + "已被引用，只能停用");
        }
        boolean hasChild = nodes.stream().anyMatch(node -> Objects.equals(node.parentId(), id));
        if (hasChild) {
            throw new BusinessException("该" + kind.label + "下还有下级，不能删除");
        }
        deleteRow(kind, id);
        writeAudit(operator, kind, "DELETE", current.name(), "删除" + kind.label + "「" + current.name() + "」");
    }

    private OrgNodeVO findVo(Kind kind, Long id) {
        return toVos(kind, load(kind)).stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(kind.label + "不存在"));
    }

    private List<OrgNodeVO> toVos(Kind kind, List<Node> nodes) {
        Map<Long, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) {
            byId.put(node.id(), node);
        }
        Set<Long> parents = nodes.stream()
                .map(Node::parentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<OrgNodeVO> list = new ArrayList<>();
        for (Node node : nodes) {
            OrgNodeVO vo = new OrgNodeVO();
            vo.setId(node.id());
            vo.setName(node.name());
            vo.setParentId(node.parentId());
            if (node.parentId() != null) {
                Node parent = byId.get(node.parentId());
                vo.setParentName(parent == null ? null : parent.name());
            }
            vo.setEnabled(enabled(node));
            vo.setReferenced(referenced(kind, node.id()));
            vo.setHasChildren(parents.contains(node.id()));
            list.add(vo);
        }
        return list;
    }

    private String normalizeName(String name, Kind kind) {
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(kind.label + "名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 64) {
            throw new BusinessException(kind.label + "名称不能超过 64 字");
        }
        return trimmed;
    }

    private void assertUniqueName(String name, Long excludeId, List<Node> nodes, Kind kind) {
        boolean duplicated = nodes.stream()
                .anyMatch(node -> !Objects.equals(node.id(), excludeId) && name.equals(node.name()));
        if (duplicated) {
            throw new BusinessException(kind.label + "名称已存在");
        }
    }

    private void assertParent(Long id, Long parentId, List<Node> nodes, Kind kind) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(id, parentId)) {
            throw new BusinessException("不能选择自己作为上级" + kind.label);
        }
        Map<Long, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) {
            byId.put(node.id(), node);
        }
        if (!byId.containsKey(parentId)) {
            throw new BusinessException("上级" + kind.label + "不存在");
        }
        if (id == null) {
            return;
        }
        Long cursor = parentId;
        int guard = nodes.size() + 1;
        while (cursor != null && guard-- > 0) {
            if (Objects.equals(cursor, id)) {
                throw new BusinessException("不能选择下级作为上级" + kind.label);
            }
            Node node = byId.get(cursor);
            cursor = node == null ? null : node.parentId();
        }
    }

    private String changeSummary(Kind kind, Node current, String name, Long parentId, boolean enabled, List<Node> nodes) {
        List<String> parts = new ArrayList<>();
        if (!current.name().equals(name)) {
            parts.add("名称「" + current.name() + "」改为「" + name + "」");
        }
        if (!Objects.equals(current.parentId(), parentId)) {
            parts.add("上级改为" + parentLabel(parentId, nodes));
        }
        if (enabled(current) != enabled) {
            parts.add(enabled ? "启用" : "停用");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "修改" + kind.label + "「" + name + "」：" + String.join("，", parts);
    }

    private String parentText(Long parentId, List<Node> nodes) {
        if (parentId == null) {
            return "";
        }
        return "，上级为" + parentLabel(parentId, nodes);
    }

    private String parentLabel(Long parentId, List<Node> nodes) {
        if (parentId == null) {
            return "无";
        }
        return nodes.stream()
                .filter(node -> Objects.equals(node.id(), parentId))
                .map(node -> "「" + node.name() + "」")
                .findFirst()
                .orElse("未知");
    }

    private boolean referenced(Kind kind, Long id) {
        if (kind == Kind.DEPT) {
            return count(sysUserDao.selectCount(Wrappers.<SysUserDO>lambdaQuery().eq(SysUserDO::getDeptId, id)))
                    || count(assetDao.selectCount(Wrappers.<AssetDO>lambdaQuery().eq(AssetDO::getDeptId, id)))
                    || count(stocktakeDao.selectCount(Wrappers.<StocktakeDO>lambdaQuery().eq(StocktakeDO::getDeptId, id)))
                    || count(stocktakeItemDao.selectCount(Wrappers.<StocktakeItemDO>lambdaQuery().eq(StocktakeItemDO::getDeptId, id)));
        }
        return count(assetDao.selectCount(Wrappers.<AssetDO>lambdaQuery().eq(AssetDO::getLocationId, id)))
                || count(stocktakeDao.selectCount(Wrappers.<StocktakeDO>lambdaQuery().eq(StocktakeDO::getLocationId, id)))
                || count(stocktakeItemDao.selectCount(Wrappers.<StocktakeItemDO>lambdaQuery().eq(StocktakeItemDO::getLocationId, id)));
    }

    private boolean count(Long value) {
        return value != null && value > 0;
    }

    private List<Node> load(Kind kind) {
        if (kind == Kind.DEPT) {
            return sysDeptDao.selectList(Wrappers.<SysDeptDO>lambdaQuery().orderByAsc(SysDeptDO::getId))
                    .stream()
                    .map(row -> new Node(row.getId(), row.getName(), row.getParentId(), row.getEnabled()))
                    .toList();
        }
        return sysLocationDao.selectList(Wrappers.<SysLocationDO>lambdaQuery().orderByAsc(SysLocationDO::getId))
                .stream()
                .map(row -> new Node(row.getId(), row.getName(), row.getParentId(), row.getEnabled()))
                .toList();
    }

    private Long insert(Kind kind, String name, Long parentId, boolean enabled) {
        if (kind == Kind.DEPT) {
            SysDeptDO row = new SysDeptDO();
            row.setName(name);
            row.setParentId(parentId);
            row.setEnabled(enabled ? 1 : 0);
            sysDeptDao.insert(row);
            return row.getId();
        }
        SysLocationDO row = new SysLocationDO();
        row.setName(name);
        row.setParentId(parentId);
        row.setEnabled(enabled ? 1 : 0);
        sysLocationDao.insert(row);
        return row.getId();
    }

    private void updateRow(Kind kind, Long id, String name, Long parentId, boolean enabled) {
        int flag = enabled ? 1 : 0;
        if (kind == Kind.DEPT) {
            sysDeptDao.update(null, Wrappers.<SysDeptDO>lambdaUpdate()
                    .eq(SysDeptDO::getId, id)
                    .set(SysDeptDO::getName, name)
                    .set(SysDeptDO::getParentId, parentId)
                    .set(SysDeptDO::getEnabled, flag));
            return;
        }
        sysLocationDao.update(null, Wrappers.<SysLocationDO>lambdaUpdate()
                .eq(SysLocationDO::getId, id)
                .set(SysLocationDO::getName, name)
                .set(SysLocationDO::getParentId, parentId)
                .set(SysLocationDO::getEnabled, flag));
    }

    private void deleteRow(Kind kind, Long id) {
        if (kind == Kind.DEPT) {
            sysDeptDao.deleteById(id);
            return;
        }
        sysLocationDao.deleteById(id);
    }

    private Node require(Kind kind, Long id, List<Node> nodes) {
        return nodes.stream()
                .filter(node -> Objects.equals(node.id(), id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(kind.label + "不存在"));
    }

    private boolean enabled(Node node) {
        return node.enabled() != null && node.enabled() == 1;
    }

    private void requireAdmin(AuthUser operator) {
        if (operator == null || !operator.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
    }

    private void writeAudit(AuthUser operator, Kind kind, String action, String name, String summary) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule(kind.name());
        log.setAction(action);
        log.setObjectNo(StrUtil.sub(name, 0, 64));
        log.setSummary(StrUtil.sub(summary, 0, 500));
        auditLogDao.insert(log);
    }

    private enum Kind {
        DEPT("部门"),
        LOCATION("地点");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private record Node(Long id, String name, Long parentId, Integer enabled) {
    }
}
