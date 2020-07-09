package com.github.axet.bookreader.fragments;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.crypto.MD5;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.InvalidateOptionsMenuCompat;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.SearchView;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.activities.MainActivity;
import com.github.axet.bookreader.app.BooksCatalogs;
import com.github.axet.bookreader.app.LocalBooksCatalog;
import com.github.axet.bookreader.app.Storage;
import com.github.axet.bookreader.widgets.BrowserDialogFragment;

import org.apache.commons.io.IOUtils;
import org.geometerplus.android.util.UIUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LocalLibraryFragment extends Fragment implements MainActivity.SearchListener {
    public static final String TAG = LocalLibraryFragment.class.getSimpleName();

    public static final int RESULT_PERMS = 1;

    LibraryFragment.FragmentHolder holder;
    LocalLibraryAdapter books;
    Storage storage;
    LocalBooksCatalog n;
    String host;
    BooksCatalogs catalogs;
    Handler handler = new Handler();
    Runnable invalidateOptionsMenu;
    Uri calcRoot;
    int calcIndex;
    ArrayList<Uri> calc = new ArrayList<>();
    Runnable calcRun = new Runnable() {
        Snackbar old;

        @Override
        public void run() {
            if (calcIndex < calc.size()) {
                Uri u = calc.get(calcIndex);
                show(u);
                walk(calcRoot, u);
                calcIndex++;
                handler.post(this);
            } else {
                Collections.sort(books.all, new ByCreated());
                books.filter = null;
                loadBooks();
            }
        }

        void show(Uri u) {
            if (old == null)
                old = Snackbar.make(getActivity().findViewById(android.R.id.content), "", Snackbar.LENGTH_SHORT);
            old.setText(Storage.getDisplayName(getContext(), u));
            old.show();
        }
    };

    public static String getPath(Context context, Uri uri) { // display purpose
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return Storage.getDocumentPath(context, uri);
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = Storage.getFile(uri);
            return f.getPath();
        } else {
            throw new Storage.UnknownUri();
        }
    }

    public static String getDisplayName(Context context, Uri u) {
        String p = getPath(context, u);
        return ".../" + new File(p).getName();
    }

    public static class FilesFirst implements Comparator<Storage.Node> {
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            return Boolean.valueOf(o1.dir).compareTo(o2.dir);
        }
    }

    public static class ByCreated implements Comparator<Item> {
        @Override
        public int compare(Item o1, Item o2) {
            if (o1 instanceof Folder && o2 instanceof Folder) {
                return Integer.valueOf(((Folder) o1).order).compareTo(((Folder) o2).order);
            }
            if (o1 instanceof Folder && o2 instanceof Book) {
                return Integer.valueOf(((Folder) o1).order).compareTo(((Book) o2).folder.order);
            }
            if (o1 instanceof Book && o2 instanceof Folder) {
                return Integer.valueOf(((Book) o1).folder.order).compareTo(((Folder) o2).order);
            }
            Book b1 = (Book) o1;
            Book b2 = (Book) o2;
            int r = Integer.valueOf(b1.folder.order).compareTo(b2.folder.order);
            if (r != 0)
                return r;
            return b1.url.getLastPathSegment().compareTo(b2.url.getLastPathSegment());
        }
    }

    public interface Item {
    }

    public static class Folder implements Item {
        public int order;
        public String name;

        public Folder(String f) {
            name = f;
        }
    }

    public static class Book extends Storage.Book implements Item {
        public Folder folder;

        public Book(Folder ff, File f) {
            url = Uri.fromFile(f);
            folder = ff;
        }

        @TargetApi(21)
        public Book(Folder ff, Uri u) {
            url = u;
            folder = ff;
        }
    }

    public class LocalLibraryAdapter extends LibraryFragment.BooksAdapter {
        Map<String, Folder> folders = new TreeMap<>();
        List<Item> all = new ArrayList<>(); // all items
        List<Item> list = new ArrayList<>(); // filtered list
        String filter;

        public class BookHolder extends LibraryFragment.BooksAdapter.BookHolder {
            TextView folder;

            public BookHolder(View itemView) {
                super(itemView);
                folder = (TextView) itemView.findViewById(R.id.book_folder);
            }
        }

        public LocalLibraryAdapter() {
            super(LocalLibraryFragment.this.getContext(), LocalLibraryFragment.this.holder);
        }

        @Override
        public int getItemViewType(int position) {
            if (list.get(position) instanceof Folder)
                return R.layout.book_folder_item;
            return holder.layout;
        }

        public InputStream open(Uri uri) throws IOException {
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File f = Storage.getFile(uri);
                return new FileInputStream(f);
            } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver resolver = getContext().getContentResolver();
                return resolver.openInputStream(uri);
            } else {
                throw new Storage.UnknownUri();
            }
        }

        Folder getFolder(Uri root, Uri u) {
            File m;
            String s = root.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                int r = root.getPath().length();
                File f = Storage.getFile(u);
                String n = f.getPath();
                n = n.substring(r);
                m = new File(n);
            } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                m = new File(Storage.buildDocumentPath(getContext(), root, u));
            } else {
                throw new Storage.UnknownUri();
            }
            return getFolder(m.getParent());
        }

        Folder getFolder(String s) {
            if (s == null)
                s = OpenFileDialog.ROOT;
            Folder m = folders.get(s);
            if (m != null)
                return m;
            m = new Folder(s);
            m.order = folders.size();
            folders.put(s, m);
            all.add(m);
            return m;
        }

        void clear() {
            folders.clear();
            all.clear();
            clearTasks();
        }

        public void refresh() {
            if (filter == null || filter.isEmpty()) {
                list = new ArrayList<>(all);
                clearTasks();
            } else {
                list = new ArrayList<>();
                Set<Folder> ff = new HashSet<>();
                for (Item a : all) {
                    if (a instanceof Book) {
                        Book b = (Book) a;
                        String t = null;
                        if (b.info != null)
                            t = b.info.title;
                        if (t == null || t.isEmpty()) {
                            t = getDisplayName(getContext(), b.url);
                        }
                        if (SearchView.filter(filter, t)) {
                            if (!ff.contains(b.folder)) {
                                ff.add(b.folder);
                                list.add(b.folder);
                            }
                            list.add(b);
                        }
                    }
                }
            }
            Collections.sort(list, new ByCreated());
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public Item getItem(int position) {
            return list.get(position);
        }

        @Override
        public LibraryFragment.BooksAdapter.BookHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View convertView = inflater.inflate(viewType, parent, false);
            BookHolder h = new BookHolder(convertView);
            return h;
        }

        @Override
        public void onBindViewHolder(final LibraryFragment.BooksAdapter.BookHolder h, int position) {
            View convertView = h.itemView;
            Item i = list.get(position);
            if (i instanceof Book) {
                Book b = (Book) i;
                if (b.info == null || b.cover == null || !b.cover.exists()) {
                    downloadTask(b, convertView);
                } else {
                    downloadTaskClean(convertView);
                    downloadTaskUpdate(null, b, convertView);
                }
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (holder.clickListener != null)
                            holder.clickListener.onItemClick(null, v, h.getAdapterPosition(), -1);
                    }
                });
                convertView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (holder.longClickListener != null)
                            holder.longClickListener.onItemLongClick(null, v, h.getAdapterPosition(), -1);
                        return true;
                    }
                });
            }
            if (i instanceof Folder) {
                Folder f = (Folder) i;
                ((BookHolder) h).folder.setText(f.name);
            }
        }

        @Override
        public void downloadTaskUpdate(CacheImagesAdapter.DownloadImageTask task, Object item, Object view) {
            super.downloadTaskUpdate(task, item, view);
            BookHolder h = new BookHolder((View) view);

            Book b = (Book) item;

            if (b.info != null) {
                setText(h.aa, b.info.authors);
                String t = b.info.title;
                if (t == null || t.isEmpty()) {
                    t = getDisplayName(getContext(), b.url);
                }
                setText(h.tt, t);
            } else {
                setText(h.aa, "");
                setText(h.tt, getDisplayName(getContext(), b.url));
            }

            if (b.cover != null && b.cover.exists()) {
                ImageView image = (ImageView) ((View) view).findViewById(R.id.book_cover);
                try {
                    Bitmap bm = BitmapFactory.decodeStream(new FileInputStream(b.cover));
                    image.setImageBitmap(bm);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void updateView(CacheImagesAdapter.DownloadImageTask task, ImageView image, ProgressBar progress) {
            super.updateView(task, image, progress);
        }

        @Override
        public Bitmap downloadImageTask(CacheImagesAdapter.DownloadImageTask task) {
            try {
                Book book = (Book) task.item;
                String md5 = MD5.digest(book.url.toString());
                book.md5 = md5; // url md5, not file content!
                book.ext = Storage.getExt(getContext(), book.url).toLowerCase();
                File r = recentFile(book);
                if (r.exists()) {
                    try {
                        book.info = new Storage.RecentInfo(getContext(), r);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Unable to load info", e);
                    }
                }
                File cover = coverFile(book);
                if (book.info == null || !cover.exists() || cover.length() == 0) {
                    try {
                        LocalLibraryFragment.this.load(book); // load cover && authors
                    } catch (RuntimeException e) {
                        Log.e(TAG, "unable to load file", e);
                    }
                } else {
                    book.cover = cover;
                }
                if (book.cover == null)
                    return null;
                try {
                    return BitmapFactory.decodeStream(new FileInputStream(book.cover));
                } catch (IOException e) {
                    book.cover.delete();
                    throw new RuntimeException(e);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to load cover", e);
            }
            return null;
        }
    }

    public File coverFile(Book book) {
        return CacheImagesAdapter.cacheUri(getContext(), book.url);
    }

    public File recentFile(Book book) {
        return n.getFile(book.md5 + "." + Storage.JSON_EXT);
    }

    public void load(final Book book) {
        if (book.info == null) {
            File r = recentFile(book);
            if (r.exists())
                try {
                    book.info = new Storage.RecentInfo(getContext(), r);
                } catch (RuntimeException e) {
                    Log.d(TAG, "Unable to load info", e);
                }
        }
        if (book.info == null) {
            book.info = new Storage.RecentInfo();
            book.info.created = System.currentTimeMillis();
        }

        Storage.FBook fbook = null;
        try {
            if (book.info.authors == null || book.info.authors.isEmpty()) {
                if (fbook == null)
                    fbook = storage.read(book);
                book.info.authors = fbook.book.authorsString(", ");
            }
            if (book.info.title == null || book.info.title.isEmpty() || book.info.title.equals(book.md5)) {
                if (fbook == null)
                    fbook = storage.read(book);
                book.info.title = Storage.getTitle(book, fbook);
            }
            if (book.cover == null) {
                File cover = coverFile(book);
                if (!cover.exists() || cover.length() == 0) {
                    if (fbook == null)
                        fbook = storage.read(book);
                    storage.createCover(fbook, cover);
                }
                book.cover = cover;
            }
            save(book);
        } finally {
            if (fbook != null)
                fbook.close();
        }
    }

    public void save(Book book) {
        book.info.last = System.currentTimeMillis();
        File f = recentFile(book);
        try {
            String json = book.info.save(getContext()).toString();
            Writer w = new FileWriter(f);
            IOUtils.write(json, w);
            w.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public LocalLibraryFragment() {
    }

    public static LocalLibraryFragment newInstance(String n) {
        LocalLibraryFragment fragment = new LocalLibraryFragment();
        Bundle args = new Bundle();
        args.putString("url", n);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new Storage(getContext());
        catalogs = new BooksCatalogs(getContext());
        final String u = getArguments().getString("url");
        holder = new LibraryFragment.FragmentHolder(getContext()) {
            @Override
            public String getLayout() {
                return MD5.digest(u);
            }

            @Override
            public int getSpanSize(int position) {
                Item i = books.list.get(position);
                if (i instanceof Folder) {
                    RecyclerView.LayoutManager lm = grid.getLayoutManager();
                    if (lm instanceof GridLayoutManager) {
                        return ((GridLayoutManager) lm).getSpanCount();
                    }
                }
                return super.getSpanSize(position);
            }
        };
        books = new LocalLibraryAdapter();
        n = (LocalBooksCatalog) catalogs.find(u);

        setHasOptionsMenu(true);
    }

    void loadBooks() {
        books.clearTasks();
        books.refresh();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_library, container, false);

        final MainActivity main = (MainActivity) getActivity();

        holder.create(v);
        holder.footer.setVisibility(View.GONE);
        holder.footerNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtil.wait("search", new Runnable() {
                    @Override
                    public void run() {
                    }
                }, getContext());
            }
        });

        main.toolbar.setTitle(R.string.app_name);
        holder.grid.setAdapter(books);
        holder.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    final Item i = books.getItem(position);
                    Book b = (Book) i;
                    loadBook(b);
                } catch (RuntimeException e) {
                    ErrorDialog.Error(main, e);
                }
            }
        });
        return v;
    }

    void loadBook(final Book book) {
        final MainActivity main = (MainActivity) getActivity();
        main.loadBook(book.url, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        final MainActivity main = (MainActivity) getActivity();
        main.setFullscreen(false);
        main.restoreNetworkSelection(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void search(final String ss) {
        if (ss == null || ss.isEmpty())
            return;
        books.filter = ss;
        books.refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        books.clearTasks();
        handler.removeCallbacks(calcRun);
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCatalog();
    }

    void walk(Uri root, Uri uri) {
        ArrayList<Storage.Node> nn = Storage.walk(getContext(), root, uri);
        Collections.sort(nn, new FilesFirst());
        for (Storage.Node n : nn) {
            if (n.uri.equals(uri))
                continue;
            if (n.dir) {
                calc.add(n.uri);
            } else {
                String ext = Storage.getExt(n.name).toLowerCase(Locale.US);
                FileTypeDetector.Detector[] dd = Storage.supported();
                for (FileTypeDetector.Detector d : dd) {
                    if (ext.equals(d.ext)) {
                        books.all.add(new Book(books.getFolder(root, n.uri), n.uri));
                        break;
                    }
                }
                if (ext.equals(Storage.ZIP_EXT)) {
                    try {
                        InputStream is = books.open(n.uri);
                        FileTypeDetector.detecting(getContext(), dd, is, null, n.uri);
                        is.close();
                    } catch (IOException | NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                    for (FileTypeDetector.Detector d : dd) {
                        if (d.detected) {
                            Book book = new Book(books.getFolder(root, n.uri), n.uri);
                            book.ext = d.ext;
                            books.all.add(book);
                            break; // priority first - more imporant
                        }
                    }
                }
            }
        }
    }

    void loadCatalog() {
        calcRoot = Uri.parse(n.url);
        String s = calcRoot.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (!Storage.permitted(LocalLibraryFragment.this, Storage.PERMISSIONS_RO, RESULT_PERMS))
                return;
        }
        books.clear();
        calc.clear();
        calcIndex = 0;
        calc.add(calcRoot);
        calcRun.run();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void openBrowser(String u) {
        if (Build.VERSION.SDK_INT < 11) {
            AboutPreferenceCompat.openUrl(getContext(), u);
        } else {
            BrowserDialogFragment b = BrowserDialogFragment.create(u);
            b.show(getFragmentManager(), "");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_home) {
            openBrowser(host);
            return true;
        }
        if (holder.onOptionsItemSelected(item)) {
            invalidateOptionsMenu.run();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        invalidateOptionsMenu = InvalidateOptionsMenuCompat.onCreateOptionsMenu(this, menu, inflater);

        MenuItem homeMenu = menu.findItem(R.id.action_home);
        MenuItem tocMenu = menu.findItem(R.id.action_toc);
        MenuItem searchMenu = menu.findItem(R.id.action_search);
        MenuItem reflow = menu.findItem(R.id.action_reflow);
        MenuItem fontsize = menu.findItem(R.id.action_fontsize);
        MenuItem debug = menu.findItem(R.id.action_debug);
        MenuItem rtl = menu.findItem(R.id.action_rtl);
        MenuItem mode = menu.findItem(R.id.action_mode);
        MenuItem sort = menu.findItem(R.id.action_sort);

        reflow.setVisible(false);
        fontsize.setVisible(false);
        debug.setVisible(false);
        rtl.setVisible(false);
        tocMenu.setVisible(false);
        mode.setVisible(false);
        sort.setVisible(false);

        searchMenu.setVisible(true);

        if (host == null || host.isEmpty())
            homeMenu.setVisible(false);
        else
            homeMenu.setVisible(true);

        holder.onCreateOptionsMenu(menu);
    }

    @Override
    public void searchClose() {
        books.filter = null;
        books.refresh();
    }

    @Override
    public String getHint() {
        return getString(R.string.search_local);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RESULT_PERMS) {
            if (Storage.permitted(getContext(), Storage.PERMISSIONS_RO))
                loadCatalog();
            else
                Toast.makeText(getContext(), R.string.not_permitted, Toast.LENGTH_SHORT).show();
        }
    }
}
