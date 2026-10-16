package com.yupi.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.mapper.PictureReportMapper;
import com.yupi.model.dto.report.PictureReportAddRequest;
import com.yupi.model.dto.report.PictureReportHandleRequest;
import com.yupi.model.dto.report.PictureReportQueryRequest;
import com.yupi.model.entity.Picture;
import com.yupi.model.entity.PictureReport;
import com.yupi.model.entity.User;
import com.yupi.model.enums.PictureReviewStatusEnum;
import com.yupi.model.enums.ReportReasonEnum;
import com.yupi.model.enums.ReportStatusEnum;
import com.yupi.model.vo.PictureReportVO;
import com.yupi.model.vo.UserVO;
import com.yupi.service.PictureReportService;
import com.yupi.service.PictureService;
import com.yupi.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PictureReportServiceImpl extends ServiceImpl<PictureReportMapper, PictureReport>
        implements PictureReportService {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Override
    public void addReport(PictureReportAddRequest req, User loginUser) {
        ThrowUtils.throwIf(req == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = req.getPictureId();
        String reportReason = req.getReportReason();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片不存在");
        ThrowUtils.throwIf(ReportReasonEnum.getByValue(reportReason) == null,
                ErrorCode.PARAMS_ERROR, "举报原因无效");

        // 校验图片存在
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 不能重复举报同一图片（同一用户对该图片已有未处理举报时不允许）
        long count = this.lambdaQuery()
                .eq(PictureReport::getPictureId, pictureId)
                .eq(PictureReport::getUserId, loginUser.getId())
                .eq(PictureReport::getStatus, ReportStatusEnum.PENDING.getValue())
                .count();
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "你已举报过该图片，请勿重复举报");

        PictureReport report = new PictureReport();
        report.setPictureId(pictureId);
        report.setUserId(loginUser.getId());
        report.setReportReason(reportReason);
        report.setReportDetail(StrUtil.blankToDefault(req.getReportDetail(), ""));
        report.setStatus(ReportStatusEnum.PENDING.getValue());

        boolean saved = this.save(report);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
    }

    @Override
    @Transactional
    public void handleReport(PictureReportHandleRequest req, User loginUser) {
        ThrowUtils.throwIf(req == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        Long id = req.getId();
        Integer status = req.getStatus();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ReportStatusEnum statusEnum = ReportStatusEnum.getByValue(status);
        ThrowUtils.throwIf(statusEnum == null || ReportStatusEnum.PENDING.equals(statusEnum),
                ErrorCode.PARAMS_ERROR, "处理状态无效");

        // 查询举报记录
        PictureReport report = this.getById(id);
        ThrowUtils.throwIf(report == null, ErrorCode.NOT_FOUND_ERROR, "举报记录不存在");
        ThrowUtils.throwIf(!Integer.valueOf(ReportStatusEnum.PENDING.getValue()).equals(report.getStatus()),
                ErrorCode.OPERATION_ERROR, "该举报已处理");

        // 更新举报记录
        report.setStatus(status);
        report.setHandlerId(loginUser.getId());
        report.setHandleResult(StrUtil.blankToDefault(req.getHandleResult(), ""));
        report.setHandleTime(new Date());
        boolean updated = this.updateById(report);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);

        // 如果处理结果为"已下架"，同步将图片审核状态设为拒绝
        if (Integer.valueOf(ReportStatusEnum.APPROVED.getValue()).equals(status)) {
            Picture picture = new Picture();
            picture.setId(report.getPictureId());
            picture.setReviewStatus(PictureReviewStatusEnum.REJECT.getValue());
            ReportReasonEnum reasonEnum = ReportReasonEnum.getByValue(report.getReportReason());
            String reasonText = reasonEnum != null ? reasonEnum.getText() : report.getReportReason();
            picture.setReviewMessage("举报下架 - " + reasonText + " - " + StrUtil.blankToDefault(req.getHandleResult(), ""));
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            boolean picUpdated = pictureService.updateById(picture);
            ThrowUtils.throwIf(!picUpdated, ErrorCode.OPERATION_ERROR, "图片下架失败");
        }
    }

    @Override
    public QueryWrapper<PictureReport> getQueryWrapper(PictureReportQueryRequest req) {
        QueryWrapper<PictureReport> queryWrapper = new QueryWrapper<>();
        if (req == null) {
            return queryWrapper;
        }
        Long pictureId = req.getPictureId();
        Long userId = req.getUserId();
        String reportReason = req.getReportReason();
        Integer status = req.getStatus();
        Long handlerId = req.getHandlerId();
        String sortField = req.getSortField();
        String sortOrder = req.getSortOrder();

        queryWrapper.eq(ObjUtil.isNotEmpty(pictureId), "pictureId", pictureId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(StrUtil.isNotBlank(reportReason), "reportReason", reportReason);
        queryWrapper.eq(ObjUtil.isNotEmpty(status), "status", status);
        queryWrapper.eq(ObjUtil.isNotEmpty(handlerId), "handlerId", handlerId);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public PictureReportVO getReportVO(PictureReport report) {
        if (report == null) {
            return null;
        }
        PictureReportVO vo = PictureReportVO.objToVo(report);
        // 填充举报人信息
        Long userId = report.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            if (user != null) {
                vo.setReporter(userService.getUserVO(user));
            }
        }
        // 填充图片信息
        Long pictureId = report.getPictureId();
        if (pictureId != null && pictureId > 0) {
            Picture picture = pictureService.getById(pictureId);
            if (picture != null) {
                vo.setPictureUrl(picture.getUrl());
                vo.setPictureName(picture.getName());
            }
        }
        return vo;
    }

    @Override
    public Page<PictureReportVO> getReportVOPage(Page<PictureReport> page) {
        List<PictureReport> recordList = page.getRecords();
        Page<PictureReportVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (CollUtil.isEmpty(recordList)) {
            return voPage;
        }
        List<PictureReportVO> voList = recordList.stream()
                .map(PictureReportVO::objToVo)
                .collect(Collectors.toList());

        // 批量填充举报人信息
        Set<Long> userIdSet = recordList.stream()
                .map(PictureReport::getUserId)
                .collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.groupingBy(User::getId));

        // 批量填充图片信息
        Set<Long> pictureIdSet = recordList.stream()
                .map(PictureReport::getPictureId)
                .collect(Collectors.toSet());
        Map<Long, List<Picture>> pictureIdMap = pictureService.listByIds(pictureIdSet)
                .stream()
                .collect(Collectors.groupingBy(Picture::getId));

        voList.forEach(vo -> {
            Long userId = vo.getUserId();
            if (userIdUserMap.containsKey(userId)) {
                User user = userIdUserMap.get(userId).get(0);
                vo.setReporter(userService.getUserVO(user));
            }
            Long picId = vo.getPictureId();
            if (pictureIdMap.containsKey(picId)) {
                Picture picture = pictureIdMap.get(picId).get(0);
                vo.setPictureUrl(picture.getUrl());
                vo.setPictureName(picture.getName());
            }
        });
        voPage.setRecords(voList);
        return voPage;
    }
}
