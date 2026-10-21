package org.example.finflowbackend.auth.security;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.auth.token.JwtTokenProvider;
import org.example.finflowbackend.support.MockMapperConfig;
import org.example.finflowbackend.system.entity.SysRole;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.service.RbacAuthorityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S05 安全过滤链验收：
 * 无凭证 / 无效凭证 → 401；有效凭证但无权限 → 403；有权限 → 放行；公共接口不受影响。
 * 统一响应结构（code/message/data）在 401/403 下同样保持。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MockMapperConfig.class)
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private RbacAuthorityService rbacAuthorityService;

    @MockitoBean
    private SysRoleMapper sysRoleMapper;

    @BeforeEach
    void setUp() {
        // 角色分页返回空页，200 用例不依赖数据
        when(sysRoleMapper.selectPage(any(), any())).thenReturn(new Page<>());
    }

    private String tokenFor(Long userId) {
        org.example.finflowbackend.auth.entity.SysUser user = new org.example.finflowbackend.auth.entity.SysUser();
        user.setId(userId);
        user.setUsername("tester");
        return tokenProvider.issueAccessToken(user);
    }

    @Test
    void missingTokenGets401UnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void invalidTokenGets401UnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void validTokenWithoutPermissionGets403UnifiedResponse() throws Exception {
        when(rbacAuthorityService.loadPermissionCodes(2L)).thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/system/roles").header("Authorization", "Bearer " + tokenFor(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void validTokenWithPermissionPassesThrough() throws Exception {
        when(rbacAuthorityService.loadPermissionCodes(1L)).thenReturn(Set.of("system:role:list"));

        mockMvc.perform(get("/api/v1/system/roles").header("Authorization", "Bearer " + tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void publicEndpointNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    void emptyRolePageReturnsUnifiedPagingShape() throws Exception {
        when(rbacAuthorityService.loadPermissionCodes(1L)).thenReturn(Set.of("system:role:list"));

        mockMvc.perform(get("/api/v1/system/roles").header("Authorization", "Bearer " + tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
