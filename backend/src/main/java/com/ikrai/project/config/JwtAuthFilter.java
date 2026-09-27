package com.ikrai.project.config;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SysUserDO;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SysUserDao sysUserDao;

    public JwtAuthFilter(JwtService jwtService, SysUserDao sysUserDao) {
        this.jwtService = jwtService;
        this.sysUserDao = sysUserDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthUser parsed = jwtService.parse(header.substring(7));
                SysUserDO user = sysUserDao.selectById(parsed.getUserId());
                if (user != null && user.getEnabled() != null && user.getEnabled() == 1
                        && user.getTokenVersion() != null && user.getTokenVersion() == parsed.getTokenVersion()) {
                    AuthUser current = new AuthUser(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            current,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null}");
                    return;
                }
            } catch (JwtException ex) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
