package com.yupi.controller;


import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class MainController {

    // 1、基础的接口，用于判断项目是否正常
    @GetMapping("/health")
    public BaseResponse<String> health(){
        return ResultUtils.success("OK");
    }

}
