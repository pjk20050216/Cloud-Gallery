package com.yupi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.api.aliYunAi.AliYunAiApi;
import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskRequest;
import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskResponse;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.manager.CosManager;
import com.yupi.manager.FileManager;
import com.yupi.manager.upload.FilePictureUpload;
import com.yupi.manager.upload.PictureUploadTemplate;
import com.yupi.manager.upload.UrlPictureUpload;
import com.yupi.model.dto.file.UploadPictureResult;
import com.yupi.model.dto.picture.*;
import com.yupi.model.entity.Picture;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.User;
import com.yupi.model.enums.PictureReviewStatusEnum;
import com.yupi.model.vo.PictureVO;
import com.yupi.model.vo.UserVO;
import com.yupi.service.PictureService;
import com.yupi.mapper.PictureMapper;
import com.yupi.service.SpaceService;
import com.yupi.service.UserService;
import com.yupi.utils.ColorSimilarUtils;
import com.yupi.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author 23372
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-12-11 16:42:29
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private UserService userService;

    @Resource
    private FileManager fileManager;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private SpaceService spaceService;

    @Autowired
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    // 注入异步线程池，这个是用于批量抓取图片的
    // 线程池的定义已移到 com.yupi.configuration.TaskExecutorConfig
    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    // 图片校验
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // url：图片地址
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        // introduction：图片简介
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    // 1、图片上传，可以包含对原图的更新或者上传新图片
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1、校验参数
        // 1、用户是否存在
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2、空间校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验上传权限，只空间的管理员才能上传
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间的权限");
            }
            // 校验空间的额度是否够
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间条数不够");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间大小不够");
            }
        }

        // 2、判断操作类型：新增，删除，更新
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        if (pictureId != null) // 1、图片存在则为更新操作
        {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.PARAMS_ERROR, "图片不存在");
            // 然后进行更新操作，但更新操作只能本人获取管理员使用
            if (!oldPicture.getUserId().equals(loginUser.getId()) && userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 校验这个更新图片传的空间
            if (spaceId == null) // 用户没传 spaceId
            {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else // 传的 spaceId，则要保证与原有的相同
            {
                if (ObjectUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 3、上传图片，并得到图片信息

        // 规定上传文件的前缀，就是规定文件存放的目录，按照用户 Id、空间 Id 来划分
        String uploadPrefix;
        if (spaceId == null) {
            // spaceId 为空，则说明上传的为公共图库
            uploadPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPrefix = String.format("space/%s", spaceId);
        }

        // 根据 inputSource 区分上传的方式（动态选择上传策略，使用了模板方法）
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload; // 先初始为文件上传形式
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        // 构建要创建数据库对象的图片信息，调用模板方法
        UploadPictureResult pictureResult = pictureUploadTemplate.uploadPictureResult(inputSource, uploadPrefix);

        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 设置空间 Id
        picture.setUrl(pictureResult.getUrl());
        picture.setThumbnailUrl(pictureResult.getThumbnailUrl());
        // 进行自定义图片的名称
        String picName = pictureResult.getName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(pictureResult.getPicSize());
        picture.setPicWidth(pictureResult.getPicWidth());
        picture.setPicHeight(pictureResult.getPicHeight());
        picture.setPicScale(pictureResult.getPicScale());
        picture.setPicFormat(pictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        // picture.setPicColor(pictureResult.getPicColor());
        // 转为标准颜色格式
        picture.setPicColor(ColorTransformUtils.getStandardColor(pictureResult.getPicColor()));
        // 补充审核参数
        this.fileReviewParams(picture, loginUser);

        // 4、操作数据库
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        Long finalSpaceId = spaceId; // 因为事务过程要保证相对稳定，所以得重新创建一个常量，用于事务的执行

        // 采用编程式事务
        transactionTemplate.execute(status -> {
            boolean saveOrUpdate = this.saveOrUpdate(picture);
            // this.clearPictureFile(picture); 如果是更新操作可以清理图片资源
            ThrowUtils.throwIf(!saveOrUpdate, ErrorCode.OPERATION_ERROR, "数据库操作失败");
            // 更新空间的可使用额度，同时也要使用事务，传图片和更新额度是一起的
            // 注意：spaceId 为空表示公共图库，此时不需要（也不能）更新空间统计，
            // 否则 eq(Space::getId, null) 会更新 0 行并抛"空间更新失败"
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize()) // 类似与数据库执行语句（简单化了一些）
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间更新失败");
            }
            return picture; // 随便返回一个
        });

        return PictureVO.ObjToVO(picture);
    }

    // 2、分页查询，获取 MyBatis 能够使用的分页查询的条件
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();

        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        // 根据不同查询参数动态构建 SQL 查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewMessage), "reviewMessage", reviewMessage);
        // 表示：判断数据库中的值 editTime 是否大于 startEditTime 变量的值
        queryWrapper.gt(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // 这个则为判断 数据库的值 editTime 是否小于 endEditTime 的值
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序（sortOrder 可能为 null，必须判空，否则这里会 NPE）
        if (StrUtil.isNotEmpty(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        }
        return queryWrapper;
    }

    // 3、获取单个图片中信息
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.ObjToVO(picture);
        // 关联查询用户信息，原理：通过 id 在数据库中查出来
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    // 4、分页获取图片封装
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::ObjToVO)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 取出所有图片中的用户 id，组成一个集合
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());
        // 对这个用户 id 进行分组，就是利用 Map 集合
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    // 校验图片
    @Override
    public void PictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);

        // 1、校验参数是否有问题
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();

        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getTextByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();

        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2、判断图片是否存在，这个 oldPicture 只用于判断图片是否存在，不干其他操作
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3、检验图片的状态是否重复
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }

        // 4、然后对数据库进行操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean b = this.updateById(updatePicture);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR);
    }

    // 填充审核参数的公共方法
    @Override
    public void fileReviewParams(Picture picture, User loginUser) {
        // 如果是管理员则自动过审
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }

    }

    // 批量获取图片，其中使用的是
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        return uploadPictureByBatch(pictureUploadByBatchRequest, loginUser, null);
    }

    // 批量获取图片（带进度回调）
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                        User loginUser,
                                        java.util.function.Consumer<Integer> onProgress) {
        // 1、校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "抓取数量不超过30条");
        // 名称前缀默认为搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getSearchText();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 2、抓取内容（从一个网站中抓取，关键字为 q 等于号后面的内容）
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        // 这个 document 可以理解为一个 html，因此可以从中批量获取图片
        Document document;
        try {
            // 通过 Jsoup 工具来抓取
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 3、解析内容（Element：HTML 文档中的一个节点或标签）
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjectUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imageElementList = div.select("img.mimg");

        /* 4、原始 for 循环抓取图片的方法
        int uploadCount = 0; // 表示上传图片数量

        for (Element element : imageElementList) {
            // element 代表一个 <img ...> 标签对象，而.attr("src") 就是从这个标签里拿到 src="..." 属性对应的值（也就是图片的链接）。
            String fileUrl = element.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前地址为空，跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址：处理宽高，特殊字符，例如：ohMyGood.cn?yu=dog 就应该只取 ohMyGood.cn
            int indexed = fileUrl.indexOf("?");
            if (indexed > -1) {
                fileUrl = fileUrl.substring(0, indexed);
            }

            // 处理完毕
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功：图片 id：{}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
         */

        // 4、使用 CompletableFuture 并行处理图片上传
        // 限制处理的图片数量，避免解析过多无效元素
        int limit = Math.min(imageElementList.size(), count);
        List<Element> subList = imageElementList.subList(0, limit);

        // 用于收集异步任务结果的列表
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 成功上传计数（供进度回调使用）
        final java.util.concurrent.atomic.AtomicInteger successCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        // 遍历元素列表，提交异步任务
        for (int i = 0; i < subList.size(); i++) {
            Element element = subList.get(i);
            // 使用索引确保文件名唯一
            final int index = i + 1;

            // 提交异步任务到线程池
            String finalNamePrefix = namePrefix;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String fileUrl = element.attr("src");
                    if (StrUtil.isBlank(fileUrl)) {
                        log.info("当前地址为空，跳过：{}", fileUrl);
                        return;
                    }
                    // 处理图片地址
                    int indexed = fileUrl.indexOf("?");
                    if (indexed > -1) {
                        fileUrl = fileUrl.substring(0, indexed);
                    }

                    PictureUploadRequest request = new PictureUploadRequest();
                    request.setUrl(fileUrl);
                    request.setPicName(finalNamePrefix + index);

                    // 执行上传
                    PictureVO pictureVO = this.uploadPicture(fileUrl, request, loginUser);
                    log.info("图片上传成功：图片 id：{}", pictureVO.getId());
                    // 成功计数 + 进度回调（多线程并发，用原子变量）
                    if (onProgress != null) {
                        int done = successCount.incrementAndGet();
                        try {
                            onProgress.accept(done);
                        } catch (Exception e) {
                            // 进度回调里的异常（例如任务被取消）不应被吞掉，直接中断
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("图片上传失败", e);
                    // 注意：这里不抛出异常，以免影响其他任务
                }
            }, taskExecutor); // 指定线程池

            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("批量上传任务执行异常", e);
        }

        // 返回实际成功上传的数量
        return successCount.get();

    }

    // 清理图片
    @Async // 表示改方法会在独立的线程中执行，调用者不会等待方法执行完成就继续执行后续代码。
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断图片是否被多条记录查询
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        if (count > 1) {
            // 有多条记录查询则不删除
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    // 删除图片操作
    @Override
    public void deletePicture(User loginUser, long pictureId) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        // 通过图片获取图片本人的用户
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，仅管理员和用户本人能够删除图片
        // 已经改为 sa-token 鉴权
        // checkPictureAuth(loginUser, oldPicture);
        // 事务执行
        transactionTemplate.execute(status -> {
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "数据库操作失败");
            // 公共图库（spaceId 为空）不涉及空间统计，跳过，否则会更新 0 行并抛异常
            if (oldPicture.getSpaceId() != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize()) // 类似与数据库执行语句（简单化了一些）
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间更新失败");
            }
            return true; // 随便返回一个
        });
        this.clearPictureFile(oldPicture);
    }

    // 编辑图片
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        // 改为 sa-token 鉴权
        // this.checkPictureAuth(loginUser, oldPicture);

        // 填充审核参数
        this.fileReviewParams(picture, loginUser);

        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    // 校验图片的空间权限是否合理
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long userId = loginUser.getId();
        if (spaceId == null) {
            // 说明为公共图片库，则只能用户本人和管理员能够修改
            if (!spaceId.equals(userId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅用户本人和管理员可修改");
            }
        } else {
            if (!spaceId.equals(userId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅用户本人可修改");
            }
        }
    }

    // 搜索所有与本图片颜色类似的图片，然后返回
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1、校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 2、校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 3、返回查询空间下的所有图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 4、将颜色字符串转为主色调（用于判断某颜色与某颜色的相似度的）
        Color targetColor = Color.decode(picColor);

        // 5、计算颜色的相似度并排序
        List<Picture> sortPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片默认排到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度，并且值越大越相似，因此为了让大的值排在前面，得加 -
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 只取前 12 个
                .collect(Collectors.toList());
        // 6、返回结果，得转为 PictureVO
        return sortPictureList.stream()
                .map(PictureVO::ObjToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1、校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2、校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
        }
        // 3、查询指定图片
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .in(Picture::getId, pictureIdList)
                .eq(Picture::getSpaceId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4、更新分类标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithRule(pictureList, nameRule);

        // 5、操作数据库批量更新
        boolean result = this.updateBatchById(pictureList); // 这个方法体为一个 java 内置的
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(
            CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId)) // 类似判断是否为空
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 检验权限
        // 改为 sa-token 鉴权
        // checkPictureAuth(loginUser, picture);
        // 进行扩图任务
        // 1、创建结果响应对象
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        // 2、结果赋值
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        Long userId = loginUser.getId();
        return aliYunAiApi.createOutPaintingTask(userId, createOutPaintingTaskRequest);
    }

    // 根据 nameRule 中的内容，依次对 pictureList 中的的每个 picture 的名称修改
    private void fillPictureWithRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        try {
            long count = 1;
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                // 因为 nameRule 的格式为：图片{序号}
            }
        } catch (Exception e) {
            log.error("名称解析失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析失败");
        }

    }
}




