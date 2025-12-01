package com.bytedance.tictok_live.constant;

/**
 * 直播业务常量（统一管理）
 */
public class BusinessConstant {
    // 私有化构造方法，避免被实例化
    private BusinessConstant() {}

    // 评论最大长度
    public static final int COMMENT_MAX_LENGTH = 128;

    // 约定在线人数加1触发消息
    public static final String ONLINE_COUNT_INCREASE_MSG = "online_increase";

    // 直播 DASH 源
    public static final String LIVE_DASH_URL = "https://akamaibroadcasteruseast.akamaized.net/cmaf/live/657078/akasource/out.mpd";

    // http地址
    public static final String BASE_URL = "https://692415ae3ad095fb84729fff.mockapi.io/api/v1/";

    // 初始在线人数
    public static final Integer ONLINE_COUNT_INIT_VALUE = 100;


}
