# 鉴权漏洞修复

> 关联：[P4 封禁体系](./P4_IMPLEMENTATION.md)　发现于多智能体改造期间的安全审查

---

## 摘要

排查中发现 **3 个真实存在的鉴权漏洞**，其中 1 个是致命的（任意登录用户可改删他人图片）。
全部已修复并验证。

| # | 漏洞 | 危害 | 状态 |
| --- | --- | --- | --- |
| 1 | Sa-Token 拦截器路径写错 | 所有 `@SaCheckLogin` / `@SaSpaceCheckPermission` 注解形同虚设 | ✅ 已修 |
| 2 | `StpInterfaceImpl` 的 Content-Type 判断用 `equals` | 带 charset 的 JSON 请求直接放行 ADMIN 权限 | ✅ 已修 |
| 3 | `GlobalExceptionHandler` 参数类型写错 | 无权限时返回 500「系统错误」而非 40101 | ✅ 已修 |

---

## 漏洞 1：拦截器路径永远匹配不到

### 问题

```java
// SaTokenConfigure.java（修复前）
registry.addInterceptor(new SaInterceptor())
        .addPathPatterns("/api/**");   // ❌
```

项目 `server.servlet.context-path` 是 `/api`，而 Spring 的拦截器路径是
**相对 context-path** 匹配的（用 `getLookupPathForRequest`，返回的是 servlet 内路径）。
所以 `/api/**` 永远匹配不到 `/spaceUser/add` 这种真实路径。

### 危害

12 个带 `@SaSpaceCheckPermission` 的接口**完全没有鉴权**。其中
`SpaceUserController.addSpaceUser` 内部没有任何其他权限校验——
**未登录用户可以直接往任意空间加成员**。

### 验证（修复前）

```bash
curl -X POST http://localhost:8082/api/spaceUser/add \
     -H 'Content-Type: application/json' -d '{}'
# 返回 {"code":40000,"message":"请求参数错误"}
# ↑ 说明请求已经进入业务逻辑，而不是被鉴权拦下
```

### 修复

```java
registry.addInterceptor(new SaInterceptor())
        .addPathPatterns("/**")
        .excludePathPatterns("/agent-ui/**");
```

### 验证（修复后）

```bash
curl -X POST http://localhost:8082/api/spaceUser/add \
     -H 'Content-Type: application/json' -d '{}'
# 返回 {"code":40100,"message":"未能读取到有效 token"}
# ↑ Sa-Token 拦下了
```

> 判据：Sa-Token 的 `NotLoginException` 消息是「未能读取到有效 token」，
> 而项目自己的 `userService.getLoginUser` 抛的是「未登录」。
> 靠这个消息差异可以区分是「拦截器拦的」还是「业务代码拦的」。

---

## 漏洞 2：Content-Type 判断导致越权（最严重）

### 问题

```java
// StpInterfaceImpl.getAuthContextByRequest()（修复前）
if (ContentType.JSON.getValue().equals(contentType)) {   // ❌ equals
    authRequest = JSONUtil.toBean(ServletUtil.getBody(request), SpaceUserAuthContext.class);
} else {
    Map<String, String> paramMap = ServletUtil.getParamMap(request);
    authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
}
```

实际请求头常见的是 `application/json; charset=utf-8`，`equals("application/json")` 为 **false**。

于是走 else 分支读 `paramMap`——POST 请求没有 query 参数，得到空的 `SpaceUserAuthContext`。
接着：

```java
if (isAllFieldsNull(authContext)) {
    return ADMIN_PERMISSIONS;   // 所有字段为空 = 认为是公共图库查询 = 放行全部权限
}
```

**结果：任何带 charset 的 JSON POST 请求，都被判定为"公共图库查询"，直接拿到管理员权限。**

### 危害

普通用户可以对**任意图片**执行 edit / delete。

### 验证（修复前）

```bash
# 普通用户 agenttest2（userRole=user）编辑别人的图片
curl -b cookie2.txt -X POST http://localhost:8082/api/picture/edit \
     -H 'Content-Type: application/json; charset=utf-8' \
     -d '{"id":1999444332930932737,"name":"hacked"}'
# 返回 {"code":0,"data":true}  ← 越权成功

# 图片名真的被改了
```

### 修复

```java
private boolean isJsonRequest(String contentType) {
    if (StrUtil.isBlank(contentType)) {
        return false;
    }
    return StrUtil.startWithIgnoreCase(contentType.trim(), ContentType.JSON.getValue());
}
```

同时把 `body` 为空的情况也处理掉（避免 `JSONUtil.toBean("")` 得到 null）：

