package com.ikrai.project.service.user;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.ChangePasswordDTO;
import com.ikrai.project.dto.LoginDTO;
import com.ikrai.project.dto.ProfileUpdateDTO;
import com.ikrai.project.vo.LoginVO;
import com.ikrai.project.vo.UserVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    UserVO getMe(AuthUser current);

    UserVO updateProfile(AuthUser current, ProfileUpdateDTO dto);

    void updatePassword(AuthUser current, ChangePasswordDTO dto);
}
