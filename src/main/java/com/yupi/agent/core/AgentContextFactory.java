package com.yupi.agent.core;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.manager.auth.SpaceUserAuthManager;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.mapper.SpaceMapper;
import com.yupi.mapper.SpaceUserMapper;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.SpaceUser;
import com.yupi.model.entity.User;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.model.enums.AppealStatusEnum;
import com.yupi.model.enums.SpaceRoleEnum;
import com.yupi.service.UserBanService;
import com.yupi.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 在请求线程中构建 {@link AgentContext}。
 * <p>
 * 权限快照的计算方式：
 * <ul>
 *     <li>自己创建的空间 → 拥有者，等同于空间管理员权限</li>
 *     <li>作为成员加入的团队空间 → 按 spaceRole 取权限</li>
 *     <li>平台管理员 → 直接拥有空间管理员权限</li>
 * </ul>
 * <p>
 * 这里直接使用 Mapper 而不是 SpaceService / SpaceUserService：
 * 一是这两条查询非常简单，二是可以避免牵入
 * SpaceServiceImpl ↔ SpaceAnalyzeServiceImpl 的循环依赖。
 */
@Component
public class AgentContextFactory {

    @Resource
    private UserService userService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private UserBanService userBanService;

    public AgentContext build(User loginUser) {
        boolean admin = userService.isAdmin(loginUser);
        Long userId = loginUser.getId();

        // 封禁状态必须在请求线程里查好（异步线程里拿不到）
        UserBanRecord activeBan = userBanService.getActiveBan(userId);

        // 1. 自己创建的空间
        List<Space> ownedSpaces = spaceMapper.selectList(new LambdaQueryWrapper<Space>()
                .eq(Space::getUserId, userId));

        // 2. 作为成员加入的空间
        List<SpaceUser> memberships = spaceUserMapper.selectList(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getUserId, userId));

        // 3. 合并权限与可访问空间
        List<String> ownerPermissions =
                spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        Set<String> permissions = new LinkedHashSet<>();
        Set<Long> spaceIds = new LinkedHashSet<>();

        for (Space space : ownedSpaces) {
            spaceIds.add(space.getId());
            permissions.addAll(ownerPermissions);
        }
        for (SpaceUser membership : memberships) {
            spaceIds.add(membership.getSpaceId());
            permissions.addAll(spaceUserAuthManager.getPermissionsByRole(membership.getSpaceRole()));
        }
        if (admin) {
            permissions.addAll(ownerPermissions);
        }
        if (permissions.isEmpty()) {
            // 保底：任何登录用户至少能查看
            permissions.add(SpaceUserPermissionConstant.PICTURE_VIEW);
        }

        return AgentContext.builder()
                .userId(userId)
                .userAccount(loginUser.getUserAccount())
                .userName(loginUser.getUserName())
                .admin(admin)
                .banned(activeBan != null)
                .banMessage(activeBan == null ? null : buildBanMessage(activeBan))
                .permissions(new ArrayList<>(permissions))
                .accessibleSpaceIds(new ArrayList<>(spaceIds))
                .traceId(IdUtil.fastSimpleUUID())
                .build();
    }

    private String buildBanMessage(UserBanRecord record) {
        StringBuilder sb = new StringBuilder("账号当前处于封禁状态。");
        if (StrUtil.isNotBlank(record.getReasonDetail())) {
            sb.append("原因：").append(record.getReasonDetail()).append("。");
        }
        if (record.getEndTime() == null) {
            sb.append("解封时间：永久。");
        } else {
            sb.append("解封时间：")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(record.getEndTime()))
                    .append("。");
        }
        sb.append("申诉状态：").append(AppealStatusEnum.getTextByValue(record.getAppealStatus())).append("。");
        return sb.toString();
    }
}
