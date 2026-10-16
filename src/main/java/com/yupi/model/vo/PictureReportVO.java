package com.yupi.model.vo;

import com.yupi.model.entity.PictureReport;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class PictureReportVO implements Serializable {

    private Long id;

    private Long pictureId;

    private Long userId;

    private String reportReason;

    private String reportDetail;

    private Integer status;

    private Long handlerId;

    private String handleResult;

    private Date handleTime;

    private Date createTime;

    private UserVO reporter;

    private String pictureUrl;

    private String pictureName;

    private static final long serialVersionUID = 1L;

    public static PictureReportVO objToVo(PictureReport pictureReport) {
        if (pictureReport == null) {
            return null;
        }
        PictureReportVO vo = new PictureReportVO();
        BeanUtils.copyProperties(pictureReport, vo);
        return vo;
    }
}
