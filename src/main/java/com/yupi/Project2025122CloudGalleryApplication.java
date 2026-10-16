package com.yupi;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = ShardingSphereAutoConfiguration.class) // 移除分库分表的配置类的加载
@EnableAsync
@MapperScan("com.yupi.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class Project2025122CloudGalleryApplication {

    public static void main(String[] args) {
        SpringApplication.run(Project2025122CloudGalleryApplication.class, args);
    }

}
