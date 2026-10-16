package com.yupi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.annotation.AutoCheck;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.constant.UserConstant;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.dto.report.PictureReportAddRequest;
import com.yupi.model.dto.report.PictureReportHandleRequest;
import com.yupi.model.dto.report.PictureReportQueryRequest;
import com.yupi.model.entity.PictureReport;
import com.yupi.model.entity.User;
import com.yupi.model.vo.PictureReportVO;
import com.yupi.service.PictureReportService;
import com.yupi.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/report")
public class PictureReportController {

    @Resource
    private PictureReportService pictureReportService;

    @Resource
    private UserService userService;

    /**
     * 用户提交举报
     */
    @PostMapping("/add")
    public BaseResponse<Long> addReport(@RequestBody PictureReportAddRequest pictureReportAddRequest,
                                         HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReportAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureReportService.addReport(pictureReportAddRequest, loginUser);
        return ResultUtils.success(null);
    }

    /**
     * 管理员处理举报（含下架图片）
     */
    @PostMapping("/handle")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> handleReport(@RequestBody PictureReportHandleRequest pictureReportHandleRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReportHandleRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureReportService.handleReport(pictureReportHandleRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 管理员查看举报详情
     */
    @GetMapping("/get")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureReportVO> getReportById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        PictureReport report = pictureReportService.getById(id);
        ThrowUtils.throwIf(report == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(pictureReportService.getReportVO(report));
    }

    /**
     * 管理员分页查询举报列表
     */
    @PostMapping("/list/page")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<PictureReportVO>> listReportByPage(@RequestBody PictureReportQueryRequest queryRequest) {
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        Page<PictureReport> reportPage = pictureReportService.page(new Page<>(current, size),
                pictureReportService.getQueryWrapper(queryRequest));
        return ResultUtils.success(pictureReportService.getReportVOPage(reportPage));
    }

    /**
     * 用户查看自己的举报记录
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<PictureReportVO>> listMyReportByPage(@RequestBody PictureReportQueryRequest queryRequest,
                                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        queryRequest.setUserId(loginUser.getId());
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        Page<PictureReport> reportPage = pictureReportService.page(new Page<>(current, size),
                pictureReportService.getQueryWrapper(queryRequest));
        return ResultUtils.success(pictureReportService.getReportVOPage(reportPage));
    }
}
