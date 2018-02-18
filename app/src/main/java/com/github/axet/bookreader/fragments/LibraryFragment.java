package com.github.axet.bookreader.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.services.FileProvider;
import com.github.axet.androidlibrary.widgets.HeaderGridView;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.activities.MainActivity;
import com.github.axet.bookreader.app.MainApplication;
import com.github.axet.bookreader.app.Storage;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class LibraryFragment extends Fragment implements MainActivity.SearchListener {
    public static final String TAG = LibraryFragment.class.getSimpleName();

    LibraryAdapter books;
    Storage storage;
    String lastSearch = "";
    FragmentHolder holder;

    public static class FragmentHolder {
        HeaderGridView grid;

        public int layout;

        View toolbar;
        View searchpanel;
        LinearLayout searchtoolbar;
        View footer;
        View footerButtons;
        View footerNext;
        View footerProgress;
        View footerStop;
        View toolbarBottom;
        ImageView toolbar_list;

        Context context;

        public FragmentHolder(Context context) {
            this.context = context;
        }

        public void create(View v) {
            grid = (HeaderGridView) v.findViewById(R.id.grid);

            LayoutInflater inflater = LayoutInflater.from(context);

            toolbar = v.findViewById(R.id.search_header_toolbar_parent);
            searchpanel = v.findViewById(R.id.search_panel);
            searchtoolbar = (LinearLayout) v.findViewById(R.id.search_header_toolbar);

            toolbar.setVisibility(View.GONE);

            footer = inflater.inflate(R.layout.library_footer, null);
            footerButtons = footer.findViewById(R.id.search_footer_buttons);
            footerNext = footer.findViewById(R.id.search_footer_next);
            footerProgress = footer.findViewById(R.id.search_footer_progress);
            footerStop = footer.findViewById(R.id.search_footer_stop);
            toolbarBottom = v.findViewById(R.id.toolbar_bottom);

            toolbar_list = (ImageView) toolbarBottom.findViewById(R.id.toolbar_view);
            toolbar_list.setVisibility(View.VISIBLE);

            grid.addFooterView(footer);

            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

            toolbar_list.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor editor = shared.edit();
                    if (layout == R.layout.book_list_item) {
                        editor.putString(MainApplication.PREFERENCE_LIBRARY_LAYOUT, "book_item");
                    } else {
                        editor.putString(MainApplication.PREFERENCE_LIBRARY_LAYOUT, "book_list_item");
                    }
                    editor.commit();
                    updateGrid();
                }
            });
            updateGrid();
        }

        void updateGrid() {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

            layout = R.layout.book_item;
            if (shared.getString(MainApplication.PREFERENCE_LIBRARY_LAYOUT, "").equals("book_list_item")) {
                grid.setNumColumns(1);
                layout = R.layout.book_list_item;
            } else {
                grid.setNumColumns(4);
                layout = R.layout.book_item;
            }

            BooksAdapter a = (BooksAdapter) grid.getAdapter();
            if (a != null)
                a.notifyDataSetChanged();
            updateToolbar();
        }

        public void updateToolbar() {
            if (layout == R.layout.book_item) {
                toolbar_list.setImageResource(R.drawable.ic_view_module_black_24dp);
            } else {
                toolbar_list.setImageResource(R.drawable.ic_view_list_black_24dp);
            }
        }
    }

    public static class BookViewHolder {
        public Bitmap bm;
        public HashSet<ImageView> views = new HashSet<>(); // one task can set multiple ImageView's, except reused ones;
        public HashSet<ProgressBar> progress = new HashSet<>();
        public boolean done;

        public BookViewHolder() {
        }

        public BookViewHolder(ProgressBar p, ImageView i) {
            progress.add(p);
            views.add(i);
        }
    }

    public static class DownloadImageTask extends AsyncTask<Uri, Void, Bitmap> {
        BookViewHolder book;

        public DownloadImageTask(BookViewHolder b) {
            book = b;
        }

        protected Bitmap doInBackground(Uri... urls) {
            Uri u = urls[0];
            Bitmap bm = null;
            try {
                String s = u.getScheme();
                if (s.startsWith(WebViewCustom.SCHEME_HTTP)) {
                    InputStream in = new URL(u.toString()).openStream();
                    bm = BitmapFactory.decodeStream(in);
                } else {
                    bm = BitmapFactory.decodeFile(u.getPath());
                }
            } catch (Exception e) {
                Log.e(TAG, "broken download", e);
            }
            return bm;
        }

        protected void onPostExecute(Bitmap result) {
            book.done = true;
            for (ProgressBar p : book.progress)
                p.setVisibility(View.GONE);
            book.bm = result;
            if (book.bm == null)
                return;
            for (ImageView v : book.views)
                v.setImageBitmap(book.bm);
        }
    }

    public static class ByRecent implements Comparator<Storage.Book> {

        @Override
        public int compare(Storage.Book o1, Storage.Book o2) {
            return Long.valueOf(o2.info.last).compareTo(o1.info.last);
        }

    }

    public static class ByCreated implements Comparator<Storage.Book> {

        @Override
        public int compare(Storage.Book o1, Storage.Book o2) {
            return Long.valueOf(o1.info.created).compareTo(o2.info.created);
        }

    }

    public class LibraryAdapter extends BooksAdapter {
        ArrayList<Storage.Book> list = new ArrayList<>();

        public LibraryAdapter() {
            super(getContext());
        }

        @Override
        public int getLayout() {
            return holder.layout;
        }

        public Uri getCover(int position) {
            Storage.Book b = list.get(position);
            if (b.cover != null)
                return Uri.fromFile(b.cover);
            return null;
        }

        @Override
        public String getAuthors(int position) {
            Storage.Book b = list.get(position);
            return b.info.authors;
        }

        @Override
        public String getTitle(int position) {
            Storage.Book b = list.get(position);
            return b.info.title;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Storage.Book getItem(int position) {
            return list.get(position);
        }

        public void refresh() {
            list.clear();
            ArrayList<Storage.Book> ll = storage.list();
            if (filter == null || filter.isEmpty()) {
                list = ll;
                views.clear();
                images.clear();
            } else {
                for (Storage.Book b : ll) {
                    if (b.info.title.toLowerCase(Locale.US).contains(filter.toLowerCase(Locale.US))) {
                        list.add(b);
                    }
                }
            }
            Collections.sort(list, new ByCreated());
            notifyDataSetChanged();
        }
    }

    public static abstract class BooksAdapter implements ListAdapter {
        Map<Uri, BookViewHolder> views = new TreeMap<>();
        Map<ImageView, BookViewHolder> images = new HashMap<>();
        DataSetObserver listener;
        String filter;
        Context context;

        public BooksAdapter(Context context) {
            this.context = context;
        }

        public Uri getCover(int position) {
            return null;
        }

        public int getLayout() {
            return -1;
        }

        public String getAuthors(int position) {
            return "";
        }

        public String getTitle(int position) {
            return "";
        }

        public void refresh() {
        }

        public void notifyDataSetChanged() {
            if (listener != null)
                listener.onChanged();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            listener = observer;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            listener = null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View book = inflater.inflate(getLayout(), null, false);
            ImageView image = (ImageView) book.findViewById(R.id.book_cover);
            TextView aa = (TextView) book.findViewById(R.id.book_authors);
            TextView tt = (TextView) book.findViewById(R.id.book_title);
            ProgressBar progress = (ProgressBar) book.findViewById(R.id.book_progress);
            progress.setVisibility(View.GONE);

            Uri cover = getCover(position);

            if (cover != null) {
                BookViewHolder task = images.get(image);
                if (task != null) { // reuse image
                    task.views.remove(image);
                    task.progress.remove(progress);
                }
                task = views.get(cover);
                if (task != null) { // add new ImageView to populate on finish
                    task.views.add(image);
                    task.progress.add(progress);
                }
                if (task == null) {
                    task = new BookViewHolder(progress, image);
                    views.put(cover, task);
                    images.put(image, task);
                    new LibraryFragment.DownloadImageTask(task).execute(cover);
                } else if (task.bm != null) {
                    image.setImageBitmap(task.bm);
                }
                for (ProgressBar p : task.progress) {
                    p.setVisibility(task.done ? View.GONE : View.VISIBLE);
                }
            }

            setText(aa, getAuthors(position));
            setText(tt, getTitle(position));

            return book;
        }

        void setText(TextView t, String s) {
            if (t == null)
                return;
            if (s == null || s.isEmpty()) {
                t.setVisibility(View.GONE);
                return;
            }
            t.setVisibility(View.VISIBLE);
            t.setText(s);
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }
    }

    public LibraryFragment() {
    }

    public static LibraryFragment newInstance() {
        LibraryFragment fragment = new LibraryFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storage = new Storage(getContext());
        holder = new FragmentHolder(getContext());
        books = new LibraryAdapter();
        books.refresh();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_library, container, false);

        holder.create(v);
        holder.footer.setVisibility(View.GONE);

        final MainActivity main = (MainActivity) getActivity();
        main.toolbar.setTitle(R.string.app_name);
        holder.grid.setAdapter(books);
        holder.grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Storage.Book b = books.getItem(position);
                main.loadBook(b);
            }
        });

        holder.grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final Storage.Book b = books.getItem(position);
                PopupMenu popup = new PopupMenu(getContext(), view);
                popup.inflate(R.menu.bookitem_menu);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.action_rename) {
                            final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
                            e.setTitle(R.string.book_rename);
                            e.setText(b.info.title);
                            e.setPositiveButton(new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String name = e.getText();
                                    b.info.title = name;
                                    storage.save(b);
                                    books.notifyDataSetChanged();
                                }
                            });
                            AlertDialog d = e.create();
                            d.show();
                        }
                        if (item.getItemId() == R.id.action_open) {
                            String ext = Storage.getExt(b.file);
                            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                            String name = Storage.getNameNoExt(b.file);
                            Uri uri = FileProvider.getUriForFile(getContext(), type, name, b.file);
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(uri, type);
                            FileProvider.grantPermissions(getContext(), intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            startActivity(intent);
                        }
                        if (item.getItemId() == R.id.action_share) {
                            String ext = Storage.getExt(b.file);
                            String type = Storage.getTypeByName(b.file.getName());
                            Uri uri = FileProvider.getUriForFile(getContext(), type, b.info.title + "." + ext, b.file);
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setType(type);
                            intent.putExtra(Intent.EXTRA_EMAIL, "");
                            intent.putExtra(Intent.EXTRA_SUBJECT, b.info.title + "." + ext);
                            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shared_via, getString(R.string.app_name)));
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            FileProvider.grantPermissions(getContext(), intent, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            startActivity(intent);
                        }
                        if (item.getItemId() == R.id.action_delete) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setTitle(R.string.book_delete);
                            builder.setMessage(R.string.are_you_sure);
                            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            });
                            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    storage.delete(b);
                                    books.refresh();
                                }
                            });
                            builder.show();
                        }
                        return true;
                    }
                });
                popup.show();
                return true;
            }
        });

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        MainActivity main = ((MainActivity) getActivity());
        main.setFullscreen(false);
        main.clearMenu();
        main.libraryMenu.setChecked(true);
        books.refresh();
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
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem homeMenu = menu.findItem(R.id.action_home);
        MenuItem tocMenu = menu.findItem(R.id.action_toc);
        MenuItem searchMenu = menu.findItem(R.id.action_search);
        MenuItem reflow = menu.findItem(R.id.action_reflow);
        reflow.setVisible(false);
        searchMenu.setVisible(true);
        homeMenu.setVisible(false);
        tocMenu.setVisible(false);
    }

    public void search(String s) {
        books.filter = s;
        books.refresh();
        lastSearch = books.filter;
    }

    @Override
    public void searchClose() {
        search("");
    }

    @Override
    public String getHint() {
        return getString(R.string.search_local);
    }
}
