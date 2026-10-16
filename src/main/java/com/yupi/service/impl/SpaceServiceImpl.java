package com.yupi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.manager.sharding.DynamicShardingManager;
import com.yupi.model.dto.space.SpaceAddRequest;
import com.yupi.model.dto.space.SpaceQueryRequest;
import com.yupi.model.entity.*;
import com.yupi.model.enums.SpaceLevelEnum;
import com.yupi.model.enums.SpaceRoleEnum;
import com.yupi.model.enums.SpaceTypeEnum;
import com.yupi.model.vo.*;
import com.yupi.model.vo.SpaceVO;
import com.yupi.service.SpaceAnalyzeService;
import com.yupi.service.SpaceService;
import com.yupi.mapper.SpaceMapper;
import com.yupi.service.SpaceUserService;
import com.yupi.service.UserService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * @author 23372
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-12-19 13:46:59
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate; // 用于操作事务的

    @Resource
    private SpaceUserService spaceUserService;
    @Autowired
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private RedissonClient redissonClient;

    // 为了方便部署，注释掉分库分表
    // @Resource
    // @Lazy
    // private DynamicShardingManager dynamicShardingManager;

    // 1、添加私有空间
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1、填充参数的默认值
        // 实体类转 DTO
        Space space = new Space();
        BeanUtil.copyProperties(spaceAddRequest, space);
        // 设置默认值，初始化
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 设置默认值
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充空间容量
        this.fillSpaceBySpaceLevel(space);
        // 2、校验参数
        this.validSpace(space, true);
        // 3、权限校验，非管理员只能创建普通级别空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别空间");
        }
        // 4、控制同一用户只能创建一个私有空间，以及一个团队空间
        // 使用 Redisson 分布式锁，按 userId + spaceType 粒度加锁
        String lockKey = "space:create:lock:" + userId + ":" + space.getSpaceType();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // tryLock(waitTime, leaseTime, unit): 等待最多 10 秒获取锁，持锁 30 秒后自动释放防止死锁
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                try {
                    Long newSpaceId = transactionTemplate.execute(status -> {
                        // 判断是否已有空间，有则不创建
                        boolean exists = this.lambdaQuery()
                                .eq(Space::getUserId, userId)
                                .eq(Space::getSpaceType, space.getSpaceType())
                                .exists();
                        ThrowUtils.throwIf(exists, ErrorCode.PARAMS_ERROR, "每个用户仅能有一个私有空间或者团队空间");
                        boolean result = this.save(space);
                        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间保存到数据库失败");
                        // 为团队空间则关联新增团员记录
                        if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                            SpaceUser spaceUser = new SpaceUser();
                            spaceUser.setSpaceId(space.getId());
                            spaceUser.setUserId(userId);
                            spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                            result = spaceUserService.save(spaceUser);
                            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "团队空间创建失败");
                        }
                        return space.getId();
                    });
                    return newSpaceId;
                } finally {
                    lock.unlock();
                }
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作太频繁，请稍后再试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统繁忙，请稍后再试");
        }
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName(); // 空间名称
        Integer spaceLevel = space.getSpaceLevel(); // 空间级别
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        if (add) {
            // 1、这种为创建时进行校验
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不能为空");
            }
        }
        // 2、这种为修改时校验
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        // spaceLevelEnum 一直不会为 null，是因为其内部创建时的原理
        if (spaceLevel == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不存在");
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息，原理：通过 id 在数据库中查出来
        Long userId = spaceVO.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> SpaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return SpaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> SpaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 取出所有图片中的用户 id，组成一个集合
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .collect(Collectors.toSet());
        // 对这个用户 id 进行分组，就是利用 Map 集合
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2. 填充信息
        SpaceVOList.forEach(SpaceVO -> {
            Long userId = SpaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            SpaceVO.setUser(userService.getUserVO(user));
        });
        SpaceVOPage.setRecords(SpaceVOList);
        return SpaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        Integer spaceType = spaceQueryRequest.getSpaceType();

        // 根据不同查询参数动态构建 SQL 查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    // 根据空间级别填充空间的容量
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    // 增加空间 token 的数量
    @Override
    public boolean deductTokens(Long spaceId, Long userId, Long tokenCount, String apiType, String description) {
        // 检查空间是否存在
        Space space = this.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        // 检查余额是否充足
        if (space.getRemainingTokens() < tokenCount) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您的API调用额度不足");
        }

        // 扣除token
        space.setUsedTokens(space.getUsedTokens() + tokenCount);
        space.setRemainingTokens(space.getRemainingTokens() - tokenCount);
        boolean updateSuccess = this.updateById(space);

        // 记录token使用情况
        if (updateSuccess) {
            TokenUsageRecord record = new TokenUsageRecord();
            record.setSpaceId(spaceId);
            record.setUserId(userId);
            record.setTokenCount(tokenCount);
            record.setApiType(apiType);
            record.setDescription(description);
            // 这里得改：tokenUsageRecordService.save(record);
        }

        return updateSuccess;
    }

    // 增加空间 token
    @Override
    public boolean addTokens(Long spaceId, Long tokenCount) {
        // 检查空间是否存在
        Space space = this.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        // 增加token
        space.setTotalTokens(space.getTotalTokens() + tokenCount);
        space.setRemainingTokens(space.getRemainingTokens() + tokenCount);
        return this.updateById(space);
    }

    // 根据空间的级别获取对应的 token 额度
    @Override
    public Long getTokenQuotaBySpaceLevel(Integer spaceLevel) {
        // 根据空间级别返回对应的token额度
        switch (spaceLevel) {
            case 0: // 普通版
                return 100L;
            case 1: // 专业版
                return 1000L;
            case 2: // 旗舰版
                return 10000L;
            default:
                return 0L;
        }
    }
}




