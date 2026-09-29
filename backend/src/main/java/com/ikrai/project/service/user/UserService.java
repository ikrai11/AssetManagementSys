package com.ikrai.project.service.user;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.ResetPasswordDTO;
import com.ikrai.project.dto.UserSaveDTO;
import com.ikrai.project.query.UserQuery;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.UserVO;

public interface UserService {

    UserVO create(AuthUser operator, UserSaveDTO dto);

    UserVO update(AuthUser operator, Long id, UserSaveDTO dto);

    void resetPassword(AuthUser operator, Long id, ResetPasswordDTO dto);

    PageVO<UserVO> list(UserQuery query);
}
