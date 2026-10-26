package com.yupi.controller;

import com.yupi.annotation.AutoCheck;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.constant.UserConstant;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.dto.ban.BanAppealReviewRequest;
import com.yupi.model.dto.ban.BanAppealRequest;
import com.yupi.model.dto.ban.UserBanRequest;
import com.yupi.model.dto.ban.UserUnbanRequest;
import com.yupi.model.entity.User;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.model.vo.UserBanRecordVO;
import com.yupi.service.UserBanService;
import com.yupi.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户封禁相关接口。
 * <p>
 * 管理端：封禁 / 解封 / 查询 / 审核申诉
 * 用户端：查看自己的处罚记录 / 提交申诉
 */
@RestController
@RequestMapping("/user/ban")
public class UserBanController {

    @Resource
    private UserService userService;

    @Resource
    private UserBanService userBanService;

    // ---------------------------------------------------------------- 管理端

    /**
     * 封禁用户
     */
    @PostMapping("/add")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> banUser(@RequestBody UserBanRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        UserBanRecord record = userBanService.ban(request, loginUser);
        return ResultUtils.success(record.getId());
    }

    /**
     * 解封
     */
    @PostMapping("/unban")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> unban(@RequestBody UserUnbanRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(userBanService.unban(request, loginUser));
    }

    /**
     * 审核申诉
     */
    @PostMapping("/appeal/review")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewAppeal(@RequestBody BanAppealReviewRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(userBanService.reviewAppeal(request, loginUser));
    }

    /**
     * 管理端查询处罚记录
     */
    @GetMapping("/list")
    @AutoCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<UserBanRecordVO>> listForAdmin(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<UserBanRecordVO> list = userBanService.listForAdmin(userId, status, limit).stream()
                .map(userBanService::toVO)
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    // ---------------------------------------------------------------- 用户端

    /**
     * 查看自己的处罚记录
     */
    @GetMapping("/my")
    public BaseResponse<List<UserBanRecordVO>> myBanRecords(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        List<UserBanRecordVO> list = userBanService.listByUser(loginUser.getId()).stream()
                .map(userBanService::toVO)
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    /**
     * 提交申诉
     */
    @PostMapping("/appeal")
    public BaseResponse<Boolean> submitAppeal(@RequestBody BanAppealRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = userBanService.submitAppeal(loginUser.getId(),
                request.getBanRecordId(), request.getContent());
        return ResultUtils.success(result);
    }
}
