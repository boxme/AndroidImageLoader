package com.replaid.efficientbitmap.app;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = "Gallery";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Activity onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupFragment();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        Log.i(TAG, "Activity onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupFragment() {
        FragmentManager fm = getSupportFragmentManager();
        PhotoGalleryFragment fragment;

        fragment = (PhotoGalleryFragment) fm.findFragmentByTag("photo_gallery");
        if (fragment == null) {
            Log.i(TAG, "Fragment not found in fragment manager");
            fragment = PhotoGalleryFragment.newInstance();
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment, "photo_gallery").commit();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "Activity onDestroy");
        super.onDestroy();
    }
}
