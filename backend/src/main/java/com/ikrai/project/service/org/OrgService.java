package com.ikrai.project.service.org;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.OrgSaveDTO;
import com.ikrai.project.vo.OrgNodeVO;

import java.util.List;

public interface OrgService {

    List<OrgNodeVO> listDepts(AuthUser operator);

    OrgNodeVO saveDept(AuthUser operator, OrgSaveDTO dto);

    OrgNodeVO updateDept(AuthUser operator, Long id, OrgSaveDTO dto);

    void removeDept(AuthUser operator, Long id);

    List<OrgNodeVO> listLocations(AuthUser operator);

    OrgNodeVO saveLocation(AuthUser operator, OrgSaveDTO dto);

    OrgNodeVO updateLocation(AuthUser operator, Long id, OrgSaveDTO dto);

    void removeLocation(AuthUser operator, Long id);
}
