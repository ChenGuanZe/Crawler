package com.game.commom;


import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Component
public class RestTemplateUtils {

    private static RestTemplate restTemplate;

    /**
     * POST请求调用方式
     *
     * @param url          请求URL
     * @param requestBody  请求参数体
     * @param responseType 返回对象类型
     * @return ResponseEntity 响应对象封装类
     */
    public static <T> ResponseEntity<T> post(String url, Object requestBody, Class<T> responseType)
            throws RestClientException {
        return restTemplate.postForEntity(url, requestBody, responseType);
    }

    /**
     * get请求
     *
     * @param url          网址
     * @param responseType 响应类型
     * @return {@link ResponseEntity }<{@link T }>
     */
    public static <T> ResponseEntity<T> get(String url, Class<T> responseType) {
        return restTemplate.getForEntity(url, responseType);
    }

    public static <T>  ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<String> entity, Class<T> responseType) {
        return restTemplate.exchange(url, method, entity, responseType);
    }

    @Resource
    public void setRestTemplate(RestTemplate restTemplate) {
        RestTemplateUtils.restTemplate = restTemplate;
    }
}
