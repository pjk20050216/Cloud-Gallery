package com.yupi.manager.auth.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;


// 团队各成员的权限配置，对应 resource 中的 json 文件
@Data
public class SpaceUserAuthConfig implements Serializable {

    /**
     * 权限列表
     */
    private List<SpaceUserPermission> permissions;

    /**
     * 角色列表
     */
    private List<SpaceUserRole> roles;

    private static final long serialVersionUID = 1L;
}

