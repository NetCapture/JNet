package com.jnet.auth;

import com.jnet.core.Request;

/**
 * 认证接口 - 函数式接口
 * 极简设计：仅负责将认证信息应用到请求上
 */
@FunctionalInterface
public interface Auth {
    /**
     * 应用认证信息到请求
     * @param request 原始请求
     * @return 包含认证信息的请求（通常是添加了 Authorization 头）
     */
    Request apply(Request request);
}
