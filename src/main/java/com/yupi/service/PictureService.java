package com.yupi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskResponse;
import com.yupi.model.dto.picture.*;
import com.yupi.model.dto.user.UserQueryRequest;
import com.yupi.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.model.entity.User;
import com.yupi.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author 23372
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-12-11 16:42:29
 */
public interface PictureService extends IService<Picture> {
    // 图片校验
    void validPicture(Picture picture);

    /**
     * 上传图片
     */
    PictureVO uploadPicture(Object object,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    // 获取 MyBatis 能够使用的分页查询的条件
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    // 对图片中信息脱敏
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    // 4、分页获取图片封装
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    // 5、图片审核
    void PictureReview(PictureReviewRequest pictureReviewRequest, User loginUser); // loginUser 就用于获取谁进行了图片审核

    void fileReviewParams(Picture picture, User loginUser);

    // 批量抓取图片
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                 User loginUser);

    /**
     * 批量抓取图片（带进度回调，供异步任务使用）
     *
     * @param onProgress 每成功上传一张回调一次，参数为已成功数量；可为 null
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                 User loginUser,
                                 java.util.function.Consumer<Integer> onProgress);

    // 清除 COS 中的图片文件
    void clearPictureFile(Picture picture);

    // 删除图片操作
    void deletePicture(User loginUser, long pictureId);

    // 编辑图片
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    // 校验图片的空间权限是否正确
    void checkPictureAuth(User loginUser, Picture picture);

    // 根据颜色查询图片
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    // 批量编辑图片
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * AI 扩展图片服务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser                           登录的用户
     * @return
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

}
