package com.ikrai.project.service.audit;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.query.AuditQuery;
import com.ikrai.project.vo.AuditLogVO;
import com.ikrai.project.vo.PageVO;

public interface AuditService {

    PageVO<AuditLogVO> list(AuditQuery query, AuthUser operator);

    byte[] export(AuditQuery query, AuthUser operator);
}
