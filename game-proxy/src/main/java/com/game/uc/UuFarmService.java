package com.game.uc;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//https://m.zhyy.net/games/uu-farm?gameId=15  获取游戏h5
/*Uc浏览器-uu农场*/
@Service
@Slf4j
public class UuFarmService {

    // ---------- 全局默认配置 ----------
    private static String AES_KEY_DEFAULT = "2pc19ADACzahaPC3";
    private static String AES_IV_DEFAULT  = "QrNyZAZhGcCpZrQA";
    private static String URL_DEFAULT     = "https://m.zhyy.net/api/game/gameApis";
    private static String TOKEN_DEFAULT   = "IQ4AAGUyMGUyNDViMjg1ODY2ODhiOTA1ZjE2MDQxN2RhMWE0";

    // ---------- 动态配置 ----------
    private static String aesKey = AES_KEY_DEFAULT;
    private static String aesIv  = AES_IV_DEFAULT;
    private static String url    = URL_DEFAULT;
    private static String token  = TOKEN_DEFAULT;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 可动态修改配置，如果传 null 或空则使用默认值 */
    public static void setConfig(String newAesKey, String newAesIv, String newUrl, String newToken) {
        if (newAesKey != null && !newAesKey.isEmpty()) aesKey = newAesKey;
        if (newAesIv != null && !newAesIv.isEmpty())   aesIv  = newAesIv;
        if (newUrl != null && !newUrl.isEmpty())       url    = newUrl;
        if (newToken != null && !newToken.isEmpty())   token  = newToken;
    }

    // AES CBC 加密 -> Base64
    private static String encryptPostData(JSONObject postData) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("postData", postData);
        payload.put("timestamp", System.currentTimeMillis());

        String plain = payload.toString();

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(aesIv.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    // 调用接口
    private static JSONObject callGameApi(int gameId) throws Exception {
        JSONObject postData = new JSONObject();
        postData.put("action", "getGameOpenWinData");

        JSONObject inner = new JSONObject();
        inner.put("gameid", gameId);
        postData.put("data", inner);

        String encrypted = encryptPostData(postData);
        log.info("《uu农场》--发送请求-加密数据：{}", encrypted);

        JSONObject bodyJson = new JSONObject();
        bodyJson.put("data", encrypted);

        OkHttpClient client = new OkHttpClient.Builder().build();
        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("cookie", "token=" + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("《uu农场》--请求失败-状态码：{}---信息：{}", response.code(), response.message());
                return null;
            } else {
                String respStr = response.body().string();
                log.info("《uu农场》--获取游戏结果-{}", respStr);
                return new JSONObject(respStr);
            }
        }
    }

    // 解析数据
    private static JSONObject parseResponse(JSONObject resp) {
        JSONObject result = new JSONObject();

        if (resp == null) return result;

        int xqTimeouts = resp.getInt("XQTimeOuts", 0);
        result.put("openTime", xqTimeouts);

        JSONArray bqwinArray = resp.getJSONArray("BQwin");
        if (bqwinArray != null && !bqwinArray.isEmpty()) {
            JSONObject first = bqwinArray.getJSONObject(0);
            result.put("id", first.getInt("Pid"));
            result.put("name", first.getStr("Pname"));
        }

        return result;
    }



    public  String getGameResult(int gameId) {
        try {
            JSONObject resp = callGameApi(gameId);
            JSONObject result = parseResponse(resp);
            // 返回格式化的 JSON 字符串，也可以用 result.toString() 返回紧凑字符串
            return result.toStringPretty();
        } catch (Exception e) {
            log.error("获取游戏结果异常", e);
            return null;
        }
    }


}
