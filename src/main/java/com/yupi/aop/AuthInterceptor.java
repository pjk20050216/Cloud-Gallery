package com.yupi.aop;


import com.yupi.annotation.AutoCheck;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.entity.User;
import com.yupi.model.enums.UserRoleEnum;
import com.yupi.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;


// 校验用户身份
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /*
     1、定义哪些方法要进行切点一下
     2、对方法进行拦截
     3、校验其身份信息
     */
    @Around("@annotation(autoCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AutoCheck autoCheck) throws Throwable { // autoCheck 权限校验注解
        // 1、获取被 AutoCheck 注解定义的方法上设置的 mustRole 内容，可以理解为：门禁系统的标签：只有房主才能进入
        String mustRole = autoCheck.mustRole();
        // 2、获取请求信息的用户的信息，看下面的步骤基本能理解
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        // 3、根据用户的角色获取其含有的权限
        UserRoleEnum mustRoleEnum = UserRoleEnum.getTextByValue(mustRole);
        // 4、如果不需要权限，则放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 5、用户实际含有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getTextByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 5、判断是否含管理员权限
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 6、通过权限校验，放行
        return joinPoint.proceed();
    }
}
