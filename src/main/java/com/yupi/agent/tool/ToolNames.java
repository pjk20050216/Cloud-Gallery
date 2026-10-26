package com.yupi.agent.tool;

/**
 * 工具名常量，避免各处硬编码字符串写错。
 */
public final class ToolNames {

    private ToolNames() {
    }

    // 通用
    public static final String HELP_LIST_CAPABILITIES = "help.listCapabilities";
    public static final String HANDOFF = "agent.handoff";

    // 图片
    public static final String PICTURE_SEARCH = "picture.search";
    public static final String PICTURE_GET_DETAIL = "picture.getDetail";
    public static final String PICTURE_UPLOAD_BY_URL = "picture.uploadByUrl";
    public static final String PICTURE_UPLOAD_FROM_ATTACHMENT = "picture.uploadFromAttachment";
    public static final String PICTURE_EDIT = "picture.edit";
    public static final String PICTURE_DELETE = "picture.delete";
    public static final String PICTURE_BATCH_UPLOAD = "picture.batchUpload";

    // 空间
    public static final String SPACE_LIST_MINE = "space.listMine";

    // 账号
    public static final String ACCOUNT_MY_STATUS = "account.myStatus";
    public static final String ACCOUNT_MY_BAN_RECORDS = "account.myBanRecords";
    public static final String ACCOUNT_SUBMIT_APPEAL = "account.submitAppeal";

    // 异步任务
    public static final String TASK_GET_STATUS = "task.getStatus";
}
