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
package org.carbonrom.quarks;

import android.Manifest;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuPopupHelper;
import android.support.v7.widget.CardView;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.carbonrom.quarks.favorite.Favorite;
import org.carbonrom.quarks.favorite.FavoriteActivity;
import org.carbonrom.quarks.favorite.FavoriteDatabaseHandler;
import org.carbonrom.quarks.history.HistoryActivity;
import org.carbonrom.quarks.ui.EditTextExt;
import org.carbonrom.quarks.utils.AdBlocker;
import org.carbonrom.quarks.utils.PrefsUtils;
import org.carbonrom.quarks.utils.UiUtils;
import org.carbonrom.quarks.webview.WebViewExt;
import org.carbonrom.quarks.webview.WebViewExtActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends WebViewExtActivity implements View.OnTouchListener,
        View.OnScrollChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PROVIDER = "org.carbonrom.quarks.fileprovider";
    private static final String EXTRA_INCOGNITO = "extra_incognito";
    private static final String EXTRA_DESKTOP_MODE = "extra_desktop_mode";
    private static final String EXTRA_URL = "extra_url";
    private static final int STORAGE_PERM_REQ = 423;
    private static final int LOCATION_PERM_REQ = 424;
    private static final int ALWAYS_DEFAULT_TO_INCOGNITO = 1;
    private static final int EXTERNAL_DEFAULT_TO_INCOGNITO = 2;

    private CoordinatorLayout mCoordinator;
    private WebViewExt mWebView;
    private CardView searchCard;
    private EditTextExt editText;
    private ImageView searchMenu;
    private ImageView searchIncognito;

    private String mWaitingDownloadUrl;

    private Bitmap mUrlIcon;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private GestureDetectorCompat mGestureDetector;
    private boolean mFingerReleased = false;
    private boolean mGesture = false;
    private boolean nightMode;
    private boolean mIncognito;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCoordinator = (CoordinatorLayout) findViewById(R.id.coordinator_layout);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            mWebView.reload();
            new Handler().postDelayed(() -> mSwipeRefreshLayout.setRefreshing(false), 1000);
        });
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.load_progress);
        editText = (EditTextExt) findViewById(R.id.url_bar);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager manager = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                manager.hideSoftInputFromWindow(editText.getApplicationWindowToken(), 0);

                mWebView.loadUrl(editText.getText().toString());
                editText.clearFocus();
                return true;
            }
            return false;
        });

        // Make sure prefs are set before loading them
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        Intent intent = getIntent();
        String url = intent.getDataString();
        mIncognito = intent.getBooleanExtra(EXTRA_INCOGNITO, false);
        boolean desktopMode = false;
        switch(PrefsUtils.getIncognitoPolicy(this)) {
            case ALWAYS_DEFAULT_TO_INCOGNITO:
                mIncognito = true;
                break;
            case EXTERNAL_DEFAULT_TO_INCOGNITO:
                mIncognito = !Intent.ACTION_MAIN.equals(intent.getAction());
                break;
            default:
                incognito = intent.getBooleanExtra(EXTRA_INCOGNITO, false);
        }

        SharedPreferences night_mode = this.getSharedPreferences("night_mode_pref", Context.MODE_PRIVATE);
        nightMode = night_mode.getBoolean("night_mode_pref", false);

        // Restore from previous instance
        if (savedInstanceState != null) {
            mIncognito = savedInstanceState.getBoolean(EXTRA_INCOGNITO, mIncognito);
            if (url == null || url.isEmpty()) {
                url = savedInstanceState.getString(EXTRA_URL, null);
            }
            desktopMode = savedInstanceState.getBoolean(EXTRA_DESKTOP_MODE, false);
        }
        searchCard = (CardView) findViewById(R.id.search_card);
        searchMenu = (ImageView) findViewById(R.id.search_menu);
        searchIncognito = (ImageView) findViewById(R.id.incognito);
        if (nightMode) {
            int cardColor = getColor(R.color.cardview_dark_background);
            int textColor = getColor(android.R.color.white);
            searchCard.setCardBackgroundColor(cardColor);
            editText.setTextColor(textColor);
            searchMenu.setColorFilter(textColor);
            searchIncognito.setColorFilter(textColor);
        }

        searchIncognito.setVisibility(mIncognito ? View.VISIBLE : View.GONE);

        setupMenu();
        mWebView = (WebViewExt) findViewById(R.id.web_view);
        mWebView.init(this, editText, progressBar, mIncognito);
        mWebView.setDesktopMode(desktopMode);
        mWebView.loadUrl(url == null ? PrefsUtils.getHomePage(this) : url);

        SimpleGestureListener simpleGestureListener = new SimpleGestureListener();
        mGestureDetector = new GestureDetectorCompat(this, simpleGestureListener);
        mGestureDetector.setOnDoubleTapListener(simpleGestureListener);
        mWebView.setOnTouchListener(this);
        mWebView.setOnScrollChangeListener(this);
        AdBlocker.init(this);
    }

    @Override
    protected void onStop() {
        CookieManager.getInstance().flush();
        super.onStop();
    }

    @Override
    public void onPause() {
        mWebView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.onResume();
        CookieManager.getInstance()
                .setAcceptCookie(!mWebView.isIncognito() && PrefsUtils.getCookie(this));
        if (PrefsUtils.getLookLock(this)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        switch (requestCode) {
            case LOCATION_PERM_REQ:
                if (hasLocationPermission()) {
                    mWebView.reload();
                }
                break;
            case STORAGE_PERM_REQ:
                if (hasStoragePermission() && mWaitingDownloadUrl != null) {
                    downloadFileAsk(mWaitingDownloadUrl, null, null);
                } else {
                    if (shouldShowRequestPermissionRationale(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.permission_error_title)
                                .setMessage(R.string.permission_error_storage)
                                .setCancelable(false)
                                .setPositiveButton(getString(R.string.permission_error_ask_again),
                                        ((dialog, which) -> requestStoragePermission()))
                                .setNegativeButton(getString(R.string.dismiss),
                                        (((dialog, which) -> dialog.dismiss())))
                                .show();
                    } else {
                        Snackbar.make(mCoordinator, getString(R.string.permission_error_forever),
                                Snackbar.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Preserve webView status
        outState.putString(EXTRA_URL, mWebView.getUrl());
        outState.putBoolean(EXTRA_INCOGNITO, mWebView.isIncognito());
        outState.putBoolean(EXTRA_DESKTOP_MODE, mWebView.isDesktopMode());
    }

    private void setupMenu() {
        ImageButton menu = (ImageButton) findViewById(R.id.search_menu);
        menu.setOnClickListener(v -> {
            boolean isDesktop = mWebView.isDesktopMode();
            ContextThemeWrapper wrapper = new ContextThemeWrapper(this,
                    R.style.AppTheme_PopupMenuOverlapAnchor);

            PopupMenu popupMenu = new PopupMenu(wrapper, menu, Gravity.NO_GRAVITY,
                    R.attr.actionOverflowMenuStyle, 0);
            popupMenu.inflate(R.menu.menu_main);

            MenuItem desktopMode = popupMenu.getMenu().findItem(R.id.desktop_mode);
            desktopMode.setTitle(getString(isDesktop ?
                    R.string.menu_mobile_mode : R.string.menu_desktop_mode));
            desktopMode.setIcon(ContextCompat.getDrawable(this, isDesktop ?
                    R.drawable.ic_mobile : R.drawable.ic_desktop));

            MenuItem nightModeMenu = popupMenu.getMenu().findItem(R.id.night_mode);
            nightModeMenu.setTitle(getString(nightMode ?
                    R.string.menu_day_mode : R.string.menu_night_mode));
            nightModeMenu.setIcon(ContextCompat.getDrawable(this, nightMode ?
                    R.drawable.ic_day : R.drawable.ic_night));

            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.menu_new:
                        openInNewTab(null, false);
                        break;
                    case R.id.menu_incognito:
                        openInNewTab(null, true);
                        break;
                    case R.id.menu_reload:
                        mWebView.reload();
                        break;
                    case R.id.menu_add_favorite:
                        setAsFavorite(mWebView.getTitle(), mWebView.getUrl());
                        break;
                    case R.id.menu_share:
                        // Delay a bit to allow popup menu hide animation to play
                        new Handler().postDelayed(() -> shareUrl(mWebView.getUrl()), 300);
                        break;
                    case R.id.menu_favorite:
                        startActivity(new Intent(this, FavoriteActivity.class));
                        break;
                    case R.id.menu_history:
                        startActivity(new Intent(this, HistoryActivity.class));
                        break;
                    case R.id.menu_shortcut:
                        addShortcut();
                        break;
                    case R.id.menu_settings:
                        startActivity(new Intent(this, SettingsActivity.class));
                        break;
                    case R.id.desktop_mode:
                        mWebView.setDesktopMode(!isDesktop);
                        desktopMode.setTitle(getString(isDesktop ?
                                R.string.menu_desktop_mode : R.string.menu_mobile_mode));
                        desktopMode.setIcon(ContextCompat.getDrawable(this, isDesktop ?
                                R.drawable.ic_desktop : R.drawable.ic_mobile));
                        break;
                    case R.id.night_mode:
                        nightMode = !nightMode;
                        int cardColor = nightMode ?
                                getColor(R.color.cardview_dark_background) : getColor(R.color.cardview_light_background);
                        int textColor = nightMode ?
                                getColor(android.R.color.white) : getColor(android.R.color.black);
                        searchCard.setCardBackgroundColor(cardColor);
                        editText.setTextColor(textColor);
                        searchMenu.setColorFilter(textColor);
                        searchIncognito.setColorFilter(textColor);
                        mWebView.reload();
                        nightModeMenu.setTitle(getString(nightMode ?
                                R.string.menu_day_mode : R.string.menu_night_mode));
                        nightModeMenu.setIcon(ContextCompat.getDrawable(this, nightMode ?
                                R.drawable.ic_day : R.drawable.ic_night));
                        // Save choice to preference
                        SharedPreferences night_mode = this.getSharedPreferences("night_mode_pref", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = night_mode.edit();
                        editor.putBoolean("night_mode_pref", nightMode);
                        editor.apply();
                        break;
                }
                return true;
            });

            // Fuck you, lint
            //noinspection RestrictedApi
            MenuPopupHelper helper = new MenuPopupHelper(wrapper,
                    (MenuBuilder) popupMenu.getMenu(), menu);
            //noinspection RestrictedApi
            helper.setForceShowIcon(true);
            //noinspection RestrictedApi
            helper.show();
        });
    }

    private void openInNewTab(String url, boolean incognito) {
        Intent intent = new Intent(this, MainActivity.class);
        if (url != null && !url.isEmpty()) {
            intent.setData(Uri.parse(url));
        }
        intent.setAction(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(EXTRA_INCOGNITO, incognito);
        startActivity(intent);
    }

    private void shareUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, url);

        if (PrefsUtils.getAdvancedShare(this) && url.equals(mWebView.getUrl())) {
            try {
                File file = new File(getCacheDir(),
                        String.valueOf(System.currentTimeMillis()) + ".png");
                FileOutputStream out = new FileOutputStream(file);
                Bitmap bm = mWebView.getSnap();
                if (bm == null) {
                    out.close();
                    return;
                }
                bm.compress(Bitmap.CompressFormat.PNG, 70, out);
                out.flush();
                out.close();
                intent.putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(this, PROVIDER, file));
                intent.setType("image/png");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            intent.setType("text/plain");
        }

        startActivity(Intent.createChooser(intent, getString(R.string.share_title)));
    }

    private void setAsFavorite(String title, String url) {
        FavoriteDatabaseHandler handler = new FavoriteDatabaseHandler(this);
        boolean hasValidIcon = mUrlIcon != null && !mUrlIcon.isRecycled();
        handler.addItem(new Favorite(title, url, hasValidIcon ?
                UiUtils.getColor(this, mUrlIcon, false, false) :
                ContextCompat.getColor(this, R.color.colorAccent)));
        Snackbar.make(mCoordinator, getString(R.string.favorite_added),
                Snackbar.LENGTH_LONG).show();
    }

    public void downloadFileAsk(String url, String contentDisposition, String mimeType) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

        if (!hasStoragePermission()) {
            mWaitingDownloadUrl = url;
            requestStoragePermission();
            return;
        }
        mWaitingDownloadUrl = null;

        new AlertDialog.Builder(this)
                .setTitle(R.string.download_title)
                .setMessage(getString(R.string.download_message, fileName))
                .setPositiveButton(getString(R.string.download_positive),
                        (dialog, which) -> fetchFile(url, fileName))
                .setNegativeButton(getString(R.string.dismiss),
                        ((dialog, which) -> dialog.dismiss()))
                .show();
    }

    private void fetchFile(String url, String fileName) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

        // Let this downloaded file be scanned by MediaScanner - so that it can
        // show up in Gallery app, for example.
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        manager.enqueue(request);
    }

    public void showSheetMenu(String url, boolean shouldAllowDownload) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);

        View view = getLayoutInflater().inflate(R.layout.sheet_actions, new LinearLayout(this));
        View tabLayout = view.findViewById(R.id.sheet_new_tab);
        View shareLayout = view.findViewById(R.id.sheet_share);
        View favouriteLayout = view.findViewById(R.id.sheet_favourite);
        View downloadLayout = view.findViewById(R.id.sheet_download);

        tabLayout.setOnClickListener(v -> openInNewTab(url, mIncognito));
        shareLayout.setOnClickListener(v -> shareUrl(url));
        favouriteLayout.setOnClickListener(v -> setAsFavorite(url, url));
        if (shouldAllowDownload) {
            downloadLayout.setOnClickListener(v -> downloadFileAsk(url, null, null));
            downloadLayout.setVisibility(View.VISIBLE);
        }
        sheet.setContentView(view);
        sheet.show();
    }

    private void requestStoragePermission() {
        String[] permissionArray = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissionArray, STORAGE_PERM_REQ);
    }

    private boolean hasStoragePermission() {
        int result = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermission() {
        String[] permissionArray = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        requestPermissions(permissionArray, LOCATION_PERM_REQ);
    }

    public boolean hasLocationPermission() {
        int result = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void setColor(Bitmap favicon, boolean incognito) {
        ActionBar actionBar = getSupportActionBar();
        if (favicon == null || favicon.isRecycled() || actionBar == null) {
            return;
        }

        mUrlIcon = favicon.copy(favicon.getConfig(), true);
        int color = UiUtils.getColor(this, favicon, incognito, nightMode);
        actionBar.setBackgroundDrawable(new ColorDrawable(color));
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);

        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (UiUtils.isColorLight(color)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);

        setTaskDescription(new ActivityManager.TaskDescription(mWebView.getTitle(),
                favicon, color));

        if (!favicon.isRecycled()) {
            favicon.recycle();
        }
    }

    public void setFavicon() {
        ImageView mFavicon = (ImageView) findViewById(R.id.favicon);
        if (mUrlIcon == null || mUrlIcon.isRecycled()) {
            mFavicon.setVisibility(View.GONE);
            return;
        }
        mFavicon.setVisibility(View.VISIBLE);
        mFavicon.setImageBitmap(mUrlIcon);
    }

    private void addShortcut() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setData(Uri.parse(mWebView.getUrl()));
        intent.setAction(Intent.ACTION_MAIN);

        Bitmap icon = mUrlIcon == null ?
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher) : mUrlIcon;
        Bitmap launcherIcon = UiUtils.getShortcutIcon(this, icon);

        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mWebView.getTitle());
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, launcherIcon);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        sendBroadcast(addIntent);
        launcherIcon.recycle();
        Snackbar.make(mCoordinator, getString(R.string.shortcut_added),
                Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mFingerReleased = event.getAction() == MotionEvent.ACTION_UP;

        if (mGesture && mFingerReleased && mWebView.getScrollY() == 0) {
            // We are ending a gesture and we are at the top
            mSwipeRefreshLayout.setEnabled(true);
        } else if (mGesture || event.getPointerCount() > 1) {
            // A gesture is ongoing or starting
            mSwipeRefreshLayout.setEnabled(false);
        } else if (event.getAction() != MotionEvent.ACTION_MOVE) {
            // We are either initiating or ending a movement
            if (mWebView.getScrollY() == 0) {
                mSwipeRefreshLayout.setEnabled(true);
            } else {
                mSwipeRefreshLayout.setEnabled(false);
            }
        }
        mGesture = false;

        return super.onTouchEvent(event);
    }

    @Override
    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        // In case we reach the top without touching the screen (e.g. fling gesture)
        if (mFingerReleased && scrollY == 0) {
            mSwipeRefreshLayout.setEnabled(true);
        }
    }

    private class SimpleGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public void onLongPress(MotionEvent e) {
            mGesture = true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            mGesture = true;
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mGesture = true;
            return false;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            mGesture = true;
            return false;
        }
    }

    // Intents used for QuickTiles and other shortcuts
    public static boolean handleShortcuts(Context c, String shortcut) {
        switch (shortcut){
            case "incognito":
                Intent intent = new Intent(c, MainActivity.class);
                intent.putExtra(EXTRA_INCOGNITO, true);
                c.startActivity(intent);
                break;
            case "newtab":
                c.startActivity(new Intent(c, MainActivity.class));
                break;
            case "favorites":
                c.startActivity(new Intent(c, FavoriteActivity.class));
                break;
        }
        return true;
    }
}
