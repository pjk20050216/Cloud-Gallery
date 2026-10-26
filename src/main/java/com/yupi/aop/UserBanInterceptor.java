package com.yupi.aop;

import cn.hutool.core.util.StrUtil;
import com.yupi.constant.UserConstant;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.model.entity.User;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.service.UserBanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 封禁拦截器。
 * <p>
 * 被封禁的用户仍然可以登录、查看自己的信息、以及**使用智能体对话**
 * （否则连"我为什么被封"都问不了）；
 * 但访问其余业务接口（上传、删除、空间等）会被拦下。
 * <p>
 * 放行规则在 {@code WebMvcConfig} 里配置。
 */
@Slf4j
@Component
public class UserBanInterceptor implements HandlerInterceptor {

    @Resource
    private UserBanService userBanService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object userObj = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(userObj instanceof User)) {
            // 未登录交给后续逻辑处理
            return true;
        }
        User loginUser = (User) userObj;
        if (loginUser.getId() == null) {
            return true;
        }

        UserBanRecord activeBan = userBanService.getActiveBan(loginUser.getId());
        if (activeBan == null) {
            return true;
        }

        String message = buildMessage(activeBan);
        log.info("用户 {} 处于封禁中，拦截请求 {}", loginUser.getId(), request.getRequestURI());
        throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, message);
    }

    private String buildMessage(UserBanRecord record) {
        StringBuilder sb = new StringBuilder("账号已被封禁，暂时无法执行该操作。");
        if (StrUtil.isNotBlank(record.getReasonDetail())) {
            sb.append("原因：").append(record.getReasonDetail()).append("。");
        }
        if (record.getEndTime() == null) {
            sb.append("解封时间：永久。");
        } else {
            sb.append("解封时间：")
                    .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(record.getEndTime()))
                    .append("。");
        }
        sb.append("你可以通过智能体助手查询详情或提交申诉。");
        return sb.toString();
    }
}
