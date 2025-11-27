package com.bytedance.tictok_live.network.http;

import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.model.HostInfo;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * 主播相关API接口
 */
public interface HostApiService {

    /**
     * 获取主播信息
     * @return 主播信息实体
     */
    @GET("hosts/5")
    Call<HostInfo> getHostInfo();

    /**
     * 获取公屏评论
     * @return 评论实体列表
     */
    @GET("comments")
    Call<List<Comment>> getComments();

    /**
     * 发送评论
     * @return 评论实体
     */
    @FormUrlEncoded // 声明为x-www-form-urlencoded格式表单提交
    @POST("comments")
    Call<Comment> sendComment(@Field("comment") String content);


}
