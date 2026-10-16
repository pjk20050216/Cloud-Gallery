package com.yupi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.model.dto.report.PictureReportAddRequest;
import com.yupi.model.dto.report.PictureReportHandleRequest;
import com.yupi.model.dto.report.PictureReportQueryRequest;
import com.yupi.model.entity.PictureReport;
import com.yupi.model.entity.User;
import com.yupi.model.vo.PictureReportVO;

public interface PictureReportService extends IService<PictureReport> {

    void addReport(PictureReportAddRequest req, User loginUser);

    void handleReport(PictureReportHandleRequest req, User loginUser);

    QueryWrapper<PictureReport> getQueryWrapper(PictureReportQueryRequest req);

    PictureReportVO getReportVO(PictureReport report);

    Page<PictureReportVO> getReportVOPage(Page<PictureReport> page);
}
