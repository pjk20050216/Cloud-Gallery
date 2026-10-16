package com.yupi.manager.auth;

import com.yupi.model.entity.Picture;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.SpaceUser;
import lombok.Data;

import java.util.List;

// 用于统一储存我们从请求中拿到的值

@Data
public class SpaceUserAuthContext {

    /**
     * 临时参数，不同请求对应的 id 可能不同
     */
    private Long id;

    /**
     * 权限列表
     */
    List<String> permissionList;

    /**
     * 图片 ID
     */
    private Long pictureId;

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 空间用户 ID
     */
    private Long spaceUserId;

    /**
     * 图片信息
     */
    private Picture picture;

    /**
     * 空间信息
     */
    private Space space;

    /**
     * 空间用户信息
     */
    private SpaceUser spaceUser;
}

