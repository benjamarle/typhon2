package com.github.axet.bookreader.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.crypto.MD5;
import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.InvalidateOptionsMenuCompat;
import com.github.axet.androidlibrary.widgets.SearchView;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.activities.MainActivity;
import com.github.axet.bookreader.app.BooksCatalogs;
import com.github.axet.bookreader.app.NetworkBooksCatalog;
import com.github.axet.bookreader.app.Storage;
import com.github.axet.bookreader.widgets.BookDialog;
import com.github.axet.bookreader.widgets.BrowserDialogFragment;

import org.geometerplus.android.util.UIUtil;
import org.geometerplus.fbreader.network.INetworkLink;
import org.geometerplus.fbreader.network.NetworkBookItem;
import org.geometerplus.fbreader.network.NetworkCatalogItem;
import org.geometerplus.fbreader.network.NetworkImage;
import org.geometerplus.fbreader.network.NetworkLibrary;
import org.geometerplus.fbreader.network.NetworkOperationData;
import org.geometerplus.fbreader.network.SearchItem;
import org.geometerplus.fbreader.network.SingleCatalogSearchItem;
import org.geometerplus.fbreader.network.opds.OPDSNetworkLink;
import org.geometerplus.fbreader.network.opds.OPDSPredefinedNetworkLink;
import org.geometerplus.fbreader.network.opds.OpenSearchDescription;
import org.geometerplus.fbreader.network.tree.CatalogExpander;
import org.geometerplus.fbreader.network.tree.NetworkBookTree;
import org.geometerplus.fbreader.network.tree.NetworkCatalogTree;
import org.geometerplus.fbreader.network.tree.NetworkItemsLoader;
import org.geometerplus.fbreader.network.tree.SearchCatalogTree;
import org.geometerplus.fbreader.network.urlInfo.UrlInfo;
import org.geometerplus.fbreader.network.urlInfo.UrlInfoCollection;
import org.geometerplus.fbreader.network.urlInfo.UrlInfoWithDate;
import org.geometerplus.fbreader.tree.FBTree;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.network.ZLNetworkContext;
import org.geometerplus.zlibrary.core.network.ZLNetworkException;
import org.geometerplus.zlibrary.core.network.ZLNetworkRequest;
import org.geometerplus.zlibrary.core.util.MimeType;
import org.geometerplus.zlibrary.core.util.ZLNetworkUtil;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;

public class NetworkLibraryFragment extends Fragment implements MainActivity.SearchListener {
    public static final String TAG = NetworkLibraryFragment.class.getSimpleName();

    public static final String CONTENTTYPE_EPUB = "application/epub+zip";
    public static final String CONTENTTYPE_MOBI = "application/x-mobipocket-ebook";
    public static final String CONTENTTYPE_PDF = "application/pdf";

    LibraryFragment.FragmentHolder holder;
    NetworkLibraryAdapter books;
    Storage storage;
    NetworkBooksCatalog n;
    NetworkLibrary lib;
    BooksCatalogs.NetworkContext nc;
    SearchCatalog searchCatalog;
    String host;
    BooksCatalogs catalogs;
    OPDSNetworkLink link;
    NetworkItemsLoader def;
    ArrayList<Pattern> ignore;
    String useragent;
    ArrayList<NetworkItemsLoader> toolbarItems = new ArrayList<>();
    Handler handler = new Handler();
    Runnable invalidateOptionsMenu;

    public static String formatMime(String mime) {
        switch (mime) {
            case Storage.CONTENTTYPE_FB2:
                return "fb2";
            case CONTENTTYPE_EPUB:
                return "epub";
            case CONTENTTYPE_MOBI:
                return "mobi";
            case CONTENTTYPE_PDF:
                return "pdf";
        }
        return mime;
    }

    public static class MobileFirst implements Comparator<UrlInfo> {
        public static String[] order = new String[]{"epub", "fb2", "x-fictionbook", "mobi", "x-mobipocket-ebook"};

