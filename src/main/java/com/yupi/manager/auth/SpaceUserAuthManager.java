package com.yupi.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.yupi.manager.auth.model.SpaceUserAuthConfig;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.manager.auth.model.SpaceUserRole;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.SpaceUser;
import com.yupi.model.entity.User;
import com.yupi.model.enums.SpaceRoleEnum;
import com.yupi.model.enums.SpaceTypeEnum;
import com.yupi.service.SpaceUserService;
import com.yupi.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 空间成员权限管理
@Component
public class SpaceUserAuthManager {

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    // 储存 JSON 格式的内容，final 修饰的对象不能二次修改
    private static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    // 静态代码块会在类加载时执行，比构造函数执行早，且执行一次
    static {
        // HuTool 工具，读取 resources 目录中的 biz 中的 json 文件
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        // 将 JSON 字符串反序列化为 java 对象
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    // 根据角色查询其对应的权限
    public List<String> getPermissionByRole(String spaceUserRole){
        if(StrUtil.isBlank(spaceUserRole)){
            return new ArrayList<>();
        }
        // 从 json 文件中获取与 spaceUserRole 角色
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles()
                .stream()
                .filter(r -> r.getKey().equals(spaceUserRole))
                .findFirst()
                .orElse(null);// 没找到则返回 null
        if(role == null)
        {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }

    // 通过角色获取权限
    public List<String> getPermissionsByRole(String spaceUserRole) {
        if (StringUtils.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles()
                .stream()
                .filter(r -> r.getKey().equals(spaceUserRole))
                .findFirst()
                .orElse(null);
        if (role == null) {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }

    // 获取权限列表，用于返回给前端的
    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }
}











