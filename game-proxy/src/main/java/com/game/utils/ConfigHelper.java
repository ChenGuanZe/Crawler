//package com.game.utils;
//
//import jakarta.annotation.PostConstruct;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//
//@Component
//@Order(1) //很关键
//public class AppConfig  {
//
//    @Value("${test.enable}")
//    private Boolean testEnabled;
//
//    // 静态变量保存配置值
//    public static Boolean TEST_ENABLED;
//
//    @PostConstruct
//    public void getValue() {
//        TEST_ENABLED = testEnabled;
//    }
//}
