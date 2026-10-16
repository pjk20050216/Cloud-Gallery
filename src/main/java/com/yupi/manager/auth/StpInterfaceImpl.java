package com.yupi.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.manager.auth.model.SpaceUserAuthConfig;
import com.yupi.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.model.entity.Picture;
import com.yupi.model.entity.Space;
import com.yupi.model.entity.SpaceUser;
import com.yupi.model.entity.User;
import com.yupi.model.enums.SpaceRoleEnum;
import com.yupi.model.enums.SpaceTypeEnum;
import com.yupi.service.PictureService;
import com.yupi.service.SpaceService;
import com.yupi.service.SpaceUserService;
import com.yupi.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yupi.constant.UserConstant.USER_LOGIN_STATE;

/*
1、Sa-Token 权限认证框架的核心接口实现类，
2、Sa-Token 框架的 AOP 切面或拦截器会拦截拦截带有 @SaSpaceCheckPermission 注解的请求
3、拦截到后调用这个类来动态计算并返回当前登录用户所拥的权限列表
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    // 因为我们要找到请求的是什么，所以得先找请求路径是什么（如：picture、space等）
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    /**
     * 返回一个账号所拥有的权限码集合 
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断 loginType，仅对 space 类型进行权限校验
        if(!StpKit.SPACE_TYPE.equals(loginType)){
            return new ArrayList<>();
        }

        // 管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        SpaceUserAuthContext authContext = getAuthContextByRequest();

        // 如果所有字段都为空，表示查询公共图库，可以通过
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }
        // 获取 userId，sa-token 中的方法
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        ThrowUtils.throwIf(Objects.isNull(loginUser), ErrorCode.NOT_FOUND_ERROR, "未找到用户信息");
        Long userId = loginUser.getId();
        // 优先从上下文中获取 SpaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 如果有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            ThrowUtils.throwIf(Objects.isNull(spaceUser), ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            // 取出当前登录用户对应的 spaceUser
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getUserId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();

            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        // 这种情况可能为：公共图库或者没有传入 spaceId
        if (spaceId == null) {
            // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space 对象
            Long pictureId = authContext.getPictureId();

            // 图片 id 也没有，则默认通过权限校验
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                            .eq(Picture::getId, pictureId)
                            .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                            .one();

            ThrowUtils.throwIf(Objects.isNull(picture), new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息"));
            spaceId = picture.getSpaceId();

            // 这种则为公共图库，仅本人或管理员可操作
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // 获取 Space 对象
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(Objects.isNull(space), ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    /**
     * 本项目中还用不到，返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
       return new ArrayList<>();
    }

    // 从请求中获取上下文对象
    private SpaceUserAuthContext getAuthContextByRequest(){
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue()); // 获取请求中的内容类型
        SpaceUserAuthContext authRequest;

        // 获取请求内容
        if(ContentType.JSON.getValue().equals(contentType)){ // 判断请求参数是不是 json
            String body = ServletUtil.getBody(request); // 拿到所有请求体
            // 如果 body 为 json 格式数据则直接转换然后返回
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);

        } else { // 为 get 请求，没有 json 对象
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 根据请求路径区分 id 字段的含义（因为有的为 spaceId，有的为 pictureId）
        Long id = authRequest.getId();
        if(ObjectUtil.isNotNull(id)){

            // 获取请求的业务前缀，就是从 api/picture/··· 中获取 picture
            String requestURI = request.getRequestURI();
            // 先替换掉 api ，剩下的就是前缀
            String partURI = requestURI.replace(contextPath + "/", "");
            // 然后获取编辑后的第一个字符串
            String moduleName = StrUtil.subBefore(partURI, "/", false);

            switch (moduleName){
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

    // 判断对象的所以字段是否为空
    private boolean isAllFieldsNull(Object object){
        if(object == null){
            return true;
        }
        // 获取所有字段，并判断是否所有字段都为空

        return Arrays.stream(ReflectUtil.getFields(object.getClass())) // 利用反射获取这个对象的所有字段信息
                // 然后利用反射获取字段中的值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 判断是否所有字段为空，是的话返回 true
                .allMatch(ObjectUtil::isEmpty);

    }
}
