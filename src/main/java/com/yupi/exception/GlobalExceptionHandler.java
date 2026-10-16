package com.yupi.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 用于捕获全局的异常
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e){
        log.error("notLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    // sa-token 权限校验发送异常捕获，因为 sa-token 有自己的异常捕获，所以得统一一下
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionException(NotLoginException e){
        log.error("notPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class) // 只要发生这个类中的异常就会被捕获
    public BaseResponse<?> businessExceptionHandler(BusinessException e){
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class) // 只要发生这个类中的异常就会被捕获
    public BaseResponse<?> RuntimeExceptionHandler(RuntimeException e){
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
