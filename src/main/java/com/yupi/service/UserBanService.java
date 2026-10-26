package com.yupi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.model.dto.ban.BanAppealReviewRequest;
import com.yupi.model.dto.ban.UserBanRequest;
import com.yupi.model.dto.ban.UserUnbanRequest;
import com.yupi.model.entity.User;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.model.vo.UserBanRecordVO;

import java.util.List;

/**
 * 用户封禁服务
 */
public interface UserBanService extends IService<UserBanRecord> {

    /**
     * 封禁用户
     */
    UserBanRecord ban(UserBanRequest request, User operator);

    /**
     * 解封
     */
    boolean unban(UserUnbanRequest request, User operator);

    /**
     * 查询用户当前生效的封禁记录；没有或已过期返回 null
     */
    UserBanRecord getActiveBan(Long userId);

    /**
     * 用户是否处于封禁中
     */
    boolean isBanned(Long userId);

    /**
     * 查询某个用户的全部处罚记录（新→旧）
     */
    List<UserBanRecord> listByUser(Long userId);

    /**
     * 转为脱敏 VO
     */
    UserBanRecordVO toVO(UserBanRecord record);

    /**
     * 用户提交申诉
     */
    boolean submitAppeal(Long userId, Long banRecordId, String content);

    /**
     * 管理员审核申诉
     */
    boolean reviewAppeal(BanAppealReviewRequest request, User operator);

    /**
     * 管理端：分页查询处罚记录
     */
    List<UserBanRecord> listForAdmin(Long userId, Integer status, int limit);
}
