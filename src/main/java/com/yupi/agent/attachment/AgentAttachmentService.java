package com.yupi.agent.attachment;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.configuration.CosClientConfig;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.manager.CosManager;
import com.yupi.mapper.AgentAttachmentMapper;
import com.yupi.model.entity.AgentAttachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 智能体附件服务。
 * <p>
 * 上传后落到 COS 的临时目录，供前端预览、也便于跨重启保留；
 * 真正入图库时再下载到本地临时文件，走与普通上传完全一致的
 * {@code PictureService.uploadPicture(MultipartFile, ...)} 链路
 * （这样可以绕开 UrlPictureUpload 的 2MB 限制，并复用 10MB/后缀/审核等全部校验）。
 */
@Slf4j
@Service
public class AgentAttachmentService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final List<String> ALLOW_SUFFIX = Arrays.asList("jpeg", "png", "jpg", "webp");

    private static final String STATUS_UPLOADED = "UPLOADED";

    private static final String STATUS_CONSUMED = "CONSUMED";

    @Resource
    private AgentAttachmentMapper agentAttachmentMapper;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 上传附件到 COS 临时目录
     */
    public AgentAttachment upload(Long userId, MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "文件为空");
        ThrowUtils.throwIf(multipartFile.getSize() > MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过 10MB");

        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = StrUtil.nullToEmpty(FileUtil.getSuffix(originalFilename)).toLowerCase();
        ThrowUtils.throwIf(!ALLOW_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR,
                "仅支持 jpeg/png/jpg/webp 格式");

        String cosKey = String.format("agent/temp/%s/%s_%s.%s",
                userId, System.currentTimeMillis(), RandomUtil.randomString(8), suffix);

        File tempFile = null;
        try {
            tempFile = File.createTempFile("agent-attach-", "." + suffix);
            multipartFile.transferTo(tempFile);
            cosManager.putObject(cosKey, tempFile);
        } catch (Exception e) {
            log.error("附件上传 COS 失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "附件上传失败");
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile);
            }
        }

        AgentAttachment attachment = new AgentAttachment();
        attachment.setUserId(userId);
        attachment.setUrl(StrUtil.removeSuffix(cosClientConfig.getHost(), "/") + "/" + cosKey);
        attachment.setCosKey(cosKey);
        attachment.setFileName(StrUtil.blankToDefault(originalFilename, "attachment." + suffix));
        attachment.setContentType(multipartFile.getContentType());
        attachment.setFileSize(multipartFile.getSize());
        attachment.setStatus(STATUS_UPLOADED);
        attachment.setCreateTime(new Date());
        agentAttachmentMapper.insert(attachment);
        return attachment;
    }

    /**
     * 按 id 取附件，且必须是当前用户的
     */
    public AgentAttachment getOwned(Long userId, Long attachmentId) {
        if (attachmentId == null) {
            return null;
        }
        return agentAttachmentMapper.selectOne(new LambdaQueryWrapper<AgentAttachment>()
                .eq(AgentAttachment::getId, attachmentId)
                .eq(AgentAttachment::getUserId, userId));
    }

    /**
     * 下载附件到本地临时文件，供上传链路使用。调用方负责删除。
     */
    public File downloadToTemp(AgentAttachment attachment) {
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(attachment.getFileName()), "jpg");
        try {
            File tempFile = File.createTempFile("agent-consume-", "." + suffix);
            HttpUtil.downloadFile(attachment.getUrl(), tempFile);
            return tempFile;
        } catch (Exception e) {
            log.error("附件下载失败，attachmentId={}", attachment.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "附件读取失败，请重新上传");
        }
    }

    public void markConsumed(Long attachmentId) {
        AgentAttachment update = new AgentAttachment();
        update.setId(attachmentId);
        update.setStatus(STATUS_CONSUMED);
        agentAttachmentMapper.updateById(update);
    }

    /**
     * 删除 COS 上的临时对象（入图库后原附件已无用途）
     */
    public void deleteCosObject(AgentAttachment attachment) {
        if (attachment == null || StrUtil.isBlank(attachment.getCosKey())) {
            return;
        }
        try {
            cosManager.deleteObject(attachment.getCosKey());
        } catch (Exception e) {
            // 清理失败不影响主流程
            log.warn("删除附件 COS 对象失败：{}", attachment.getCosKey());
        }
    }
}