        public MobileFirst() {
        }

        Integer indexOf(MimeType m) {
            for (int i = 0; i < order.length; i++) {
                if (m.Name.contains(order[i]))
                    return i;
            }
            return Integer.MAX_VALUE;
        }

        public int compare(MimeType m1, MimeType m2) {
            return indexOf(m1).compareTo(indexOf(m2));
        }

        @Override
        public int compare(UrlInfo o1, UrlInfo o2) {
            int r = compare(o1.Mime, o2.Mime);
            if (r != 0)
                return r;
            return 0;
        }
    }

    public class SearchCatalog {
        NetworkOperationData data;
        NetworkItemsLoader l;
        String pattern;

        public SearchCatalog(INetworkLink link) {
            SearchItem item = new SingleCatalogSearchItem(link) {
                @Override
                public void runSearch(ZLNetworkContext nc, NetworkItemsLoader loader, String pattern) throws ZLNetworkException {
                    data = Link.createOperationData(loader);
                    ZLNetworkRequest request = Link.simpleSearchRequest(pattern, data);
                    nc.perform(request);
                    if (loader.confirmInterruption()) {
                        return;
                    }
                }
            };
            final NetworkCatalogTree tree = lib.getFakeCatalogTree(item);
            final SearchCatalogTree s = new SearchCatalogTree(tree, item);
            l = new NetworkItemsLoader(nc, s) {
                @Override
                protected void onFinish(ZLNetworkException exception, boolean interrupted) {
                }

                @Override
                protected void doBefore() throws ZLNetworkException {
                }

                @Override
                protected void load() throws ZLNetworkException {
                    final SearchItem item = (SearchItem) Tree.Item;
                    if (pattern.equals(item.getPattern())) {
                        if (Tree.hasChildren()) {
                            return;
                        }
                    } else {
                        Tree.clearCatalog();
                    }
                    item.runSearch(nc, this, pattern);
                }
            };
        }

        public void search(String p) {
            pattern = p;
            l.run();
        }

