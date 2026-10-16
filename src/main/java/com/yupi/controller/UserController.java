package com.yupi.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.annotation.AutoCheck;
import com.yupi.common.BaseResponse;
import com.yupi.common.DeleteRequest;
import com.yupi.common.ResultUtils;
import com.yupi.constant.UserConstant;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.dto.user.*;
import com.yupi.model.entity.User;
import com.yupi.model.vo.LoginUserVO;
import com.yupi.model.vo.UserVO;
import com.yupi.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {


    @Resource
    private UserService userService;


    // 1、用户注册接口
    @PostMapping("/register")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> UserRegister(@RequestBody UseRegisterRequest user) {
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR);

        // 借助插件，直接用：user.allget 就可以直接获取全部
        String userAccount = user.getUserAccount();
        String userPassword = user.getUserPassword();
        String checkPassword = user.getCheckPassword();
        long result = userService.UserRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    // 用户登录，相当于记录一下缓存
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> UserLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        LoginUserVO userVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(userVO);
    }

    // 获取当前登录用户，就是获取缓存
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getUserLoginVO(loginUser));
    }


    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean loginOut = userService.loginOut(request);
        return ResultUtils.success(loginOut);
    }

    // 创建用户
    @PostMapping("/add")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> AddUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        final String defaultPassword = "12345678";
        String password = userService.PasswordEncryption(defaultPassword);
        user.setUserPassword(password);
        boolean save = userService.save(user);
        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }


    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    // @AutoCheck 注解，AOP 框架会自动为这个方法创建一个代理对象。当外部请求调用这个方法时，实际上调用的是代理对象
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));

        // 这个相当于一个空壳，还没有具体的数据
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());

        // 对原始的数据列表进行脱敏，然后再赋给下面
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());

        // 设置分页查询结果的数据列表，这样就有了具体的数据
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }
}
