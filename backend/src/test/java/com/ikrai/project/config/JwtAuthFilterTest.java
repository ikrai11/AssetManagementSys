package com.ikrai.project.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dto.LoginDTO;
import com.ikrai.project.service.user.AuthService;
import com.ikrai.project.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private SysUserDao sysUserDao;

    @Test
    void disabledUserOldTokenReturns401() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("user");
        dto.setPassword("User@123");
        LoginVO login = authService.login(dto);

        SysUserDO user = sysUserDao.selectOne(Wrappers.<SysUserDO>lambdaQuery().eq(SysUserDO::getUsername, "user"));
        user.setEnabled(0);
        sysUserDao.updateById(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login.getToken()))
                .andExpect(status().isUnauthorized());
    }
}
