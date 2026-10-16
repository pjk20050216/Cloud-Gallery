package com.yupi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.model.dto.space.SpaceAddRequest;
import com.yupi.model.dto.space.SpaceQueryRequest;
import com.yupi.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.model.entity.User;
import com.yupi.model.vo.SpaceVO;
import org.springframework.cache.annotation.SpringCacheAnnotationParser;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 23372
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-12-19 13:46:59
 */
public interface SpaceService extends IService<Space> {
    /**
     * 创建空间，返回的为空间的 Id
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 校验空间
     *
     * @param space space
     * @param add   是否为创建时检验
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取空间包装类（单条）
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取查询对象
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间级别填充空间对象
     */
    void fillSpaceBySpaceLevel(Space space);

    void checkSpaceAuth(User loginUser, Space space);

    /**
     * 扣除空间token数量
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @param tokenCount 要扣除的token数量
     * @param apiType API类型
     * @param description 描述
     * @return 是否扣除成功
     */
    boolean deductTokens(Long spaceId, Long userId, Long tokenCount, String apiType, String description);

    /**
     * 增加空间token数量
     * @param spaceId 空间ID
     * @param tokenCount 要增加的token数量
     * @return 是否增加成功
     */
    boolean addTokens(Long spaceId, Long tokenCount);

    /**
     * 根据空间级别获取对应的token额度
     * @param spaceLevel 空间级别
     * @return token额度
     */
    Long getTokenQuotaBySpaceLevel(Integer spaceLevel);
}
