package com.yupi.manager.auth.annotation;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.yupi.manager.auth.StpKit;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
空间权限认证必须具有指定权限才能调用 controller 中的接口
应用了 Sa-Token
 */
@SaCheckPermission(type = StpKit.SPACE_TYPE)
@Retention(RetentionPolicy.RUNTIME) // 表示改注解在程序运行时有效
@Target({ ElementType.METHOD, ElementType.TYPE}) // 表示改注解可以用于方法上，也可以用于类上
public @interface SaSpaceCheckPermission {

	/*
	 需要校验的权限码
	  @return 需要校验的权限码
	 */
	@AliasFor(annotation = SaCheckPermission.class)
	String [] value() default {};

	/*
	  验证模式：AND | OR，默认AND
	  @return 验证模式
	 */
	@AliasFor(annotation = SaCheckPermission.class)
	SaMode mode() default SaMode.AND;

	/*
	 在权限校验不通过时的次要选择，两者只要其一校验成功即可通过校验

	  <p>
	  	例1：@SaCheckPermission(value="user-add", orRole="admin")，
	  	代表本次请求只要具有 user-add权限 或 admin角色 其一即可通过校验。
	  </p>

	  <p>
	  	例2： orRole = {"admin", "manager", "staff"}，具有三个角色其一即可。 <br>
	  	例3： orRole = {"admin, manager, staff"}，必须三个角色同时具备。
	  </p>

	  @return /
	 */
	@AliasFor(annotation = SaCheckPermission.class)
	String[] orRole() default {};

}
