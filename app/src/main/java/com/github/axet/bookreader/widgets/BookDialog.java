package com.github.axet.bookreader.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.github.axet.androidlibrary.widgets.DialogFragmentCompat;
import com.github.axet.bookreader.R;

import org.geometerplus.android.fbreader.network.NetworkBookInfoActivity;

public class BookDialog extends DialogFragmentCompat {

    public NetworkBookInfoActivity a = new NetworkBookInfoActivity() {
        @Override
        public WindowManager getWindowManager() {
            return getActivity().getWindowManager();
        }

        @Override
        public Context getApplicationContext() {
            return BookDialog.this.getContext();
        }

        @Override
        public View findViewById(int id) {
            return v.findViewById(id);
        }

        @NonNull
        @Override
        public LayoutInflater getLayoutInflater() {
            return LayoutInflater.from(getApplicationContext());
        }
    };

    @Override
    public void onCreateDialog(AlertDialog.Builder builder, Bundle savedInstanceState) {
        super.onCreateDialog(builder, savedInstanceState);
        builder.setTitle(a.myBook.Title);
        builder.setNeutralButton(getContext().getString(com.github.axet.androidlibrary.R.string.close),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                }
        );
    }

    @Override
    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.network_book, container);

        a.setupDescription();
        // a.setupExtraLinks();
        a.setupInfo();
        a.setupCover();
        // a.setupButtons();

        return v;
    }

}
