package org.example.finflowbackend.account.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.account.dto.AccountResponse;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户接口。权限编码与 sys_permission 初始化数据一一对应（V17）。
 */
@RestController
@Tag(name = "账户")
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('account:list')")
    public ApiResponse<PageResponse<AccountResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "20") long size,
                                                           @RequestParam(required = false) String ownerType,
                                                           @RequestParam(required = false) String currency) {
        return ApiResponse.ok(accountService.page(page, size, ownerType, currency));
    }
}
