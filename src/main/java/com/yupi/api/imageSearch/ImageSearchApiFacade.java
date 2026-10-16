package com.yupi.api.imageSearch;


import com.yupi.api.imageSearch.model.ImageSearchResult;
import com.yupi.api.imageSearch.sub.GetImageFirstUrlApi;
import com.yupi.api.imageSearch.sub.GetImageListApi;
import com.yupi.api.imageSearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /*
     组合前面的三个方法
     实现以图搜图
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        // 1、获取百度的以图搜图地址
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        // 2、获取相关图片的 api
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        List<ImageSearchResult> list = searchImage("as");
        System.out.println("结果列表：" + list);
    }
}
