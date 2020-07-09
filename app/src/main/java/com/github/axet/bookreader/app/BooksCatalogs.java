package com.github.axet.bookreader.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.bookreader.R;

import org.geometerplus.android.fbreader.network.auth.AndroidNetworkContext;
import org.geometerplus.fbreader.network.INetworkLink;
import org.geometerplus.fbreader.network.NetworkLibrary;
import org.geometerplus.zlibrary.core.network.ZLNetworkException;
import org.geometerplus.zlibrary.core.network.ZLNetworkRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BooksCatalogs {
    public static final String TAG = BooksCatalogs.class.getSimpleName();

    public Context context;
    public NetworkLibrary nlib;
    public ArrayList<BooksCatalog> list = new ArrayList<>();

    // disable broken, closed, or authorization only repos without free books / or open links
    public static List<String> disabledIds = Arrays.asList(
            "http://data.fbreader.org/catalogs/litres2/index.php5", // authorization
            "http://www.freebookshub.com/feed/", // fake paid links
            "http://ebooks.qumran.org/opds/?lang=en", // timeout
            "http://ebooks.qumran.org/opds/?lang=de", // timeout
            "http://www.epubbud.com/feeds/catalog.atom", // ePub Bud has decided to wind down
            "http://www.shucang.org/s/index.php" // timeout
    );

    public static List<String> libAllIds(NetworkLibrary nlib) {
        List<String> all = nlib.allIds();
        for (String id : disabledIds) {
            nlib.setLinkActive(id, false);
            all.remove(id);
        }
        return all;
    }

    public static NetworkLibrary getLib(final Activity context) {
        NetworkLibrary nlib = NetworkLibrary.Instance(new Storage.Info(context));
        if (!nlib.isInitialized()) {
            try {
                nlib.initialize(new NetworkContext(context));
            } catch (ZLNetworkException e) {
                throw new RuntimeException(e);
            }
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
            String json = shared.getString(BookApplication.PREFERENCE_CATALOGS, null);
            if (json != null && !json.isEmpty()) {
                try {
                    List<String> all = nlib.allIds();
                    for (String id : all)
                        nlib.setLinkActive(id, false);
                    JSONArray a = new JSONArray(json);
                    for (int i = 0; i < a.length(); i++) {
                        String id = a.getString(i);
                        if (!disabledIds.contains(id))
                            nlib.setLinkActive(id, true);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return nlib;
    }

    public static class NetworkContext extends AndroidNetworkContext {
        Activity a;
        HttpClient client = new HttpClient();

        public NetworkContext(Activity a) {
            this.a = a;
        }

        @Override
        protected Context getContext() {
            return a;
        }

        @Override
        protected Map<String, String> authenticateWeb(URI uri, String realm, String authUrl, String completeUrl, String verificationUrl) {
            return null;
        }

        @Override
        protected void perform(ZLNetworkRequest request, int socketTimeout, int connectionTimeout) throws ZLNetworkException {
            // super.perform(request, HttpClient.CONNECTION_TIMEOUT, HttpClient.CONNECTION_TIMEOUT);
            request.doBefore();
            boolean success = false;
            try {
                if (request instanceof ZLNetworkRequest.Get) {
                    HttpClient.DownloadResponse w = client.getResponse(null, request.getURL());
                    request.handleStream(w.getInputStream(), (int) w.contentLength);
                }
                success = true;
            } catch (Exception e) {
                ErrorDialog.Post(a, e);
            } finally {
                request.doAfter(success);
            }
        }
    }

    public BooksCatalogs(Context context) {
        this.context = context;
        load();
    }

    public void openSettings() {
        final List<String> all = libAllIds(nlib);
        List<String> active = nlib.activeIds();

        final String[] nn = new String[all.size()];
        final boolean[] bb = new boolean[all.size()];
        final INetworkLink[] nl = new INetworkLink[all.size()];

        for (int i = 0; i < all.size(); i++) {
            String id = all.get(i);
            INetworkLink link = nlib.getLinkByUrl(id);
            nn[i] = link.getTitle();
            bb[i] = active.contains(id);
            nl[i] = link;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.add_catalog);
        builder.setMultiChoiceItems(nn, bb, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                bb[which] = isChecked;
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                JSONArray a = new JSONArray();
                for (int i = 0; i < all.size(); i++) {
                    nlib.setLinkActive(all.get(i), bb[i]);
                    if (bb[i])
                        a.put(all.get(i));
                }
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor edit = shared.edit();
                edit.putString(BookApplication.PREFERENCE_CATALOGS, a.toString());
                edit.commit();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.show();
    }

    public int getCount() {
        return list.size();
    }

    public BooksCatalog getCatalog(int i) {
        return list.get(i);
    }

    public BooksCatalog load(Uri uri) {
        NetworkBooksCatalog ct = new NetworkBooksCatalog();
        String s = uri.getScheme();
        try {
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver resolver = context.getContentResolver();
                InputStream is = resolver.openInputStream(uri);
                ct.load(is);
                is.close();
            } else if (s.startsWith(WebViewCustom.SCHEME_HTTP)) {
                HttpClient client = new HttpClient();
                HttpClient.DownloadResponse w = client.getResponse(null, uri.toString());
                if (w.getError() != null)
                    throw new RuntimeException(w.getError() + ": " + uri);
                InputStream is = new BufferedInputStream(w.getInputStream());
                ct.load(is);
                is.close();
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                File f = Storage.getFile(uri);
                FileInputStream is = new FileInputStream(f);
                ct.load(is);
                is.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ct.url = uri.toString();
        ct.last = System.currentTimeMillis();
        BooksCatalog old = find(uri.toString());
        if (old != null)
            list.set(list.indexOf(old), ct);
        else
            list.add(ct);
        return ct;
    }

    public BooksCatalog loadFolder(Uri uri) {
        LocalBooksCatalog ct = new LocalBooksCatalog(context);
        ct.load(uri);
        ct.url = uri.toString();
        ct.last = System.currentTimeMillis();
        BooksCatalog old = find(uri.toString());
        if (old != null)
            list.set(list.indexOf(old), ct);
        else
            list.add(ct);
        return ct;
    }

    public void load() {
        list.clear();
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int count = shared.getInt(BookApplication.PREFERENCE_CATALOGS_PREFIX + BookApplication.PREFERENCE_CATALOGS_COUNT, -1);
        for (int i = 0; i < count; i++) {
            String json = shared.getString(BookApplication.PREFERENCE_CATALOGS_PREFIX + i, "");
            try {
                JSONObject o = new JSONObject(json);
                String type = o.optString("type", "");
                if (type.equals(NetworkBooksCatalog.class.getSimpleName())) {
                    NetworkBooksCatalog ct = new NetworkBooksCatalog(o);
                    if (ct.map.isEmpty())
                        continue;
                    list.add(ct);
                }
                if (type.equals(LocalBooksCatalog.class.getSimpleName())) {
                    LocalBooksCatalog ct = new LocalBooksCatalog(context, o);
                    list.add(ct);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putInt(BookApplication.PREFERENCE_CATALOGS_PREFIX + BookApplication.PREFERENCE_CATALOGS_COUNT, list.size());
        for (int i = 0; i < list.size(); i++) {
            JSONObject o = list.get(i).save();
            edit.putString(BookApplication.PREFERENCE_CATALOGS_PREFIX + i, o.toString());
        }
        edit.commit();
    }

    public BooksCatalog find(String u) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(u)) {
                return list.get(i);
            }
        }
        return null;
    }

    public void delete(String id) {
        for (int i = 0; i < list.size(); i++) {
            BooksCatalog b = list.get(i);
            if (b.getId().equals(id)) {
                if (b instanceof LocalBooksCatalog) {
                    ((LocalBooksCatalog) b).delete();
                }
                list.remove(i);
            }
        }
    }
}
