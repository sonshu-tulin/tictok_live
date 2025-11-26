package com.bytedance.tictok_live.network;

import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.model.HostInfo;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

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
     * @return 评论实体
     */
    @GET("comments")
    Call<List<Comment>> getComments();

}
