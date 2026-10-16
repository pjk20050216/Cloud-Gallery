package com.yupi.common;

import com.yupi.exception.ErrorCode;

public class ResultUtils {

    // 成功的响应
    public static <T>BaseResponse<T> success(T data){
        return new BaseResponse<>(0, data, "OK");

    }

    // 失败响应方式一，返回错误码
    public static BaseResponse<?> error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode);
    }

    // 失败响应方式二，返回错误信息
    public static BaseResponse<?> error(int code, String message){
        return new BaseResponse<>(code, null, message);
    }

    // 失败响应方式三
    public static BaseResponse<?> error(ErrorCode errorCode, String message){
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}
