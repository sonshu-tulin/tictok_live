package com.bytedance.tictok_live.network.http;

import androidx.media3.common.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit工具类，单例模式，统一配置网络请求
 */
public class RetrofitClient {
    public static final String BASE_URL = "https://692415ae3ad095fb84729fff.mockapi.io/api/v1/";
    private static volatile Retrofit retrofit;
    public static final String HTTP_LOG_TAG = "LiveApp_Http";

    //单例模式：获取Retrofit实例(双重检查锁)
    public static Retrofit getInstance(){
        if (retrofit == null){
            synchronized (RetrofitClient.class){
                if (retrofit == null){
                    // 1. 配置日志拦截器
                    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(
                            message -> Log.d(HTTP_LOG_TAG, message)
                    );

                    // 2. 构建 OkHttpClient
                    OkHttpClient okHttpClient = new OkHttpClient.Builder()
                            .addInterceptor(loggingInterceptor)
                            .build();

                    // 3. 初始化Retrofit
                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(okHttpClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }

    //泛型方法：创建API接口实例
    public static <T> T createApi(Class<T> serviceClass){
        return getInstance().create(serviceClass);
    }
}
