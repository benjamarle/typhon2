package com.github.axet.bookreader.activities;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.widget.Toolbar;

import com.github.axet.androidlibrary.activities.AppCompatFullscreenThemeActivity;
import com.github.axet.bookreader.R;
import com.github.axet.bookreader.app.BookApplication;

import java.util.List;

public class FullscreenActivity extends AppCompatFullscreenThemeActivity {
    public DrawerLayout drawer;
    public NavigationView navigationView;
    public Toolbar toolbar;

    public interface FullscreenListener {
        void onFullscreenChanged(boolean f);

        void onUserInteraction();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public int getAppTheme() {
        return BookApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    public int getAppThemePopup() {
        return BookApplication.getTheme(this, R.style.AppThemeLight_PopupOverlay, R.style.AppThemeDark_PopupOverlay);
    }

    @SuppressLint({"InlinedApi", "RestrictedApi"})
    @Override
    public void setFullscreen(boolean b) {
        super.setFullscreen(b);
        FragmentManager fm = getSupportFragmentManager();
        List<Fragment> ff = fm.getFragments();
        if (ff != null) {
            for (Fragment f : ff) {
                if (f instanceof FullscreenActivity.FullscreenListener)
                    ((FullscreenActivity.FullscreenListener) f).onFullscreenChanged(b);
            }
        }
    }

    @Override
    public void hideSystemUI() {
        super.hideSystemUI();
        if (Build.VERSION.SDK_INT >= 14)
            drawer.setFitsSystemWindows(false);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void showSystemUI() {
        super.showSystemUI();
        if (Build.VERSION.SDK_INT >= 14)
            drawer.setFitsSystemWindows(true);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        FragmentManager fm = getSupportFragmentManager();
        List<Fragment> ff = fm.getFragments();
        if (ff != null) {
            for (Fragment f : ff) {
                if (f instanceof FullscreenListener)
                    ((FullscreenListener) f).onUserInteraction();
            }
        }
    }
}
