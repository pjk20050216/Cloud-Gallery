package com.yupi.model.dto.user;


import lombok.Data;

import java.io.Serializable;

// 用户登录请求
@Data
public class UserLoginRequest implements Serializable {
    /*
    Serializable：标识这个类可以序列化
    序列化：就是将 java 对象转为字节流，能够进行储存和传输
     */
    private static final long serialVersionUID = -8699396486670585535L;

    private String userAccount;

    private String userPassword;

}
