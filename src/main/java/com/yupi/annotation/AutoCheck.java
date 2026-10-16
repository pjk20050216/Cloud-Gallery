package com.yupi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


// 注解类用于管理用户是哪个角色
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoCheck {

    // 设置用户角色
    String mustRole() default ""; // default 设置默认值
}
