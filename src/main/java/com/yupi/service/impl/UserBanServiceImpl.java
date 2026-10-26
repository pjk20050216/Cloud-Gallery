package com.yupi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.mapper.UserBanRecordMapper;
import com.yupi.model.dto.ban.BanAppealReviewRequest;
import com.yupi.model.dto.ban.UserBanRequest;
import com.yupi.model.dto.ban.UserUnbanRequest;
import com.yupi.model.entity.User;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.model.enums.AppealStatusEnum;
import com.yupi.model.enums.BanStatusEnum;
import com.yupi.model.enums.BanTypeEnum;
import com.yupi.model.vo.UserBanRecordVO;
import com.yupi.service.UserBanService;
import com.yupi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户封禁服务实现
 */
@Slf4j
@Service
public class UserBanServiceImpl extends ServiceImpl<UserBanRecordMapper, UserBanRecord>
        implements UserBanService {

    @Resource
    private UserService userService;

    // ---------------------------------------------------------------- 管理端

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserBanRecord ban(UserBanRequest request, User operator) {
        ThrowUtils.throwIf(request == null || request.getUserId() == null,
                ErrorCode.PARAMS_ERROR, "用户 id 不能为空");
        ThrowUtils.throwIf(request.getUserId().equals(operator.getId()),
                ErrorCode.PARAMS_ERROR, "不能封禁自己");

        User target = userService.getById(request.getUserId());
        ThrowUtils.throwIf(target == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 已经有生效中的记录就不再重复封禁
        UserBanRecord active = getActiveBan(request.getUserId());
        ThrowUtils.throwIf(active != null, ErrorCode.OPERATION_ERROR, "该用户已处于处罚中，请先解封");

        Date now = new Date();
        UserBanRecord record = new UserBanRecord();
        record.setUserId(request.getUserId());
        record.setBanType(request.getBanType() == null ? BanTypeEnum.BAN.getValue() : request.getBanType());
        record.setReasonCode(StrUtil.blankToDefault(request.getReasonCode(), "OTHER"));
        record.setReasonDetail(StrUtil.blankToDefault(request.getReasonDetail(), "违反社区规范"));
        record.setRelatedId(request.getRelatedId());
        record.setOperatorId(operator.getId());
        record.setStartTime(now);
        if (request.getDurationDays() != null && request.getDurationDays() > 0) {
            record.setEndTime(new Date(now.getTime() + TimeUnit.DAYS.toMillis(request.getDurationDays())));
        }
        record.setStatus(BanStatusEnum.ACTIVE.getValue());
        record.setAppealStatus(AppealStatusEnum.NONE.getValue());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        boolean saved = this.save(record);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "封禁失败");

        log.info("用户 {} 被 {} 处罚，原因：{}", request.getUserId(), operator.getId(), record.getReasonDetail());
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unban(UserUnbanRequest request, User operator) {
        ThrowUtils.throwIf(request == null || request.getBanRecordId() == null,
                ErrorCode.PARAMS_ERROR, "记录 id 不能为空");
        UserBanRecord record = this.getById(request.getBanRecordId());
        ThrowUtils.throwIf(record == null, ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        ThrowUtils.throwIf(!Integer.valueOf(BanStatusEnum.ACTIVE.getValue()).equals(record.getStatus()),
                ErrorCode.OPERATION_ERROR, "该记录不是生效状态");

        UserBanRecord update = new UserBanRecord();
        update.setId(record.getId());
        update.setStatus(BanStatusEnum.RELEASED.getValue());
        update.setUnbanReason(StrUtil.blankToDefault(request.getUnbanReason(), "管理员解封"));
        update.setUpdateTime(new Date());
        boolean result = this.updateById(update);
        log.info("记录 {} 被 {} 解封", record.getId(), operator.getId());
        return result;
    }

    @Override
    public List<UserBanRecord> listForAdmin(Long userId, Integer status, int limit) {
        return this.list(new LambdaQueryWrapper<UserBanRecord>()
                .eq(userId != null, UserBanRecord::getUserId, userId)
                .eq(status != null, UserBanRecord::getStatus, status)
                .orderByDesc(UserBanRecord::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    // ---------------------------------------------------------------- 用户端

    @Override
    public UserBanRecord getActiveBan(Long userId) {
        if (userId == null) {
            return null;
        }
        UserBanRecord record = this.getOne(new LambdaQueryWrapper<UserBanRecord>()
                .eq(UserBanRecord::getUserId, userId)
                .eq(UserBanRecord::getStatus, BanStatusEnum.ACTIVE.getValue())
                .orderByDesc(UserBanRecord::getCreateTime)
                .last("limit 1"));
        if (record == null) {
            return null;
        }
        // 到期的记录顺手标记为已过期
        if (record.getEndTime() != null && record.getEndTime().before(new Date())) {
            UserBanRecord update = new UserBanRecord();
            update.setId(record.getId());
            update.setStatus(BanStatusEnum.EXPIRED.getValue());
            update.setUpdateTime(new Date());
            this.updateById(update);
            return null;
        }
        return record;
    }

    @Override
    public boolean isBanned(Long userId) {
        return getActiveBan(userId) != null;
    }

    @Override
    public List<UserBanRecord> listByUser(Long userId) {
        return this.list(new LambdaQueryWrapper<UserBanRecord>()
                .eq(UserBanRecord::getUserId, userId)
                .orderByDesc(UserBanRecord::getCreateTime));
    }

    @Override
    public UserBanRecordVO toVO(UserBanRecord record) {
        if (record == null) {
            return null;
        }
        return UserBanRecordVO.builder()
                .banRecordId(record.getId())
                .banType(record.getBanType())
                .banTypeText(BanTypeEnum.getTextByValue(record.getBanType()))
                .reasonCode(record.getReasonCode())
                .reasonDetail(record.getReasonDetail())
                .relatedId(record.getRelatedId())
                .startTime(record.getStartTime())
                .endTime(record.getEndTime())
                .permanent(record.getEndTime() == null)
                .status(record.getStatus())
                .statusText(record.getStatus() == null ? "" : statusText(record.getStatus()))
                .appealStatus(record.getAppealStatus())
                .appealStatusText(AppealStatusEnum.getTextByValue(record.getAppealStatus()))
                .appealContent(record.getAppealContent())
                .appealReply(record.getAppealReply())
                .appealTime(record.getAppealTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitAppeal(Long userId, Long banRecordId, String content) {
        ThrowUtils.throwIf(banRecordId == null, ErrorCode.PARAMS_ERROR, "记录 id 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "申诉理由不能为空");
        ThrowUtils.throwIf(content.length() > 1000, ErrorCode.PARAMS_ERROR, "申诉理由过长");

        UserBanRecord record = this.getById(banRecordId);
        ThrowUtils.throwIf(record == null, ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        ThrowUtils.throwIf(!userId.equals(record.getUserId()), ErrorCode.NO_AUTH_ERROR, "只能申诉自己的记录");
        ThrowUtils.throwIf(!Integer.valueOf(BanStatusEnum.ACTIVE.getValue()).equals(record.getStatus()),
                ErrorCode.OPERATION_ERROR, "该处罚已结束，无需申诉");
        ThrowUtils.throwIf(Integer.valueOf(AppealStatusEnum.PENDING.getValue()).equals(record.getAppealStatus()),
                ErrorCode.OPERATION_ERROR, "申诉已提交，请等待处理");

        UserBanRecord update = new UserBanRecord();
        update.setId(banRecordId);
        update.setAppealStatus(AppealStatusEnum.PENDING.getValue());
        update.setAppealContent(content);
        update.setAppealTime(new Date());
        update.setUpdateTime(new Date());
        return this.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewAppeal(BanAppealReviewRequest request, User operator) {
        ThrowUtils.throwIf(request == null || request.getBanRecordId() == null,
                ErrorCode.PARAMS_ERROR, "记录 id 不能为空");
        UserBanRecord record = this.getById(request.getBanRecordId());
        ThrowUtils.throwIf(record == null, ErrorCode.NOT_FOUND_ERROR, "记录不存在");
        ThrowUtils.throwIf(!Integer.valueOf(AppealStatusEnum.PENDING.getValue()).equals(record.getAppealStatus()),
                ErrorCode.OPERATION_ERROR, "该记录没有待处理的申诉");

        boolean approve = Boolean.TRUE.equals(request.getApprove());
        UserBanRecord update = new UserBanRecord();
        update.setId(record.getId());
        update.setAppealStatus(approve
                ? AppealStatusEnum.APPROVED.getValue()
                : AppealStatusEnum.REJECTED.getValue());
        update.setAppealReply(StrUtil.blankToDefault(request.getReply(),
                approve ? "申诉通过，已解封" : "申诉驳回"));
        update.setUpdateTime(new Date());

        if (approve) {
            // 申诉通过直接解封
            update.setStatus(BanStatusEnum.RELEASED.getValue());
            update.setUnbanReason("申诉通过，管理员解封");
        }
        boolean result = this.updateById(update);
        log.info("申诉 {} 被 {} 处理，结果：{}", record.getId(), operator.getId(), approve ? "通过" : "驳回");
        return result;
    }

    private String statusText(Integer status) {
        for (BanStatusEnum anEnum : BanStatusEnum.values()) {
            if (anEnum.getValue() == status) {
                return anEnum.getText();
            }
        }
        return "";
    }

    /**
     * 拼一句给用户看的封禁说明，供异常提示使用
     */
    public String buildBanMessage(UserBanRecord record) {
        if (record == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(BanTypeEnum.getTextByValue(record.getBanType()))
                .append("原因：").append(StrUtil.blankToDefault(record.getReasonDetail(), "违反社区规范"));
        if (record.getEndTime() == null) {
            sb.append("；解封时间：永久");
        } else {
            sb.append("；解封时间：")
                    .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(record.getEndTime()));
        }
        return sb.toString();
    }
}
