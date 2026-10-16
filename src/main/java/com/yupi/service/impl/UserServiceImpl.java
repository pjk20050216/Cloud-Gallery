package com.yupi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.constant.UserConstant;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.manager.auth.StpKit;
import com.yupi.model.dto.user.UserQueryRequest;
import com.yupi.model.entity.User;
import com.yupi.model.enums.UserRoleEnum;
import com.yupi.model.vo.LoginUserVO;
import com.yupi.model.vo.UserVO;
import com.yupi.service.UserService;
import com.yupi.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 23372
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-12-04 16:44:20
 */

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /*
    private final UserService userService;

    public UserServiceImpl(UserService userService) {
        this.userService = userService;
    }*/

    @Override
    public long UserRegister(String userAccount, String userPassword, String checkPassword) {
        // 1、校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }

        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号太短");
        }

        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码太短");
        }

        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "俩次输入密码不相同");
        }
        // 2、检查用户账号是否和数据库中的已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        // 将数据库中的 userAccount 里面的内容与 String userAccount 进行比较

        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已重复");
        }
        // 3、密码一定要加密
        String encryption = PasswordEncryption(userPassword);
        // 4、插入数据到数据库中
        User user = User.builder()
                .userAccount(userAccount)
                .userPassword(userPassword)
                .build();
        boolean save = this.save(user);
        if (!save) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }

        return user.getId();
        /*
         1、由于主键回填原理，数据库将自动生成主键值，回填到 java 对象中
         2、MyBatis-Plus 框架，它默认采用的是雪花算法（Snowflake ID）作为主键生成策略
         因此生成的 id 会很长
         */
    }

    // 用户登录
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1、校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号错误");
        }

        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码太短");
        }
        // 2、密码加密
        PasswordEncryption(userPassword);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", userPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 3、查询数据库中是否存在
        if (user == null) {
            log.info("user login failed, userAccount can't match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者密码错误");
        }
        // 4、保存用户登录状态到 session 中
        request.getSession() // 获得用户登录对应的 HashMap（类似 C++ 中的 Map）
                .setAttribute(UserConstant.USER_LOGIN_STATE, user); // 为这个 HashMap 赋予 键值对
        // 同时也要保存到 sa-token 多账号体系中
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);

        // 5、返回脱敏后的用户信息，就是去除那些密码等不需要显示给前端的信息
        return this.getUserLoginVO(user);
    }

    /*
     1、由于用户已经登录，request 记录了用户登录状态
     2、相当于系统存在登录的缓存
     3、所以可以直接通过 session 来获取用户信息
     4、但要注意：缓存是先前的状态，用户可能在缓存后修改了密码等
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession() // 获得用户登录对应的 HashMap（类似 C++ 中的 Map）
                .getAttribute(UserConstant.USER_LOGIN_STATE);// 获取 HashMap 中的值，就是用户登录状态

        User currentuser = (User) userObj;
        if (currentuser == null || currentuser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); // 用户未登录状态
        }
        Long userId = currentuser.getId();
        currentuser = this.getById(userId);
        if (currentuser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentuser;
    }

    @Override
    public boolean loginOut(HttpServletRequest request) {
        Object userObj = request.getSession() // 获得用户登录对应的 HashMap（类似 C++ 中的 Map）
                .getAttribute(UserConstant.USER_LOGIN_STATE);// 获取 HashMap 中的值，就是用户登录状态

        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);

        return true;
    }


    // 密码加密
    @Override
    public String PasswordEncryption(String password) {
        final String add = "yu_pi"; // 这样可以防止别人把密码猜出来
        return DigestUtils.md5DigestAsHex((add + password).getBytes());
    }

    // 登录用户脱敏
    @Override
    public LoginUserVO getUserLoginVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO); // loginUserVO 要添加无参构造才可以使用
        return loginUserVO;
    }

    // 用户脱敏
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    // 用户列表脱敏
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    // 获取 MyBatis 能够使用的分页查询的条件
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 从对象中获取内容
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        // 从数据库中获取内容
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

}




