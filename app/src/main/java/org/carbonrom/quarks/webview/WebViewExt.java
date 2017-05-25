/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.carbonrom.quarks.webview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import org.carbonrom.quarks.MainActivity;
import org.carbonrom.quarks.ui.EditTextExt;
import org.carbonrom.quarks.utils.PrefsUtils;
import org.carbonrom.quarks.utils.UrlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebViewExt extends WebView {

    private static final String TAG = "WebViewExt";

    private static final String DESKTOP_DEVICE = "X11; Linux x86_64";
    private static final String DESKTOP_USER_AGENT_FALLBACK =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36";

    private final Context mContext;

    private String mMobileUserAgent;
    private String mDesktopUserAgent;

    private boolean mIncognito;
    private boolean mDesktopMode;

    public WebViewExt(Context context) {
        super(context);
        mContext = context;
        setup();
    }

    public WebViewExt(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setup();
    }

    public WebViewExt(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        setup();
    }

    @Override
    public void loadUrl(String url) {
        String fixedUrl = UrlUtils.smartUrlFilter(url);
        if (fixedUrl != null) {
            super.loadUrl(fixedUrl);
            return;
        }

        String templateUri = PrefsUtils.getSearchEngine(mContext);
        fixedUrl = UrlUtils.getFormattedUri(templateUri, url);
        if (fixedUrl != null) {
            super.loadUrl(fixedUrl);
        }
    }

    private void setup() {
        getSettings().setJavaScriptEnabled(PrefsUtils.getJavascript(mContext));
        getSettings().setJavaScriptCanOpenWindowsAutomatically(PrefsUtils.getJavascript(mContext));
        getSettings().setGeolocationEnabled(PrefsUtils.getLocation(mContext));
        getSettings().setBuiltInZoomControls(true);
        getSettings().setDisplayZoomControls(false);

        setWebViewClient(new WebClient());

        setOnLongClickListener(new OnLongClickListener() {
            boolean shouldAllowDownload;

            @Override
            public boolean onLongClick(View v) {
                HitTestResult result = getHitTestResult();
                switch (result.getType()) {
                    case HitTestResult.IMAGE_TYPE:
                    case HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                        shouldAllowDownload = true;
                    case HitTestResult.SRC_ANCHOR_TYPE:
                        ((MainActivity) mContext).showSheetMenu(result.getExtra(),
                                shouldAllowDownload);
                        shouldAllowDownload = false;
                        return true;
                }
                return false;
            }
        });

        setDownloadListener((url, userAgent, contentDescription, mimeType, contentLength) ->
                ((MainActivity) mContext).downloadFileAsk(url,
                        URLUtil.guessFileName(url, contentDescription, mimeType)));

        // Mobile: Remove "wv" from the WebView's user agent. Some websites don't work
        // properly if the browser reports itself as a simple WebView.
        // Desktop: Generate the desktop user agent starting from the mobile one so that
        // we always report the current engine version.
        Pattern pattern = Pattern.compile("([^)]+ \\()([^)]+)(\\) .*)");
        Matcher matcher = pattern.matcher(getSettings().getUserAgentString());
        if (matcher.matches()) {
            String mobileDevice = matcher.group(2).replace("; wv", "");
            mMobileUserAgent = matcher.group(1) + mobileDevice + matcher.group(3);
            mDesktopUserAgent = matcher.group(1) + DESKTOP_DEVICE + matcher.group(3);
            getSettings().setUserAgentString(mMobileUserAgent);
        } else {
            Log.e(TAG, "Couldn't parse the user agent");
            mMobileUserAgent = getSettings().getUserAgentString();
            mDesktopUserAgent = DESKTOP_USER_AGENT_FALLBACK;
        }
    }

    public void init(Context context, EditTextExt editText,
                     ProgressBar progressBar, boolean incognito) {
        mIncognito = incognito;
        ChromeClient chromeClient = new ChromeClient(context, incognito);
        chromeClient.bindEditText(editText);
        chromeClient.bindProgressBar(progressBar);
        setWebChromeClient(chromeClient);
    }

    public Bitmap getSnap() {
        measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
        setDrawingCacheEnabled(true);
        buildDrawingCache();
        int size = getMeasuredWidth() > getMeasuredHeight() ?
                getMeasuredHeight() : getMeasuredWidth();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        int height = bitmap.getHeight();
        canvas.drawBitmap(bitmap, 0, height, paint);
        draw(canvas);
        return bitmap;
    }

    public boolean isIncognito() {
        return mIncognito;
    }

    public void setDesktopMode(boolean desktopMode) {
        mDesktopMode = desktopMode;
        WebSettings settings = getSettings();
        settings.setUserAgentString(desktopMode ? mDesktopUserAgent : mMobileUserAgent);
        settings.setUseWideViewPort(desktopMode);
        settings.setLoadWithOverviewMode(desktopMode);
        reload();
    }

    public boolean isDesktopMode() {
        return mDesktopMode;
    }
}