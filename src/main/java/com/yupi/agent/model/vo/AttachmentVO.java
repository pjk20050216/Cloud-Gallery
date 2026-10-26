package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 附件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentVO implements Serializable {

    private Long attachmentId;

    private String url;

    private String fileName;

    private Long fileSize;

    private static final long serialVersionUID = 1L;
}
