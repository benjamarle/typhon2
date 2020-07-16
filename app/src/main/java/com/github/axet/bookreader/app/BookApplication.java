package com.github.axet.bookreader.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.activities.MainActivity;

import org.geometerplus.zlibrary.ui.android.library.ZLAndroidApplication;
import org.zorgblub.rikai.download.settings.DictionarySettings;
import org.zorgblub.rikai.download.settings.ui.OnFileChosenListener;
import org.zorgblub.rikai.download.settings.ui.TyphonFileChooser;

import java.io.File;

public class BookApplication extends MainApplication {
    public static String PREFERENCE_THEME = "theme";
    public static String PREFERENCE_CATALOGS = "catalogs";
    public static String PREFERENCE_CATALOGS_PREFIX = "catalogs_";
    public static String PREFERENCE_CATALOGS_COUNT = "count";
    public static String PREFERENCE_FONTFAMILY_FBREADER = "fontfamily_fb";
    public static String PREFERENCE_FONTSIZE_FBREADER = "fontsize_fb";
    public static String PREFERENCE_FONTSIZE_REFLOW = "fontsize_reflow";
    public static float PREFERENCE_FONTSIZE_REFLOW_DEFAULT = 0.8f;
    public static String PREFERENCE_LIBRARY_LAYOUT = "layout_";
    public static String PREFERENCE_SCREENLOCK = "screen_lock";
    public static String PREFERENCE_VOLUME_KEYS = "volume_keys";
    public static String PREFERENCE_LAST_PATH = "last_path";
    public static String PREFERENCE_ROTATE = "rotate";
    public static String PREFERENCE_VIEW_MODE = "view_mode";
    public static String PREFERENCE_STORAGE = "storage_path";
    public static String PREFERENCE_SORT = "sort";

    public ZLAndroidApplication zlib;

    public static int getTheme(Context context, int light, int dark) {
        return MainApplication.getTheme(context, PREFERENCE_THEME, light, dark, context.getString(R.string.Theme_Dark));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        BookApplication.context = getApplicationContext();
        DictionarySettings.setup(context, new TyphonFileChooser() {
            @Override
            public void startFileChooser(OnFileChosenListener listener, Activity activity) {
                OpenChoicer choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FILE_DIALOG, true) {
                    @Override
                    public void onResult(Uri uri) {
                        String s = uri.getScheme();
                        if (!s.equals(ContentResolver.SCHEME_FILE)) {
                            listener.onFileChosen(null);
                            return;
                        }
                        File f = Storage.getFile(uri);
                        listener.onFileChosen(f);
                    }
                };
                choicer.setStorageAccessFramework(activity, MainActivity.RESULT_FILE);
                choicer.setPermissionsDialog(activity, Storage.PERMISSIONS_RO, MainActivity.RESULT_FILE);
                choicer.show(null);
            }
        });
        zlib = new ZLAndroidApplication() {
            {

                attachBaseContext(BookApplication.this);
                onCreate();
            }
        };
        new HttpClient.SpongyLoader(this, false);
    }

    private static Context context;

    public static Context getAppContext() {
        return BookApplication.context;
    }
}
