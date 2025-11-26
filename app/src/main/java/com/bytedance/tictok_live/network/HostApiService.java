package com.bytedance.tictok_live.network;

import com.bytedance.tictok_live.model.HostInfo;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * 主播相关API接口
 */
public interface HostApiService {

    @GET("hosts/5")
    Call<HostInfo> getHostInfo();
}
