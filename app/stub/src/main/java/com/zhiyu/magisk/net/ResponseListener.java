package com.zhiyu.magisk.net;

public interface ResponseListener<T> {
    void onResponse(T response);
}
