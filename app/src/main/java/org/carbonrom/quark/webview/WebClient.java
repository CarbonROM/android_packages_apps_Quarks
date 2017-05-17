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
package org.carbonrom.quark.webview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.carbonrom.quark.R;

class WebClient extends WebViewClient {

    WebClient() {
        super();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Context context = view.getContext();
        if (!url.startsWith("http")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(request.getUrl());
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Snackbar.make(view, context.getString(R.string.error_no_activity_found),
                        Snackbar.LENGTH_LONG).show();
            }
            return true;
        }

        return false;
    }

}