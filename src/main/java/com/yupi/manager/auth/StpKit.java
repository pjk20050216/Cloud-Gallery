package com.yupi.manager.auth;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/*
1、StpLogic 门面类，管理项目中所有的 StpLogic 账号体系
2、添加 @Component 注解的目的是确保静态属性 DEFAULT 和 SPACE 被初始化
3、使用例子：
    1、在当前会话进行Space账号登录
    StpKit.SPACE.login(10001);

    2、检测当前会话是否以Space账号登录，并具有 picture:edit权限
    StpKit.SPACE.checkPermission("picture:edit");

    3、获取当前Space会话的Session 对象，并进行写值操作
    StpKit.SPACE.getSession().set("user"，"程序员鱼皮");
 */

@Component
public class StpKit {

    // 表示空间的权限账号体系
    public static final String SPACE_TYPE = "space";

    /**
     * 默认原生会话对象，项目中目前没使用到
     */
    public static final StpLogic DEFAULT = StpUtil.stpLogic;

    /**
     * Space 会话对象，管理 Space 表所有账号的登录、权限认证
     */
    public static final StpLogic SPACE = new StpLogic(SPACE_TYPE);
}

