package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.api.RbacAuthorityApi;

/**
 * RBAC 授权查询（S36）：跨模块消费方只经 {@link RbacAuthorityApi} 调用（铁律 4），
 * 模块内部服务接口仅用于声明实现。
 */
public interface RbacAuthorityService extends RbacAuthorityApi {
}