        public void resume() {
            try {
                ZLNetworkRequest request = data.resume();
                nc.perform(request);
            } catch (ZLNetworkException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class NetworkLibraryAdapter extends LibraryFragment.BooksAdapter {
        String filter;
        List<FBTree> all = new ArrayList<>();
        List<FBTree> list = new ArrayList<>();

        public NetworkLibraryAdapter() {
            super(NetworkLibraryFragment.this.getContext(), NetworkLibraryFragment.this.holder);
        }

        @Override
        public int getItemViewType(int position) {
            return holder.layout;
        }

        public void load(List<FBTree> ll) {
            if (ignore != null) {
                books.all = new ArrayList<>();
                for (FBTree l : ll) {
                    if (ignore(l))
                        continue;
                    books.all.add(l);
                }
            } else {
                books.all = ll;
            }
            books.filter = null;
        }

        boolean ignore(FBTree l) {
            for (Pattern p : ignore) {
                Matcher m = p.matcher(l.getName());
                if (m.find()) {
                    return true;
                }
            }
            return false;
        }

        public void refresh() {
            if (searchCatalog != null || filter == null || filter.isEmpty()) {
                list = new ArrayList<>(all);
                clearTasks();
            } else {
                list = new ArrayList<>();
                for (FBTree b : all) {
                    if (SearchView.filter(filter, b.getName())) {
                        list.add(b);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public FBTree getItem(int position) {
            return list.get(position);
        }

        @Override
        public String getAuthors(int position) {
            String a = "";
            FBTree b = list.get(position);
            if (b instanceof NetworkBookTree) {
                NetworkBookTree i = (NetworkBookTree) b;
                for (NetworkBookItem.AuthorData d : i.Book.Authors) {
                    if (!a.isEmpty())
                        a += ", ";
                    a += d.DisplayName;
                }
            }
            return a;
        }

        @Override
        public String getTitle(int position) {
            FBTree b = list.get(position);
            return b.getName();
        }

        @Override
        public Uri getCover(int position) {
            FBTree b = list.get(position);
            ZLImage cover = b.getCover();
            if (cover instanceof NetworkImage)
                return Uri.parse(((NetworkImage) cover).Url);
            return null;
        }

        @Override
        public void onBindViewHolder(BookHolder h, int position) {
            super.onBindViewHolder(h, position);

            Uri cover = getCover(position);

            View convertView = h.itemView;

            if (cover != null)
                downloadTask(cover, convertView);
            else
                downloadTaskUpdate(null, null, convertView);
        }
    }

    public NetworkLibraryFragment() {
    }

    public static NetworkLibraryFragment newInstance(String n) {
        NetworkLibraryFragment fragment = new NetworkLibraryFragment();
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
        };
        lib = NetworkLibrary.Instance(new Storage.Info(getContext()));
        books = new NetworkLibraryAdapter();
        n = (NetworkBooksCatalog) catalogs.find(u);
        nc = new BooksCatalogs.NetworkContext(getActivity());

        setHasOptionsMenu(true);

        ArrayList a = (ArrayList) n.opds.get("ignore");
        if (a != null) {
            ignore = new ArrayList<>();
            for (Object i : a) {
                Pattern p = Pattern.compile(Storage.wildcard((String) i));
                ignore.add(p);
            }
        }

        UrlInfoCollection<UrlInfoWithDate> infos = new UrlInfoCollection<>();
        infos.addInfo(new UrlInfoWithDate(UrlInfo.Type.Catalog, (String) n.opds.get("root"), MimeType.APP_ATOM_XML));
        if (n.opds.get("search") != null) {
            final OpenSearchDescription descr = OpenSearchDescription.createDefault((String) n.opds.get("search"), MimeType.APP_ATOM_XML);
            if (descr.isValid()) {
                infos.addInfo(new UrlInfoWithDate(UrlInfo.Type.Search, descr.makeQuery("%s"), MimeType.APP_ATOM_XML));
            }
        }
        link = new OPDSPredefinedNetworkLink(lib, -1, "", "", "", "", infos);

        toolbarItems.clear();
        if (n.opds.get("search") != null) {
            searchCatalog = new SearchCatalog(link);
        }
        if (n.tops != null) {
            for (String key : n.tops.keySet()) {
                String url = n.tops.get(key);
                toolbarItems.add(getCatalogItem(url, key));
            }
        }

        useragent = (String) n.opds.get("user-agent");
        if (useragent == null) {
            useragent = ZLNetworkUtil.getUserAgent();
        }

        if (n.opds.get("get") != null) {
            String get = (String) n.opds.get("get");
            if (!get.equals("tops")) {
                def = getCatalogItem(get, "default");
                loadDefault();
            }
        }
    }

    public String getUri() {
        return getArguments().getString("url");
    }

    public NetworkItemsLoader getCatalogItem(String url, String name) {
        UrlInfoCollection<UrlInfoWithDate> ii = new UrlInfoCollection<>();
        ii.addInfo(new UrlInfoWithDate(UrlInfo.Type.Catalog, url, MimeType.APP_ATOM_XML));
        OPDSPredefinedNetworkLink link = new OPDSPredefinedNetworkLink(lib, -1, "", name, "", "", ii);
        final NetworkCatalogItem item = link.libraryItem();
        final NetworkCatalogTree tree = lib.getFakeCatalogTree(item);
        final NetworkItemsLoader l = new NetworkItemsLoader(nc, tree) {
            @Override
            protected void onFinish(ZLNetworkException exception, boolean interrupted) {
            }

            @Override
            protected void doBefore() throws ZLNetworkException {
            }

            @Override
            protected void load() throws ZLNetworkException {
            }
        };
        return l;
    }

    void loadDefault() {
        final MainActivity main = (MainActivity) getActivity();
        UIUtil.wait("loadingBookList", new Runnable() {
            @Override
            public void run() {
                try {
                    if (def.Tree.subtrees().isEmpty())
                        def.Tree.Item.loadChildren(def);
                    books.load(def.Tree.subtrees());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadView();
                        }
                    });
                } catch (Exception e) {
                    ErrorDialog.Post(main, e);
                }
            }
        }, getContext());
    }

    void loadView() {
        loadBooks();
        loadtoolBar();
    }

    boolean expandCatalogs(ArrayList<NetworkCatalogTree> all, NetworkCatalogTree tree) {
        boolean c = false;
        for (FBTree f : tree.subtrees()) {
            if (all.size() > 4)
                return true;
            if (f instanceof NetworkCatalogTree) {
                c = true;
                NetworkCatalogTree t = (NetworkCatalogTree) f;
                if (t.subtrees().isEmpty()) {
                    CatalogExpander e = new CatalogExpander(nc, t, false, false) {
                        @Override
                        protected void onFinish(ZLNetworkException exception, boolean interrupted) {
                            super.onFinish(exception, interrupted);
                        }
                    };
                    e.run();
                }
                boolean e = expandCatalogs(all, t);
                if (!e)
                    all.add(t);
            }
        }
        return c;
    }

    void loadBooks() {
        books.clearTasks();
        books.refresh();
    }

    void loadtoolBar() {
        holder.searchtoolbar.removeAllViews();
        for (NetworkItemsLoader b : toolbarItems) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            final View t = inflater.inflate(R.layout.networktoolbar_item, null);
            ImageView iv = (ImageView) t.findViewById(R.id.toolbar_icon_image);
            iv.setImageResource(R.drawable.ic_sort_black_24dp);
            TextView tv = (TextView) t.findViewById(R.id.toolbar_icon_text);
            tv.setText(b.Tree.getName().trim());
            t.setTag(b);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectToolbar(t);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            holder.searchtoolbar.addView(t, lp);
        }
        if (holder.searchtoolbar.getChildCount() == 0)
            holder.toolbar.setVisibility(View.GONE);
        else
            holder.toolbar.setVisibility(View.VISIBLE);
    }

    void selectToolbar(View v) {
        final MainActivity main = (MainActivity) getActivity();
        final NetworkItemsLoader l = (NetworkItemsLoader) v.getTag();
        UIUtil.wait("loadingBookList", new Runnable() {
            @Override
            public void run() {
                try {
                    if (l.Tree.subtrees().isEmpty()) {
                        new CatalogExpander(l.NetworkContext, l.Tree, false, false).run();
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            getArguments().putString("toolbar", l.Tree.getUniqueKey().Id);
                            books.load(l.Tree.subtrees());
                            selectToolbar();
                            loadBooks();
                        }
                    });
                } catch (Exception e) {
                    ErrorDialog.Post(main, e);
                }
            }
        }, getContext());
    }

