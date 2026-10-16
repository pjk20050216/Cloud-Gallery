package com.yupi.model.dto.report;

import com.yupi.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class PictureReportQueryRequest extends PageRequest implements Serializable {

    private Long pictureId;

    private Long userId;

    private String reportReason;

    private Integer status;

    private Long handlerId;

    private static final long serialVersionUID = 1L;
}
