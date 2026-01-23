package com.game.entity;

import lombok.Data;

/**
 * 怪物捕手token信息
 *
 * @author 哈哈.唐
 * @date 2023-07-04 18:17
 */
@Data
public class GwbsToken {

    private String url;
    private String x_app_version;
    private String x_app_anchoropenid;
    private String x_app_roomid;
    private String x_access_token;
    private String content_type;
    private String user_agent;
}
