package org.carbonrom.quarks.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.webkit.WebResourceResponse;

import com.android.okhttp.HttpUrl;

import org.carbonrom.quarks.R;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class AdBlocker {
    private static final int AD_HOSTS_FILE = R.raw.hosts;
    private static final Set<String> AD_HOSTS = new HashSet<>();

    public static void init(Context context) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    loadFromAssets(context);
                } catch (IOException e) {
                    // noop
                }
                return null;
            }
        }.execute();
    }

    @WorkerThread
    private static void loadFromAssets(Context context) throws IOException {
        InputStream stream = context.getResources().openRawResource(AD_HOSTS_FILE);
        BufferedReader buffer = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = buffer.readLine()) != null) {
            AD_HOSTS.add(line);
        }
        buffer.close();
        stream.close();
    }

    public static boolean isAd(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        return isAdHost(httpUrl != null ? httpUrl.host() : "");
    }

    private static boolean isAdHost(String host) {
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        int index = host.indexOf(".");
        return index >= 0 && (AD_HOSTS.contains(host) ||
                index + 1 < host.length() && isAdHost(host.substring(index + 1)));
    }

    public static WebResourceResponse createEmptyResource() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
    }
}