    void selectToolbar() {
        String id = getArguments().getString("toolbar");
        if (id == null)
            return;
        for (int i = 0; i < holder.searchtoolbar.getChildCount(); i++) {
            View v = holder.searchtoolbar.getChildAt(i);
            NetworkItemsLoader b = (NetworkItemsLoader) v.getTag();
            ImageButton k = (ImageButton) v.findViewById(R.id.toolbar_icon_image);
            if (b.Tree.getUniqueKey().Id.equals(id)) {
                int[] states = new int[]{
                        android.R.attr.state_checked,
                };
                k.setImageState(states, false);
            } else {
                int[] states = new int[]{
                        -android.R.attr.state_checked,
                };
                k.setImageState(states, false);
            }
        }
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
                        try {
                            final int old = searchCatalog.l.Tree.subtrees().size();
                            searchCatalog.resume();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (old == searchCatalog.l.Tree.subtrees().size())
                                        holder.footer.setVisibility(View.GONE);
                                    books.all = searchCatalog.l.Tree.subtrees();
                                    books.filter = null;
                                    books.refresh();
                                }
                            });
                        } catch (Exception e) {
                            ErrorDialog.Post(main, e);
                        }
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
                    final FBTree b = books.getItem(position);
                    if (b instanceof NetworkCatalogTree) {
                        UIUtil.wait("loadingBook", new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final NetworkCatalogTree tree = (NetworkCatalogTree) b;
                                    final NetworkItemsLoader l = new NetworkItemsLoader(nc, tree) {
                                        @Override
                                        protected void onFinish(ZLNetworkException exception, boolean interrupted) {
                                        }

                                        @Override
                                        protected void doBefore() throws ZLNetworkException {
                                        }

                                        @Override
                                        protected void load() throws ZLNetworkException {
                                        }
                                    };
                                    if (l.Tree.subtrees().isEmpty())
                                        new CatalogExpander(l.NetworkContext, l.Tree, false, false).run();
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (FBTree t : tree.subtrees()) {
                                                if (t instanceof NetworkBookTree) {
                                                    loadBook((NetworkBookTree) t);
                                                    return;
                                                }
                                            }
                                            ErrorDialog.Error(main, "Empty Url");
                                        }
                                    });
                                } catch (Exception e) {
                                    ErrorDialog.Post(main, e);
                                }
                            }
                        }, getContext());
                    }
                    if (b instanceof NetworkBookTree) {
                        loadBook((NetworkBookTree) b);
                    }
                } catch (RuntimeException e) {
                    ErrorDialog.Error(main, e);
                }
            }
        });
        holder.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final FBTree b = books.getItem(position);
                if (b instanceof NetworkBookTree) {
                    List<UrlInfo> ll = ((NetworkBookTree) b).Book.getAllInfos(UrlInfo.Type.Book);
                    if (ll.size() > 1) {
                        PopupMenu w = new PopupMenu(getContext(), view);
                        Menu menu = w.getMenu();
                        for (UrlInfo u : ll) {
                            MenuItem add = menu.add(getString(R.string.book_open) + " '" + formatMime(u.Mime.Name) + "'");
                            add.setIntent(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u.Url), u.Mime.Name));
                        }
                        w.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                Intent intent = item.getIntent();
                                loadBook(new UrlInfo(UrlInfo.Type.Book, intent.getData().toString(), MimeType.get(intent.getType())));
                                return true;
                            }
                        });
                        w.show();
                    }
                }
                return true;
            }
        });
        return v;
    }

    UrlInfo getUrl(final NetworkBookTree n, UrlInfo.Type t) {
        List<UrlInfo> ll = n.Book.getAllInfos(t);
        if (ll.size() == 0)
            return null;
        Collections.sort(ll, new MobileFirst());
        return ll.get(0);
    }

    void loadBook(final NetworkBookTree n) {
        final MainActivity main = (MainActivity) getActivity();
        UrlInfo u = getUrl(n, UrlInfo.Type.Book);
        if (u == null)
            u = getUrl(n, UrlInfo.Type.BookFullOrDemo);
        if (u == null) {
            u = getUrl(n, UrlInfo.Type.BookBuyInBrowser);
            if (u == null)
                u = getUrl(n, UrlInfo.Type.HtmlPage);
            String url = null;
            if (u == null) {
                if (n.Book.Id.startsWith(WebViewCustom.SCHEME_HTTP))
                    url = n.Book.Id;
            } else {
                url = u.Url;
            }
            if (url == null) {
                UIUtil.wait("loadingBook", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            n.Book.loadFullInformation(nc);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    BookDialog d = new BookDialog();
                                    d.a.myTree = n;
                                    d.a.myBook = n.Book;
                                    d.show(getFragmentManager(), "");
                                }
                            });
                        } catch (Exception e) {
                            ErrorDialog.Post(main, e);
                        }
                    }
                }, getContext());
                return;
            }
            openBrowser(url);
        } else {
            loadBook(u);
        }
    }

    void loadBook(UrlInfo u) {
        final MainActivity main = (MainActivity) getActivity();
        final Uri uri = Uri.parse(u.Url);
        final String mimetype = u.Mime.toString(); // gutenberg fake mimetypes when it want to open browser
        final MainActivity.ProgressDialog builder = new MainActivity.ProgressDialog(getContext());
        final AlertDialog d = builder.create();
        d.show();
        Thread t = new Thread("loading book") {
            @Override
            public void run() {
                try {
                    String contentDisposition = null;
                    long total;
                    InputStream is;
                    HttpClient client = new HttpClient() {
                        @Override
                        protected CloseableHttpClient build(HttpClientBuilder builder) {
                            if (useragent != null)
                                builder.setUserAgent(useragent);
                            return super.build(builder);
                        }
                    };
                    final HttpClient.DownloadResponse w = client.getResponse(null, uri.toString());
                    if (w.getError() != null)
                        throw new RuntimeException(w.getError() + ": " + uri);
                    if (w.contentDisposition != null) {
                        Pattern cp = Pattern.compile("filename=[\"]*([^\"]*)[\"]*");
                        Matcher cm = cp.matcher(w.contentDisposition);
                        if (cm.find())
                            contentDisposition = cm.group(1);
                    }
                    String wm = w.mimetype;
                    if (w.getResponse().getEntity().getContentType() != null && mimetype != null && !wm.equals(mimetype) && wm.startsWith("text")) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                BrowserDialogFragment b = BrowserDialogFragment.createHtml(uri.toString(), w.getHtml());
                                b.show(getFragmentManager(), "");
                            }
                        });
                        return;
                    }
                    is = new BufferedInputStream(w.getInputStream());
                    total = w.contentLength;
                    Storage.ProgresInputstream pis = new Storage.ProgresInputstream(is, total, builder.progress);
                    final Storage.Book book = storage.load(pis, uri); // not using Storage.load(uri). we have to download content first, then determine it type.
                    storage.load(book);
                    if (book.info.title == null || book.info.title.isEmpty() || book.info.title.equals(book.md5)) {
                        if (contentDisposition != null && !contentDisposition.isEmpty())
                            book.info.title = contentDisposition;
                        else
                            book.info.title = Storage.getNameNoExt(uri.getLastPathSegment());
                    }
                    Uri r = storage.recentUri(book);
                    if (!Storage.exists(getContext(), r))
                        storage.save(book);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            main.loadBook(book);
                        }
                    });
                } catch (Exception e) {
                    ErrorDialog.Post(main, e);
                } finally {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            d.cancel();
                        }
                    });
                }
            }
        };
        t.start();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadBooks();
        loadtoolBar();
        selectToolbar();
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
        final MainActivity main = (MainActivity) getActivity();
        if (searchCatalog == null) {
            books.filter = ss;
            books.refresh();
        } else {
            UIUtil.wait("search", new Runnable() {
                @Override
                public void run() {
                    try {
                        searchCatalog.search(ss);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                books.load(searchCatalog.l.Tree.subtrees());
                                books.refresh();
                            }
                        });
                    } catch (Exception e) {
                        ErrorDialog.Post(main, e);
                    }
                }
            }, getContext());
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        books.clearTasks();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
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
        MenuItem bookmarksMenu = menu.findItem(R.id.action_bm);
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
        bookmarksMenu.setVisible(false);
        mode.setVisible(false);
        sort.setVisible(false);

        host = n.home.get("get");
        if (host == null || host.isEmpty())
            searchMenu.setVisible(false);
        else
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
        if (def != null) {
            loadDefault();
        } else {
            books.refresh();
        }
    }

    @Override
    public String getHint() {
        if (searchCatalog == null)
            return getString(R.string.search_local);
        else
            return getString(R.string.search_network);
    }
}
