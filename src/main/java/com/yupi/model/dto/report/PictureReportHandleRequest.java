package com.yupi.model.dto.report;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureReportHandleRequest implements Serializable {

    private Long id;

    private Integer status;

    private String handleResult;

    private static final long serialVersionUID = 1L;
}
