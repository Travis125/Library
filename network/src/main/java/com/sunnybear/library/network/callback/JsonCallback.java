package com.sunnybear.library.network.callback;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.sunnybear.library.network.NetworkConfiguration;
import com.sunnybear.library.network.R;
import com.sunnybear.library.network.util.JsonUtils;
import com.sunnybear.library.util.Logger;
import com.sunnybear.library.util.ResourcesUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 回调转换成json
 * Created by guchenkai on 2016/5/18.
 */
public abstract class JsonCallback extends RequestCallback {
    public static final String TAG = JsonCallback.class.getSimpleName();

    private static final int RESULT_NETWORK_SUCCESS = 0x01;//网络回调成功
    private static final int RESULT_CACHE_SUCCESS = 0x02;//缓存回调成功
    private static final int RESULT_FAILURE = 0x03;//失败

    private static final String KEY_URL = "url";
    private static final String KEY_JSON = "json";
    private static final String KEY_STATUS_CODE = "statusCode";
    private static final String KEY_FAILURE_MSG = "errorMsg";

    private Handler mHandler;//主线程回调

    public JsonCallback(Context context) {
        super(context);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RESULT_NETWORK_SUCCESS://网络回调成功
                        Bundle success = (Bundle) msg.obj;
                        String url_success = success.getString(KEY_URL);
                        String json = success.getString(KEY_JSON);
                        onSuccess(url_success, json);
                        //请求完成
                        onFinish(url_success, true, "");
                        break;
                    case RESULT_CACHE_SUCCESS://缓存回调成功
                        Bundle cache_success = (Bundle) msg.obj;
                        String cache_url_success = cache_success.getString(KEY_URL);
                        String cache_json = cache_success.getString(KEY_JSON);
                        onCacheSuccess(cache_url_success, cache_json);
                        //请求完成
                        onFinish(cache_url_success, true, "");
                        break;
                    case RESULT_FAILURE://失败
                        Bundle failure = (Bundle) msg.obj;
                        String url_failure = failure.getString(KEY_URL);
                        int statusCode = failure.getInt(KEY_STATUS_CODE);
                        String failure_msg = failure.getString(KEY_FAILURE_MSG);
                        onFailure(url_failure, statusCode, failure_msg);
                        //请求完成
                        onFinish(url_failure, false, failure_msg);
                        break;
                }
            }
        };
    }

    /**
     * 网络请求成功回调
     *
     * @param response 响应
     * @throws IOException
     */
    @Override
    public void onResponse(Call call, Response response) throws IOException {
        processResponse(response, true);
    }

    /**
     * 缓存请求成功回调
     *
     * @param response 响应
     * @throws IOException
     */
    @Override
    public void onCacheResponse(Call call, Response response) throws IOException {
        processResponse(response, false);
    }

    /**
     * 处理响应
     *
     * @param response  响应
     * @param isNetwork 是否是网络请求
     */
    private void processResponse(Response response, boolean isNetwork) throws IOException {
        if (response == null) {
            Bundle bundle = new Bundle();
            bundle.putString(KEY_URL, "");
            bundle.putInt(KEY_STATUS_CODE, 404);
            bundle.putString(KEY_FAILURE_MSG,
                    isNetwork ? ResourcesUtils.getString(NetworkConfiguration.getContext(), R.string.not_network)
                            : ResourcesUtils.getString(NetworkConfiguration.getContext(), R.string.not_cache));
            mHandler.sendMessage(Message.obtain(mHandler, RESULT_FAILURE, bundle));
            return;
        }
        String url = response.request().url().url().toString();
        String json = response.body().string();
        int statusCode = response.code();
        Logger.d(TAG, "url=" + url + ",状态码=" + statusCode);
        if (response.isSuccessful()) {
            if (isNetwork)
                Logger.d(TAG, "网络请求url:" + url + "\n" + "网络请求成功,请求结果=" + JsonUtils.jsonFormatter(json));
            else
                Logger.d(TAG, "缓存请求url:" + url + "\n" + "缓存请求成功,请求结果=" + JsonUtils.jsonFormatter(json));
            if (!TextUtils.isEmpty(json)) {
                Bundle bundle = new Bundle();
                bundle.putString(KEY_URL, url);
                bundle.putString(KEY_JSON, json);
                mHandler.sendMessage(Message.obtain(mHandler, isNetwork ? RESULT_NETWORK_SUCCESS : RESULT_CACHE_SUCCESS, bundle));
            }
        } else {
            Bundle bundle = new Bundle();
            bundle.putString(KEY_URL, url);
            bundle.putInt(KEY_STATUS_CODE, statusCode);
            bundle.putString(KEY_FAILURE_MSG, response.message());
            mHandler.sendMessage(Message.obtain(mHandler, RESULT_FAILURE, bundle));
        }
    }

    /**
     * 请求失败回调
     *
     * @param e 异常
     */
    @Override
    public void onFailure(Call call, IOException e) {
        Request request = call.request();
        String url = request.url().url().toString();
        Logger.e(TAG, "url=" + url + "\n", e);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_URL, url);
        bundle.putInt(KEY_STATUS_CODE, 500);
        if (e instanceof SocketTimeoutException || e instanceof UnknownHostException) {
            bundle.putString(KEY_FAILURE_MSG, "请检查网络后重新尝试");
            onTimeout();
        } else
            bundle.putString(KEY_FAILURE_MSG, e.getMessage());
        mHandler.sendMessage(Message.obtain(mHandler, RESULT_FAILURE, bundle));
    }

    /**
     * 网络请求成功回调
     *
     * @param url    网络地址
     * @param result 请求结果
     */
    public abstract void onSuccess(String url, String result);

    /**
     * 缓存请求成功回调
     *
     * @param url    网络地址
     * @param result 请求结果
     */
    public abstract void onCacheSuccess(String url, String result);

    /**
     * 请求失败回调
     *
     * @param url        网络地址
     * @param statusCode 状态码
     * @param msg        失败错误信息
     */
    public abstract void onFailure(String url, int statusCode, String msg);

    /**
     * 超时回调
     */
    public abstract void onTimeout();
}
