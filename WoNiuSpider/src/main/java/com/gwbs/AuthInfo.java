package com.gwbs;

public class AuthInfo {
    final String uid;
    final String bearerToken;
    AuthInfo(String uid, String bearerToken) {
        this.uid = uid;
        this.bearerToken = bearerToken;
    }
}
