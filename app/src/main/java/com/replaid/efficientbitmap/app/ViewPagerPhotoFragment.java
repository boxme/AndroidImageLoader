package com.replaid.efficientbitmap.app;



import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;

import Adapter.ViewPagerPhotoAdapter;
import ImageLoaderPackage.ImageCache;
import ImageLoaderPackage.ImageFetcher;


/**
 * A simple {@link Fragment} subclass.
 *
 */
public class ViewPagerPhotoFragment extends Fragment {
    private static final String TAG = "ViewPagerPhotoFragment";
    private ViewPager mViewPager;
    private ViewPagerPhotoAdapter mAdapter;
    private ImageFetcher mImageLoader;
    private int mPosition;
    private JSONArray mData;

    public static ViewPagerPhotoFragment newInstance(String data, int position) {
        ViewPagerPhotoFragment fragment = new ViewPagerPhotoFragment();
        Bundle args = new Bundle();
        args.putInt("position", position);
        args.putString("data", data);
        fragment.setArguments(args);
        return fragment;
    }

    public ViewPagerPhotoFragment() {}

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewPager.setCurrentItem(mPosition);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (getArguments() != null) {
            retrieveInfoFromArg();
        } else {
            mData = null;
            mPosition = 0;
        }

        ImageCache.ImageCacheParams cacheParams = new ImageCache.ImageCacheParams(getActivity(), "cache");
        mImageLoader = new ImageFetcher(getActivity());
        FragmentManager fm = ((FragmentActivity) getActivity()).getSupportFragmentManager();
        mImageLoader.addImageCache(fm, cacheParams);

        mAdapter = new ViewPagerPhotoAdapter(getActivity(), mData, mImageLoader);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_pager_photo, container, false);
        mViewPager = (ViewPager) view.findViewById(R.id.pager);
        setupAdapter();
        return view;
    }

    private void setupAdapter() {
        if (getActivity() == null) return;

        if (mViewPager.getAdapter() == null) {
            mViewPager.setAdapter(mAdapter);
        } else {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void retrieveInfoFromArg() {
        mPosition = getArguments().getInt("position", 0);
        final String dataString = getArguments().getString("data");
        try {
            if (dataString != null && !dataString.isEmpty()) {
                mData = new JSONArray(dataString);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mImageLoader.setExitTasksEarly(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageLoader.setPauseWork(false);
        mImageLoader.setExitTasksEarly(true);
        mImageLoader.flushCache();
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "fragment view destroyed");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "fragment destroyed");
        super.onDestroy();
        mImageLoader.closeCache();
    }
}
