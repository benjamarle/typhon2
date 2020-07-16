package com.github.axet.bookreader.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;

import com.google.android.material.internal.NavigationMenuItemView;
import com.google.android.material.navigation.NavigationView;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.SearchView;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.BookApplication;
import com.github.axet.bookreader.app.BooksCatalog;
import com.github.axet.bookreader.app.BooksCatalogs;
import com.github.axet.bookreader.app.LocalBooksCatalog;
import com.github.axet.bookreader.app.NetworkBooksCatalog;
import com.github.axet.bookreader.app.Storage;
import com.github.axet.bookreader.fragments.LibraryFragment;
import com.github.axet.bookreader.fragments.LocalLibraryFragment;
import com.github.axet.bookreader.fragments.NetworkLibraryFragment;
import com.github.axet.bookreader.fragments.ReaderFragment;
import com.github.axet.bookreader.widgets.FBReaderView;
import com.github.axet.bookreader.widgets.FullWidthActionView;
import com.github.axet.bookreader.widgets.RotatePreferenceCompat;

import org.geometerplus.fbreader.fbreader.options.ImageOptions;
import org.geometerplus.fbreader.fbreader.options.MiscOptions;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.zorgblub.anki.AnkiDroidHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends FullscreenActivity implements NavigationView.OnNavigationItemSelectedListener, ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String TAG = MainActivity.class.getSimpleName();

    public static final String LIBRARY = "library";
    public static final String ADD_CATALOG = "add_catalog";
    public static final String SCHEME_CATALOG = "catalog";
    public static final String VIEW_CATALOG = "VIEW_CATALOG";

    public static final int RESULT_FILE = 1;
    public static final int RESULT_ADD_CATALOG = 2;
    public static final int ANKI = 402;

    Storage storage;
    OpenChoicer choicer;
    SubMenu networkMenu;
    SubMenu settingsMenu;
    Map<String, MenuItem> networkMenuMap = new TreeMap<>();
    public MenuItem libraryMenu; // navigation drawer
    BooksCatalogs catalogs;
    boolean isRunning;
    String lastSearch;
    LibraryFragment libraryFragment = LibraryFragment.newInstance();

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(FBReaderView.ACTION_MENU))
                toggle();
        }
    };

    public interface SearchListener {
        String getHint();

        void search(String s);

        void searchClose();
    }

    public interface OnBackPressed {
        boolean onBackPressed();
    }

    public static class SortByPage implements Comparator<Storage.RecentInfo> {
        @Override
        public int compare(Storage.RecentInfo o1, Storage.RecentInfo o2) {
            int r = Integer.valueOf(o1.position.getParagraphIndex()).compareTo(o2.position.getParagraphIndex());
            if (r != 0)
                return r;
            return Integer.valueOf(o1.position.getElementIndex()).compareTo(o2.position.getElementIndex());
        }
    }

    @SuppressLint("RestrictedApi")
    public static View findView(ViewGroup p, MenuItem item) {
        for (int i = 0; i < p.getChildCount(); i++) {
            View v = p.getChildAt(i);
            if (v instanceof ViewGroup) {
                View m = findView((ViewGroup) v, item);
                if (m != null)
                    return m;
            }
            if (v instanceof NavigationMenuItemView) {
                if (((NavigationMenuItemView) v).getItemData() == item)
                    return v;
            }
            if (v.getId() == item.getItemId())
                return v;
        }
        return null;
    }

    public static class ResourcesMap extends HashMap<String, String> {
        public ResourcesMap(Context context, int k, int v) {
            String[] kk = context.getResources().getStringArray(k);
            String[] vv = context.getResources().getStringArray(v);
            for (int i = 0; i < kk.length; i++) {
                put(kk[i], vv[i]);
            }
        }
    }

    public static class ProgressDialog extends AlertDialog.Builder {
        public Handler handler = new Handler();
        public ProgressBar load;
        public ProgressBar v;
        public TextView text;
        public Storage.Progress progress = new Storage.Progress() {
            @Override
            public void progress(final long bytes, final long total) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ProgressDialog.this.progress(bytes, total);
                    }
                });
            }
        };

        public ProgressDialog(Context context) {
            super(context);
            int dp10 = ThemeUtils.dp2px(context, 10);

            final LinearLayout ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.VERTICAL);
            v = new ProgressBar(context);
            v.setIndeterminate(true);
            v.setPadding(dp10, dp10, dp10, dp10);
            ll.addView(v);
            load = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            load.setPadding(dp10, dp10, dp10, dp10);
            load.setMax(100);
            ll.addView(load);
            text = new TextView(context);
            text.setPadding(dp10, dp10, dp10, dp10);
            ll.addView(text);
            load.setVisibility(View.GONE);
            text.setVisibility(View.GONE);

            setTitle(R.string.loading_book);
            setView(ll);
            setCancelable(false);
            setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        }

        public void progress(long bytes, long total) {
            String str = BookApplication.formatSize(getContext(), bytes);
            if (total > 0) {
                str += " / " + BookApplication.formatSize(getContext(), total);
                load.setProgress((int) (bytes * 100 / total));
                load.setVisibility(View.VISIBLE);
                v.setVisibility(View.GONE);
            } else {
                load.setVisibility(View.GONE);
                v.setVisibility(View.VISIBLE);
            }
            str += String.format(" (%s%s)", BookApplication.formatSize(getContext(), progress.info.getCurrentSpeed()), getContext().getString(R.string.per_second));
            text.setText(str);
            text.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new Storage(this);

        registerReceiver(receiver, new IntentFilter(FBReaderView.ACTION_MENU));

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        final ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        View navigationHeader = navigationView.getHeaderView(0);

        libraryMenu = navigationView.getMenu().findItem(R.id.nav_library);

        TextView ver = (TextView) navigationHeader.findViewById(R.id.nav_version);
        AboutPreferenceCompat.setVersion(ver);

        Menu m = navigationView.getMenu();
        networkMenu = m.addSubMenu(R.string.books_catalogs);

        catalogs = new BooksCatalogs(this);
        reloadMenu();

        settingsMenu = m.addSubMenu(R.string.menu_settings);
        settingsMenu.setIcon(R.drawable.ic_settings_black_24dp);
        MenuItem add = settingsMenu.add(R.string.add_catalog);
        add.setIntent(new Intent(ADD_CATALOG));
        add.setIcon(R.drawable.ic_add_black_24dp);
        MenuItem desc = settingsMenu.add("");
        desc.setIntent(new Intent(ADD_CATALOG));
        MenuItemCompat.setActionView(desc, new FullWidthActionView(this, R.layout.nav_footer_main));

        openLibrary();

        openIntent(getIntent());

        RotatePreferenceCompat.onCreate(this, BookApplication.PREFERENCE_ROTATE);
    }

    void reloadMenu() {
        int accent = ThemeUtils.getThemeColor(this, R.attr.colorAccent);
        networkMenu.clear();
        for (int i = 0; i < catalogs.getCount(); i++) {
            final BooksCatalog ct = catalogs.getCatalog(i);
            MenuItem m = networkMenu.add(ct.getTitle());
            Intent intent = new Intent(LIBRARY);
            intent.putExtra("url", ct.getId());
            m.setIntent(intent);
            m.setIcon(R.drawable.ic_drag_handle_black_24dp);
            AppCompatImageButton b = new AppCompatImageButton(this);
            b.setColorFilter(accent);
            b.setImageResource(R.drawable.ic_delete_black_24dp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.book_delete);
                    builder.setMessage(R.string.are_you_sure);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            catalogs.delete(ct.getId());
                            catalogs.save();
                            reloadMenu();
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
                    builder.show();
                }
            });
            MenuItemCompat.setActionView(m, b);
            m.setCheckable(true);
            networkMenuMap.put(ct.getId(), m);
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            FragmentManager fm = getSupportFragmentManager();
            for (Fragment f : fm.getFragments()) {
                if (f != null && f.isVisible() && f instanceof OnBackPressed) {
                    OnBackPressed s = (OnBackPressed) f;
                    if (s.onBackPressed())
                        return;
                }
            }
            super.onBackPressed();
            if (fm.getBackStackEntryCount() == 0)
                onResume(); // udpate theme if changed
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem searchMenu = menu.findItem(R.id.action_search);

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        MenuItem theme = menu.findItem(R.id.action_theme);
        String t = shared.getString(BookApplication.PREFERENCE_THEME, "");
        String d = getString(R.string.Theme_Dark);
        theme.setIcon(t.equals(d) ? R.drawable.ic_brightness_night_white_24dp : R.drawable.ic_brightness_day_white_24dp);
        ResourcesMap map = new ResourcesMap(this, R.array.theme_value, R.array.theme_text);
        theme.setTitle(map.get(getString(t.equals(d) ? R.string.Theme_Dark : R.string.Theme_Light)));

        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenu);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public boolean onQueryTextSubmit(String query) {
                lastSearch = query;
                searchView.clearFocus();
                FragmentManager fm = getSupportFragmentManager();
                for (Fragment f : fm.getFragments()) {
                    if (f != null && f.isVisible() && f instanceof SearchListener) {
                        SearchListener s = (SearchListener) f;
                        s.search(searchView.getQuery().toString());
                    }
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onClick(View v) {
                if (lastSearch != null && !lastSearch.isEmpty())
                    searchView.setQuery(lastSearch, false);
                FragmentManager fm = getSupportFragmentManager();
                for (Fragment f : fm.getFragments()) {
                    if (f != null && f.isVisible() && f instanceof SearchListener) {
                        SearchListener s = (SearchListener) f;
                        searchView.setQueryHint(s.getHint());
                    }
                }
            }
        });
        searchView.setOnCollapsedListener(new SearchView.OnCollapsedListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onCollapsed() {
                FragmentManager fm = getSupportFragmentManager();
                for (Fragment f : fm.getFragments()) {
                    if (f != null && f.isVisible() && f instanceof SearchListener) {
                        SearchListener s = (SearchListener) f;
                        s.searchClose();
                    }
                }
            }
        });
        searchView.setOnCloseButtonListener(new SearchView.OnCloseButtonListener() {
            @Override
            public void onClosed() {
                lastSearch = "";
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about) {
            AboutPreferenceCompat.buildDialog(this, R.raw.about).show();
            return true;
        }

        if (id == R.id.action_settings) {
            SettingsActivity.startActivity(this);
            return true;
        }

        if (id == R.id.action_folder) {
            drawer.closeDrawer(GravityCompat.START);
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, true) {
                @Override
                public void onResult(Uri uri) {
                    try {
                        BooksCatalog ct = catalogs.loadFolder(uri);
                        catalogs.save();
                        reloadMenu();
                        openLibrary(ct);
                    } catch (Exception e) {
                        ErrorDialog.Post(MainActivity.this, e);
                    }
                }
            };
            choicer.setPermissionsDialog(this, Storage.PERMISSIONS_RO, RESULT_ADD_CATALOG);
            choicer.setStorageAccessFramework(this, RESULT_ADD_CATALOG);
            choicer.show(null);
            return true;
        }

        if (id == R.id.action_json) {
            drawer.closeDrawer(GravityCompat.START);
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FILE_DIALOG, true) {
                @Override
                public void onResult(Uri uri) {
                    try {
                        BooksCatalog ct = catalogs.load(uri);
                        catalogs.save();
                        reloadMenu();
                        openLibrary(ct);
                    } catch (Exception e) {
                        ErrorDialog.Post(MainActivity.this, e);
                    }
                }
            };
            choicer.setPermissionsDialog(this, Storage.PERMISSIONS_RO, RESULT_ADD_CATALOG);
            choicer.setStorageAccessFramework(this, RESULT_ADD_CATALOG);
            choicer.show(null);
            return true;
        }

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (id == R.id.action_file) {
            String last = shared.getString(BookApplication.PREFERENCE_LAST_PATH, null);
            Uri old = null;
            if (last != null) {
                old = Uri.parse(last);
                File f = Storage.getFile(old);
                while (f != null && !f.exists()) {
                    f = f.getParentFile();
                }
                if (f != null)
                    old = Uri.fromFile(f);
            }
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FILE_DIALOG, true) {
                @Override
                public void onResult(Uri uri) {
                    String s = uri.getScheme();
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        File f = Storage.getFile(uri);
                        f = f.getParentFile();
                        SharedPreferences.Editor editor = shared.edit();
                        editor.putString(BookApplication.PREFERENCE_LAST_PATH, f.toString());
                        editor.commit();
                    }
                    loadBook(uri, null);
                }
            };
            choicer.setStorageAccessFramework(this, RESULT_FILE);
            choicer.setPermissionsDialog(this, Storage.PERMISSIONS_RO, RESULT_FILE);
            choicer.show(old);
        }

        if (id == R.id.action_theme) {
            SharedPreferences.Editor edit = shared.edit();
            String t = shared.getString(BookApplication.PREFERENCE_THEME, "");
            String d = getString(R.string.Theme_Dark);
            edit.putString(BookApplication.PREFERENCE_THEME, t.equals(d) ? getString(R.string.Theme_Light) : d);
            edit.commit();
            restartActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_library)
            openLibrary();

        Intent i = item.getIntent();
        if (i != null) {
            switch (i.getAction()) {
                case LIBRARY:
                    String n = i.getStringExtra("url");
                    openLibrary(catalogs.find(n));
                    break;
                case ADD_CATALOG:
                    PopupMenu menu = new PopupMenu(this, findView(navigationView, item));
                    getMenuInflater().inflate(R.menu.add_catalog, menu.getMenu());
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            return onOptionsItemSelected(item);
                        }
                    });
                    menu.show();
                    return true; // do not close drawer
            }
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openIntent(intent);
    }

    void openIntent(Intent intent) {
        if (intent == null)
            return;
        String a = intent.getAction();
        if (a == null)
            return;
        Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null)
            u = intent.getData();
        if (u == null) {
            String t = intent.getStringExtra(Intent.EXTRA_TEXT); // handling SEND intents
            if (t != null && t.startsWith(WebViewCustom.SCHEME_HTTP))
                u = Uri.parse(t);
        }
        if (u == null)
            return;
        if (a.equals(VIEW_CATALOG))
            openLibrary(catalogs.find(u.toString()));
        else
            loadBook(u, null);
    }

    public void loadBook(final Uri u, final Runnable success) {
        final ProgressDialog builder = new ProgressDialog(this);
        final AlertDialog d = builder.create();
        d.show();
        Thread thread = new Thread("load book") {
            @Override
            public void run() {
                final Thread t = Thread.currentThread();
                d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        t.interrupt();
                    }
                });
                try {
                    String s = u.getScheme();
                    if (s.equals(SCHEME_CATALOG)) {
                        Uri.Builder b = u.buildUpon();
                        b.scheme(WebViewCustom.SCHEME_HTTP);
                        final BooksCatalog ct = catalogs.load(b.build());
                        catalogs.save();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadMenu();
                                openLibrary(ct);
                                if (success != null)
                                    success.run();
                            }
                        });
                        return;
                    }
                    final Storage.Book book = storage.load(u, builder.progress);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || !isRunning)
                                return;
                            loadBook(book);
                            if (success != null)
                                success.run();
                        }
                    });
                } catch (FileTypeDetector.DownloadInterrupted e) {
                    Log.d(TAG, "interrupted", e);
                } catch (Exception e) {
                    ErrorDialog.Post(MainActivity.this, e);
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            d.cancel();
                        }
                    });
                }
            }
        };
        thread.start();
    }

    @SuppressLint("RestrictedApi")
    public void loadBook(final Storage.Book book) {
        final List<Uri> uu = storage.recentUris(book);
        if (uu.size() > 1) {
            LayoutInflater inflater = LayoutInflater.from(this);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            final ArrayList<ZLTextPosition> selected = new ArrayList<>();

            selected.clear();
            selected.add(book.info.position);

            final Storage.FBook fbook = storage.read(book);

            final Runnable done = new Runnable() {
                @Override
                public void run() {
                    fbook.close();

                    for (Uri u : uu) {
                        try {
                            Storage.RecentInfo info = new Storage.RecentInfo(MainActivity.this, u);
                            book.info.merge(info);
                        } catch (Exception e) {
                            Log.d(TAG, "unable to merge info", e);
                        }
                        Storage.delete(MainActivity.this, u);
                    }
                    book.info.position = selected.get(0);
                    storage.save(book);

                    openBook(book.url);
                }
            };

            builder.setTitle(R.string.sync_conflict);

            View v = inflater.inflate(R.layout.recent, null);

            final FBReaderView r = (FBReaderView) v.findViewById(R.id.recent_fbview);
            // r.config.setValue(r.app.ViewOptions.ScrollbarType, 0);
            r.config.setValue(r.app.MiscOptions.WordTappingAction, MiscOptions.WordTappingActionEnum.doNothing);
            r.config.setValue(r.app.ImageOptions.TapAction, ImageOptions.TapActionEnum.doNothing);

            SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
            String mode = shared.getString(BookApplication.PREFERENCE_VIEW_MODE, "");
            r.setWidget(mode.equals(FBReaderView.Widgets.CONTINUOUS.toString()) ? FBReaderView.Widgets.CONTINUOUS : FBReaderView.Widgets.PAGING);

            r.loadBook(fbook);

            LinearLayout pages = (LinearLayout) v.findViewById(R.id.recent_pages);

            List<Storage.RecentInfo> rr = new ArrayList<>();

            for (Uri u : uu) {
                try {
                    Storage.RecentInfo info = new Storage.RecentInfo(MainActivity.this, u);
                    if (info.position != null) {
                        boolean found = false;
                        for (int i = 0; i < rr.size(); i++) {
                            Storage.RecentInfo ii = rr.get(i);
                            if (ii.position.getParagraphIndex() == info.position.getParagraphIndex() && ii.position.getElementIndex() == info.position.getElementIndex())
                                found = true;
                        }
                        if (!found) {
                            rr.add(info);
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Unable to read info", e);
                }
            }

            Collections.sort(rr, new SortByPage());

            if (rr.size() == 1) {
                done.run();
                return;
            }

            for (int i = 0; i < rr.size(); i++) {
                final Storage.RecentInfo info = rr.get(i);
                TextView p = (TextView) inflater.inflate(R.layout.recent_item, pages, false);
                if (info.position != null)
                    p.setText(info.position.getParagraphIndex() + "." + info.position.getElementIndex());
                p.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        r.gotoPosition(info.position);
                        selected.clear();
                        selected.add(info.position);
                    }
                });
                pages.addView(p);
            }

            builder.setView(v);

            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    fbook.close();
                }
            });

            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    done.run();
                }
            });

            builder.show();
            return;
        }
        openBook(book.url);
    }

    @SuppressLint("RestrictedApi")
    public void popBackStack(String tag, int flags) { // only pop existing TAG
        FragmentManager fm = getSupportFragmentManager();
        if (tag == null) {
            fm.popBackStack(null, flags);
            return;
        }
        for (int i = 0; i < fm.getBackStackEntryCount(); i++) {
            String n = fm.getBackStackEntryAt(i).getName();
            if (n != null && n.equals(tag)) {
                fm.popBackStack(tag, flags);
                return;
            }
        }
    }

    @SuppressLint("RestrictedApi")
    public Fragment getCurrentFragment() {
        FragmentManager fm = getSupportFragmentManager();
        for (Fragment f : fm.getFragments()) {
            if (f != null && f.isVisible())
                return f;
        }
        return null;
    }

    public void openBook(Uri uri) {
        popBackStack(ReaderFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        addFragment(ReaderFragment.newInstance(uri), ReaderFragment.TAG).commit();
    }

    public void openBook(Uri uri, FBReaderView.ZLTextIndexPosition pos) {
        popBackStack(ReaderFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        addFragment(ReaderFragment.newInstance(uri, pos), ReaderFragment.TAG).commit();
    }

    public void openLibrary() {
        FragmentManager fm = getSupportFragmentManager();
        popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        openFragment(libraryFragment, LibraryFragment.TAG).commit();
        onResume(); // update theme if changed
    }

    public void openLibrary(BooksCatalog ct) {
        String n = ct.url;
        FragmentManager fm = getSupportFragmentManager();
        popBackStack(LibraryFragment.TAG, 0);
        if (ct instanceof LocalBooksCatalog) {
            Fragment f = fm.findFragmentByTag(LocalLibraryFragment.TAG);
            if (f != null) {
                if (f.getArguments().getString("url").equals(n)) {
                    addFragment(f, LocalLibraryFragment.TAG).commit();
                    return;
                }
            }
            addFragment(LocalLibraryFragment.newInstance(n), LocalLibraryFragment.TAG).commit();
        }
        if (ct instanceof NetworkBooksCatalog) {
            Fragment f = fm.findFragmentByTag(NetworkLibraryFragment.TAG);
            if (f != null) {
                if (f.getArguments().getString("url").equals(n)) {
                    addFragment(f, NetworkLibraryFragment.TAG).commit();
                    return;
                }
            }
            addFragment(NetworkLibraryFragment.newInstance(n), NetworkLibraryFragment.TAG).commit();
        }
    }

    public void restoreNetworkSelection(Fragment f) {
        clearMenu();
        String u = f.getArguments().getString("url");
        MenuItem m = networkMenuMap.get(u);
        m.setChecked(true);
    }

    public void clearMenu() {
        Menu m = navigationView.getMenu();
        for (int i = 0; i < m.size(); i++)
            m.getItem(i).setChecked(false);
        for (int i = 0; i < networkMenu.size(); i++)
            networkMenu.getItem(i).setChecked(false);
    }

    public FragmentTransaction addFragment(Fragment f, String tag) {
        return openFragment(f, tag).addToBackStack(tag);
    }

    public FragmentTransaction openFragment(Fragment f, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        return fm.beginTransaction().replace(R.id.main_content, f, tag);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        RotatePreferenceCompat.onDestroy(this);
        catalogs.save();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_FILE:
            case RESULT_ADD_CATALOG:
                if (choicer != null) // called twice or activity reacated
                    choicer.onRequestPermissionsResult(permissions, grantResults);
                break;

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_FILE:
            case RESULT_ADD_CATALOG:
                if (choicer != null) // called twice or activity reacated
                    choicer.onActivityResult(resultCode, data);
                break;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (shared.getBoolean(BookApplication.PREFERENCE_VOLUME_KEYS, false)) {
            FragmentManager fm = getSupportFragmentManager();
            for (Fragment f : fm.getFragments()) {
                if (f != null && f.isVisible() && f instanceof ReaderFragment) {
                    if (((ReaderFragment) f).onKeyDown(keyCode, event))
                        return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (shared.getBoolean(BookApplication.PREFERENCE_VOLUME_KEYS, false)) {
            FragmentManager fm = getSupportFragmentManager();
            for (Fragment f : fm.getFragments()) {
                if (f != null && f.isVisible() && f instanceof ReaderFragment) {
                    if (((ReaderFragment) f).onKeyUp(keyCode, event))
                        return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        RotatePreferenceCompat.onResume(this, BookApplication.PREFERENCE_ROTATE);
        CacheImagesAdapter.cacheClear(this);
    }

    @Override
    public void restartActivity() {
        Fragment f = getCurrentFragment();
        if (f instanceof ReaderFragment) {
            ((ReaderFragment) f).updateTheme();
            invalidateOptionsMenu();
        } else if (f instanceof NetworkLibraryFragment) {
            restartActivity(((NetworkLibraryFragment) f).getUri());
        } else if (f instanceof LibraryFragment) {
            super.restartActivity();
        }
    }

    public void restartActivity(String url) {
        Uri uri = Uri.parse(url);
        finish();
        startActivity(new Intent(this, getClass()).setAction(VIEW_CATALOG).putExtra(Intent.EXTRA_STREAM, uri));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