```java
authRequest = StrUtil.isBlank(body)
        ? new SpaceUserAuthContext()
        : JSONUtil.toBean(body, SpaceUserAuthContext.class);
```

### 验证（修复后）

```bash
curl -b cookie2.txt -X POST http://localhost:8082/api/picture/edit \
     -H 'Content-Type: application/json; charset=utf-8' \
     -d '{"id":1999444332930932737,"name":"hacked"}'
# 返回 {"code":40101,"message":"无此权限：picture:edit"}  ✅
```

---

## 漏洞 3：异常处理器参数类型错误

### 问题

```java
// GlobalExceptionHandler（修复前）
@ExceptionHandler(NotPermissionException.class)
public BaseResponse<?> notPermissionException(NotLoginException e) {   // ❌ 类型不匹配
```

注解声明捕获 `NotPermissionException`，但方法参数是 `NotLoginException`。
Spring 找不到合适的参数解析器，抛：

```
java.lang.IllegalStateException: Could not resolve parameter [0] in
  ...notPermissionException(cn.dev33.satoken.exception.NotLoginException): No suitable resolver
```

最终被 `RuntimeException` 兜底成 **500「系统错误」**，用户看不到"无权限"的提示。

### 修复

```java
@ExceptionHandler(NotPermissionException.class)
public BaseResponse<?> notPermissionException(NotPermissionException e) {
    return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
}
```

顺带补上 Sa-Token 其他常见异常（`NotRoleException` / `NotSafeException` / `DisableServiceException`），
避免它们也落到 500。

---

## 修复过程中的回归

修完漏洞 1 后，`/picture/get/vo` 匿名访问变成了「未登录」——
但这个方法本来就是设计成允许匿名访问的（代码注释写明"如果这里使用 sa-token 则只能登录才能查看图片，所以不行"）。

原因：方法末尾无条件调用了 `userService.getLoginUser(request)` 来取权限列表。

修复：

```java
User loginUser = null;
try {
    loginUser = userService.getLoginUser(request);
} catch (BusinessException e) {
    // 未登录，忽略
}
List<String> permissionList = loginUser == null
        ? new ArrayList<>()
        : spaceUserAuthManager.getPermissionList(space, loginUser);
```

匿名用户能看图片，只是 `permissionList` 为空。

---

## 最终验证矩阵

| 场景 | 预期 | 结果 |
| --- | --- | --- |
| 匿名看公共图库图片详情 | 放行 | ✅ `code=0` |
| 匿名看图片列表 | 放行 | ✅ `code=0` |
| 匿名调 `/spaceUser/add` | 拒绝 | ✅ `40100 未能读取到有效 token` |
| 普通用户编辑他人图片 | 拒绝 | ✅ `40101 无此权限：picture:edit` |
| 普通用户删除他人图片 | 拒绝 | ✅ `40101 无此权限：picture:delete` |
| 普通用户看他人图片详情 | 放行（只读） | ✅ `permissionList=["picture:view"]` |
| 智能体接口 | 不受影响 | ✅ 正常返回 4 个智能体 |
| 智能体前端静态页 | 正常 | ✅ `HTTP 200` |
| 图片数据完整性 | 未被篡改 | ✅ `name="2"` |

全量测试：`Tests run: 32, Failures: 0, Errors: 0  BUILD SUCCESS`

---

## 修改文件

| 文件 | 改动 |
| --- | --- |
| `SaTokenConfigure.java` | `/api/**` → `/**`，排除 `/agent-ui/**` |
| `StpInterfaceImpl.java` | Content-Type 改用前缀匹配；空 body 兜底 |
| `GlobalExceptionHandler.java` | 修正 `notPermissionException` 参数类型；补 3 个 Sa-Token 异常 |
| `PictureController.java` | `getPictureVOById` 支持匿名访问 |

---

## 教训

1. **路径模式要看清 context-path**。拦截器、过滤器、静态资源路径都是相对 context-path 的。
2. **Content-Type 不要用 `equals`**。永远用 `startsWith` 或 `MediaType.includes()`，因为会带 charset。
3. **异常处理器的参数类型必须和 `@ExceptionHandler` 声明一致**，否则静默失败变成 500。
4. **"安全兜底放行"要谨慎**。`isAllFieldsNull → ADMIN_PERMISSIONS` 这个设计，
   一旦上游解析失败就变成全开放。更安全的做法是**失败即拒绝**。
5. **改鉴权一定要做回归**。这次修完立刻发现匿名访问图片详情挂了。

> 第 4 点可以考虑后续改进：把「解析不出上下文」从"放行"改成"拒绝"，
> 只对明确的公共图库查询路径放行。但这会影响现有公共图库的读取逻辑，需要单独评估。
