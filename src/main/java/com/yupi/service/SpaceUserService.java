package com.yupi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.model.dto.spaceuser.SpaceUserAddRequest;
import com.yupi.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yupi.model.entity.SpaceUser;
import com.yupi.model.vo.SpaceUserVO;

import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public interface SpaceUserService extends IService<SpaceUser> {
    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add       是否为创建时检验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员包装类（列表）
     *
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    // 扣除用户使用 AI 的额度
    boolean deductTokens(Long userId, Integer tokenCost);

    // 增加用户使用 AI 的额度
    boolean addTokens(Long userId, Integer tokenNum);
}
