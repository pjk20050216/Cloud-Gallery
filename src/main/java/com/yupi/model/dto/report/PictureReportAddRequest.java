package com.yupi.model.dto.report;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureReportAddRequest implements Serializable {

    private Long pictureId;

    private String reportReason;

    private String reportDetail;

    private static final long serialVersionUID = 1L;
}
