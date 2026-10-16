package com.yupi.utils;

import java.awt.*;

/**
 * 颜色转换工具类
 */
public class ColorTransformUtils {

    private ColorTransformUtils() {
        // 工具类不需要实例化  
    }

    // 获取标准颜色，将有一些图片的 picColor 的 5 位转为 6 位，因为数据万象服务的标准为 6 位
    public static String getStandardColor(String color) {
        // 0x080e0 -> 0x080e00
        if (color.length() == 7) {
            color = color.substring(0, 4) + "0" + color.substring(4, 7);
        }
        return color;
    }
}

