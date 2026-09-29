package com.ikrai.project.service.org;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.AssetTransferDTO;
import com.ikrai.project.dto.OrgSaveDTO;
import com.ikrai.project.dto.UserSaveDTO;
import com.ikrai.project.service.asset.AssetLifecycleService;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.service.dict.DictService;
import com.ikrai.project.service.user.UserService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.OrgNodeVO;
import com.ikrai.project.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrgServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private OrgService orgService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetLifecycleService lifecycleService;
    @Autowired
    private UserService userService;
    @Autowired
    private DictService dictService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private SysDeptDao sysDeptDao;
    @Autowired
    private SysLocationDao sysLocationDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;
    private AuthUser user;
    private Long categoryId;
    private Long locationA;
    private Long locationB;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        var locations = sysLocationDao.selectList(Wrappers.<SysLocationDO>lambdaQuery().eq(SysLocationDO::getEnabled, 1));
        locationA = locations.get(0).getId();
        locationB = locations.get(1).getId();
        admin = asUser(insertUser("org_admin", UserRole.ADMIN));
        user = asUser(insertUser("org_user", UserRole.USER));
    }

    @Test
    void childKeepsParentAndCanReturnToRoot() {
        OrgNodeVO parent = orgService.saveDept(admin, dept("信息处" + next(), null, true));
        OrgNodeVO child = orgService.saveDept(admin, dept("课题组" + next(), parent.getId(), true));
        OrgNodeVO listed = findDept(child.getId());
        assertEquals(parent.getId(), listed.getParentId());
        assertEquals(parent.getName(), listed.getParentName());
        assertTrue(findDept(parent.getId()).getHasChildren());

        OrgNodeVO root = orgService.updateDept(admin, child.getId(), dept(child.getName(), null, true));
        assertNull(root.getParentId());
        assertFalse(findDept(parent.getId()).getHasChildren());

        Long audits = auditLogDao.selectCount(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getModule, "DEPT")
                .eq(AuditLogDO::getAction, "CREATE")
                .eq(AuditLogDO::getObjectNo, parent.getName()));
        assertEquals(1L, audits);
    }

    @Test
    void rejectsBlankDuplicateSelfAndCycle() {
        BusinessException blank = assertThrows(BusinessException.class,
                () -> orgService.saveDept(admin, dept("  ", null, true)));
        assertTrue(blank.getMessage().contains("名称不能为空"));

        OrgNodeVO parent = orgService.saveDept(admin, dept("处室" + next(), null, true));
        BusinessException duplicated = assertThrows(BusinessException.class,
                () -> orgService.saveDept(admin, dept(parent.getName(), null, true)));
        assertTrue(duplicated.getMessage().contains("名称已存在"));

        BusinessException missing = assertThrows(BusinessException.class,
                () -> orgService.saveDept(admin, dept("处室" + next(), 9_999_999L, true)));
        assertTrue(missing.getMessage().contains("上级部门不存在"));

        OrgSaveDTO self = dept(parent.getName(), parent.getId(), true);
        BusinessException selfParent = assertThrows(BusinessException.class,
                () -> orgService.updateDept(admin, parent.getId(), self));
        assertTrue(selfParent.getMessage().contains("不能选择自己"));

        OrgNodeVO child = orgService.saveDept(admin, dept("下级" + next(), parent.getId(), true));
        BusinessException cycle = assertThrows(BusinessException.class,
                () -> orgService.updateDept(admin, parent.getId(), dept(parent.getName(), child.getId(), true)));
        assertTrue(cycle.getMessage().contains("不能选择下级"));
        assertNull(findDept(parent.getId()).getParentId());
    }

    @Test
    void referencedDeptCanOnlyBeDisabledAndHistoryKeepsName() {
        OrgNodeVO dept = orgService.saveDept(admin, dept("被引用处" + next(), null, true));
        AssetVO asset = assetService.save(asset("ORG-" + next(), dept.getId(), locationA));
        SysUserDO member = insertUser("org_member", UserRole.USER);
        member.setDeptId(dept.getId());
        sysUserDao.updateById(member);

        BusinessException removed = assertThrows(BusinessException.class, () -> orgService.removeDept(admin, dept.getId()));
        assertTrue(removed.getMessage().contains("只能停用"));
        assertEquals(dept.getName(), assetService.get(asset.getId(), admin).getDeptName());

        OrgNodeVO disabled = orgService.updateDept(admin, dept.getId(), dept(dept.getName(), null, false));
        assertFalse(disabled.getEnabled());
        assertFalse(dictService.listDepts().stream().anyMatch(item -> dept.getId().equals(item.getId())));

        AssetDetailVO kept = assetService.get(asset.getId(), admin);
        assertEquals(dept.getName(), kept.getDeptName());
        AssetVO updated = assetService.update(asset.getId(), asset(asset.getAssetNo(), dept.getId(), locationA));
        assertEquals(dept.getId(), updated.getDeptId());
        assertEquals(dept.getName(), updated.getDeptName());

        BusinessException chosen = assertThrows(BusinessException.class,
                () -> assetService.save(asset("ORG-" + next(), dept.getId(), locationA)));
        assertTrue(chosen.getMessage().contains("已停用"));

        BusinessException newUser = assertThrows(BusinessException.class, () -> userService.create(admin, userDto(dept.getId())));
        assertTrue(newUser.getMessage().contains("已停用"));

        UserVO renamed = userService.update(admin, member.getId(), profile(member, dept.getId(), "改名后"));
        assertEquals(dept.getId(), renamed.getDeptId());
        assertEquals(dept.getName(), renamed.getDeptName());

        AssetDO row = assetDao.selectById(asset.getId());
        assertEquals(dept.getId(), row.getDeptId());
    }

    @Test
    void unusedDeptDeletesAndParentWithChildDoesNot() {
        OrgNodeVO alone = orgService.saveDept(admin, dept("临时组" + next(), null, true));
        orgService.removeDept(admin, alone.getId());
        assertTrue(orgService.listDepts(admin).stream().noneMatch(item -> alone.getId().equals(item.getId())));

        OrgNodeVO parent = orgService.saveDept(admin, dept("上级处" + next(), null, true));
        orgService.saveDept(admin, dept("下级组" + next(), parent.getId(), true));
        BusinessException blocked = assertThrows(BusinessException.class, () -> orgService.removeDept(admin, parent.getId()));
        assertTrue(blocked.getMessage().contains("还有下级"));
        assertEquals(parent.getName(), findDept(parent.getId()).getName());
    }

    @Test
    void disabledLocationStaysOnAssetAndCannotBeChosenAgain() {
        OrgNodeVO floor = orgService.saveLocation(admin, place("楼层" + next(), null, true));
        OrgNodeVO nested = orgService.saveLocation(admin, place("工位" + next(), floor.getId(), true));
        assertEquals(floor.getName(), findLocation(nested.getId()).getParentName());

        OrgNodeVO room = orgService.saveLocation(admin, place("房间" + next(), null, true));
        AssetVO asset = assetService.save(asset("ORG-LOC-" + next(), null, room.getId()));
        BusinessException removed = assertThrows(BusinessException.class, () -> orgService.removeLocation(admin, room.getId()));
        assertTrue(removed.getMessage().contains("只能停用"));

        orgService.updateLocation(admin, room.getId(), place(room.getName(), null, false));
        assertEquals(room.getName(), assetService.get(asset.getId(), admin).getLocationName());
        assertFalse(dictService.listLocations().stream().anyMatch(item -> room.getId().equals(item.getId())));
        BusinessException chosen = assertThrows(BusinessException.class,
                () -> assetService.save(asset("ORG-LOC-" + next(), null, room.getId())));
        assertTrue(chosen.getMessage().contains("地点已停用"));

        OrgNodeVO targetDept = orgService.saveDept(admin, dept("迁入处" + next(), null, true));
        lifecycleService.transfer(admin, asset.getId(), transfer(targetDept.getId(), room.getId(), "只改部门"));
        AssetDO keptRoom = assetDao.selectById(asset.getId());
        assertEquals(targetDept.getId(), keptRoom.getDeptId());
        assertEquals(room.getId(), keptRoom.getLocationId());

        OrgNodeVO heldDept = orgService.saveDept(admin, dept("保留部门" + next(), null, true));
        AssetVO held = assetService.save(asset("ORG-HOLD-" + next(), heldDept.getId(), locationA));
        orgService.updateDept(admin, heldDept.getId(), dept(heldDept.getName(), null, false));
        lifecycleService.transfer(admin, held.getId(), transfer(heldDept.getId(), locationB, "只改地点"));
        AssetDO keptDept = assetDao.selectById(held.getId());
        assertEquals(heldDept.getId(), keptDept.getDeptId());
        assertEquals(locationB, keptDept.getLocationId());

        OrgNodeVO closed = orgService.saveDept(admin, dept("停用处" + next(), null, false));
        BusinessException transferOff = assertThrows(BusinessException.class,
                () -> lifecycleService.transfer(admin, held.getId(), transfer(closed.getId(), locationA, "改到停用部门")));
        assertTrue(transferOff.getMessage().contains("停用"));
        assertEquals(heldDept.getId(), assetDao.selectById(held.getId()).getDeptId());
    }

    @Test
    void userCannotMaintainOrg() {
        BusinessException denied = assertThrows(BusinessException.class, () -> orgService.listDepts(user));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatus());
        assertTrue(denied.getMessage().contains("无权限"));
    }

    private OrgNodeVO findDept(Long id) {
        return orgService.listDepts(admin).stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow();
    }

    private OrgNodeVO findLocation(Long id) {
        return orgService.listLocations(admin).stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow();
    }

    private OrgSaveDTO dept(String name, Long parentId, boolean enabled) {
        return node(name, parentId, enabled);
    }

    private OrgSaveDTO place(String name, Long parentId, boolean enabled) {
        return node(name, parentId, enabled);
    }

    private OrgSaveDTO node(String name, Long parentId, boolean enabled) {
        OrgSaveDTO dto = new OrgSaveDTO();
        dto.setName(name);
        dto.setParentId(parentId);
        dto.setEnabled(enabled);
        return dto;
    }

    private AssetSaveDTO asset(String assetNo, Long deptId, Long locationId) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo);
        dto.setName("组织设备");
        dto.setCategoryId(categoryId);
        dto.setDeptId(deptId);
        dto.setLocationId(locationId);
        return dto;
    }

    private AssetTransferDTO transfer(Long deptId, Long locationId, String reason) {
        AssetTransferDTO dto = new AssetTransferDTO();
        dto.setDeptId(deptId);
        dto.setLocationId(locationId);
        dto.setReason(reason);
        return dto;
    }

    private UserSaveDTO userDto(Long deptId) {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername("orgnew" + next());
        dto.setRealName("新同事");
        dto.setEmail("orgnew@example.com");
        dto.setRole(UserRole.USER.name());
        dto.setPassword("Passw0rd");
        dto.setDeptId(deptId);
        dto.setEnabled(true);
        return dto;
    }

    private UserSaveDTO profile(SysUserDO member, Long deptId, String realName) {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setRealName(realName);
        dto.setEmail(member.getEmail());
        dto.setRole(member.getRole());
        dto.setDeptId(deptId);
        dto.setEnabled(true);
        return dto;
    }

    private SysUserDO insertUser(String username, UserRole role) {
        SysUserDO row = new SysUserDO();
        row.setUsername(username + next());
        row.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        row.setRealName(username);
        row.setEmail(username + "@example.com");
        row.setRole(role.name());
        row.setEnabled(1);
        row.setTokenVersion(0);
        row.setMustChangePassword(0);
        sysUserDao.insert(row);
        return row;
    }

    private AuthUser asUser(SysUserDO row) {
        return new AuthUser(row.getId(), row.getUsername(), row.getRole(), row.getTokenVersion());
    }

    private int next() {
        return SEQ.getAndIncrement();
    }
}
