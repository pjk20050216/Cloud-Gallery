package com.yupi.controller;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.annotation.AutoCheck;
import com.yupi.api.aliYunAi.AliYunAiApi;
import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskResponse;
import com.yupi.api.aliYunAi.model.GetOutPaintingTaskResponse;
import com.yupi.api.imageSearch.ImageSearchApiFacade;
import com.yupi.api.imageSearch.model.ImageSearchResult;
import com.yupi.common.BaseResponse;
import com.yupi.common.DeleteRequest;
import com.yupi.common.ResultUtils;
import com.yupi.constant.UserConstant;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.manager.auth.SpaceUserAuthManager;
import com.yupi.manager.auth.StpKit;
import com.yupi.manager.auth.annotation.SaSpaceCheckPermission;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.dto.picture.*;
import com.yupi.model.entity.Picture;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.User;
import com.yupi.model.enums.PictureReviewStatusEnum;
import com.yupi.model.vo.PictureTagCategory;
import com.yupi.model.vo.PictureVO;
import com.yupi.service.PictureService;
import com.yupi.service.SpaceService;
import com.yupi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    // 注入各种服务依赖
    @Resource
    private UserService userService; // 用户服务

    @Resource
    private PictureService pictureService; // 图片服务

    @Resource
    private SpaceService spaceService; // 空间服务

    // Redis 缓存使用（属于二级缓存）
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi; // 阿里云AI API

    // 本地缓存 caffeine 配置（这种属于一级缓存）
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024) // 初始容量
            .maximumSize(10000L) // 缓存最大 10000条
            // 缓存 5 分钟移除
            .expireAfterWrite(5L, TimeUnit.MINUTES)
            .build();
    @Autowired
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 生成缓存键
     * @param pictureId 图片ID
     * @return 缓存键
     */
    private String buildPictureCacheKey(Long pictureId) {
        return "picture:detail:" + pictureId;
    }

    /**
     * 生成列表缓存键
     * @param queryRequest 查询请求
     * @return 缓存键
     */
    private String buildPictureListCacheKey(PictureQueryRequest queryRequest) {
        String queryCondition = JSONUtil.toJsonStr(queryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        return "picture:list:" + hashKey;
    }


    // 1、文件形式上传图片
    @PostMapping("/upload")
    // @AutoCheck(mustRole = "admin")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    // 新的 sa-token 注解式权限校验
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile,
                                                 PictureUploadRequest pictureUploadRequest,
                                                 HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    // 2、url 形式上传图片
    @PostMapping("/upload/url")
    // @AutoCheck(mustRole = "admin")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadByUrl(@RequestBody PictureUploadRequest pictureUploadRequest,
                                               HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String url = pictureUploadRequest.getUrl();
        PictureVO pictureVO = pictureService.uploadPicture(url, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    // 图片的删除
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> PictureDelete(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 通过请求获取操作的用户
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(loginUser, deleteRequest.getId());
        return ResultUtils.success(true);
    }

    // 仅管理员可以用的图片更新操作
    @PostMapping("/update")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {

        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fileReviewParams(oldPicture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 清理缓存，用于保证数据一致性
        String cacheKey = buildPictureCacheKey(picture.getId());
        // 删除Redis缓存
        stringRedisTemplate.delete(cacheKey);
        // 删除本地缓存
        LOCAL_CACHE.invalidate(cacheKey);

        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    // 如果这里使用 sa-token 则只能登录才能查看图片，所以不行
    public BaseResponse<PictureVO> getPictureVOById(Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            // 利用 sa-token 编程式进行权限校验，因为 spaceId 不为空，说明用户已经登录了，所以只在这里用校验
            boolean hashPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hashPermission, ErrorCode.NO_AUTH_ERROR);
            /*
            已经改为 sa-token 鉴权
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
            */
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 公共图库允许匿名访问，所以这里不能强制要求登录：
        // 未登录时只返回图片本身，permissionList 为空。
        User loginUser = null;
        try {
            loginUser = userService.getLoginUser(request);
        } catch (BusinessException e) {
            // 未登录，忽略
        }
        List<String> permissionList = loginUser == null
                ? new ArrayList<>()
                : spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    // 分页获取图片列表（封装类），这个可以理解为用户在页面中看到的一排排图片
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) // 这种为只能查看公共图库的
        {
            // 然后限制用户只能看到审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true); // true 为查看公共图库
        } else // 这种为查看私有空间的，说明用户已经登录了
        {
            // 利用 sa-token 编程式进行权限校验
            boolean hashPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hashPermission, ErrorCode.NO_AUTH_ERROR);
            /*
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问改空间");
            }
            */
        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    // 查询图片时利用 caffeine + redis 俩个缓存
    @Deprecated // 注解作用：标记改方法已经不能再使用了
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 然后限制用户只能看到审核通过的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 查询缓存，缓存没有再查询数据库
        // 1、构建缓存的 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("listPictureVOByPageWithCache:%s", hashKey);

        // 2、先查 caffeine 缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey); // 用于查询的
        if (cachedValue != null) {
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);// 把缓存转为 Page 对象
            return ResultUtils.success(cachedPage);
        }
        // 3、caffeine 缓存没查到，则查 redis 分步式缓存
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        if (cachedValue != null) {
            // 如果缓存存在，则更新 caffeine 缓存
            LOCAL_CACHE.put(cacheKey, cachedValue);
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);// 把缓存转为 Page 对象
            return ResultUtils.success(cachedPage);
        }

        // 3、没有缓存再查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 4、数据库查询到的数据存到俩个缓存中
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage); // 用于储存的
        // 设置缓存的过期时间 5 - 10 分钟，并且要随机，防止缓存雪崩（一堆缓存全部同时过期）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);

        // redis 缓存的储存方式：
        opsForValue.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // caffeine 本地缓存储存
        LOCAL_CACHE.put(cacheKey, cacheValue);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    // 这个是前端页面展示的一些可选择的标签，可以直接写死
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 审核图片
     */
    @PostMapping("/review")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> PictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.PictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    // 批量抓取并创建图片
    @PostMapping("/picture/uploadBatch")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer i = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(i);
    }

    // 以图搜图
    @PostMapping("/search/image")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {

        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null, ErrorCode.NOT_FOUND_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> listPicture = ImageSearchApiFacade.searchImage(picture.getUrl());
        return ResultUtils.success(listPicture);
    }

    // 按照颜色搜索
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOS = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOS);
    }

    // 批量编辑图片
    @PostMapping("/edit/Batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureBatch(@RequestBody PictureEditByBatchRequest pictureEditBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    // AI 扩图任务
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createOutPaintingTaskResponse(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }
}
