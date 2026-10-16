package com.yupi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.model.dto.user.UserQueryRequest;
import com.yupi.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.model.vo.LoginUserVO;
import com.yupi.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public interface UserService extends IService<User> {
    // 1、用户注册
    long UserRegister(String userAccount, String userPassword, String checkPassword);

    // 2、用户登录，同时记录缓存
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);
    // HttpServletRequest：类似点餐表，用于记录用户的行为

    // 3、获取用户登录，就是获取用户登录的缓存
    User getLoginUser(HttpServletRequest request);

    // 4、用户退出登录，就是去除 session
    boolean loginOut(HttpServletRequest request);

    // 密码加密
    String PasswordEncryption(String password);

    // 登录用户信息脱敏
    LoginUserVO getUserLoginVO(User user);

    // 用户信息脱敏
    UserVO getUserVO(User user);

    // 用户列表脱敏
    List<UserVO> getUserVOList(List<User> userList);

    // 将 UserQueryWrapper 中的查询条件转为 MyBatis 需要
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    boolean isAdmin(User user);

}
