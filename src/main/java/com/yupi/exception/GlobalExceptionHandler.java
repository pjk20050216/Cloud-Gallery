package com.yupi.exception;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotSafeException;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 用于捕获全局的异常
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("notLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    // sa-token 权限校验异常捕获，因为 sa-token 有自己的异常捕获，所以得统一一下
    //
    // 注意：参数类型必须和 @ExceptionHandler 里声明的一致。
    // 这里原来错写成 NotLoginException，导致 Spring 找不到参数解析器，
    // 抛 IllegalStateException 被兜成 500「系统错误」，用户看不到"无权限"的提示。
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionException(NotPermissionException e) {
        log.error("notPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }

    /**
     * sa-token 的其他鉴权异常，一并兜住，避免落到 RuntimeException 变成"系统错误"
     */
    @ExceptionHandler(NotRoleException.class)
    public BaseResponse<?> notRoleException(NotRoleException e) {
        log.error("notRoleException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }

    @ExceptionHandler(NotSafeException.class)
    public BaseResponse<?> notSafeException(NotSafeException e) {
        log.error("notSafeException", e);
        return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, e.getMessage());
    }

    @ExceptionHandler(DisableServiceException.class)
    public BaseResponse<?> disableServiceException(DisableServiceException e) {
        log.error("disableServiceException", e);
        return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class) // 只要发生这个类中的异常就会被捕获
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class) // 只要发生这个类中的异常就会被捕获
    public BaseResponse<?> RuntimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
