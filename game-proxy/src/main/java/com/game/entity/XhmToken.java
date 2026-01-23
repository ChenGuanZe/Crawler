package com.game.entity;

import lombok.Data;

/**
 * token信息
 *
 * @author 哈哈.唐
 * @date 2023-07-12 11:32
 */
@Data
public class XhmToken {

    private String url;
    private String x_app_version;
    private String x_app_anchoropenid;
    private String x_app_roomid;
    private String x_access_token;
    private String content_type;
    private String user_agent;
    private String traceId;
    private String reqToken;

}
