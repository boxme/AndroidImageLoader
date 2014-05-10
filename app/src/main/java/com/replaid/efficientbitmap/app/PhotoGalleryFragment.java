package com.replaid.efficientbitmap.app;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;

import Adapter.GalleryPhotoAdapter;
import BackgroundWork.ImageCache;
import BackgroundWork.ImageFetcher;

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "Gallery";
    private GridView mGridView;
    private GalleryPhotoAdapter mAdapter;
    private ImageFetcher mImageLoader;
    private int mGridViewPosition;

    public static PhotoGalleryFragment newInstance() {
        PhotoGalleryFragment fragment = new PhotoGalleryFragment();
        return fragment;
    }
    public PhotoGalleryFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Fragment onCreate");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        ImageCache.ImageCacheParams cacheParams = new ImageCache.ImageCacheParams(getActivity(), "cache");
        mImageLoader = new ImageFetcher(getActivity());
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mImageLoader.addImageCache(fm, cacheParams);
        mImageLoader.setLoadingImage(R.drawable.empty_photo);

        mAdapter = new GalleryPhotoAdapter(getActivity(), mImageLoader);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        Log.i(TAG, "Fragment onCreateView");
        mGridView = (GridView) view.findViewById(R.id.gallery);

        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                //Pause fetcher to ensure smoother scrolling when flinging
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    mImageLoader.setPauseWork(true);
                } else {
                    mImageLoader.setPauseWork(false);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

        });
        setupAdapter();
        return view;
    }

    private void setupAdapter() {
        if (getActivity() == null) return;

        if (mGridView.getAdapter() == null) {
            mGridView.setAdapter(mAdapter);
            mAdapter.refreshFromServer();
        } else {
            ((GalleryPhotoAdapter) mGridView.getAdapter())
                    .notifyDataSetChanged();
        }
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        Log.i(TAG, "Fragment onViewStateRestored");
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            mGridViewPosition = savedInstanceState.getInt("position", 0);
        }
        mGridView.smoothScrollToPosition(mGridViewPosition);
    }

    @Override
    public void onResume() {
        Log.i(TAG, "Fragment onResume");
        super.onResume();
        mImageLoader.setExitTasksEarly(false);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "Fragment onPause");
        super.onPause();
        mImageLoader.setPauseWork(false);
        mImageLoader.setExitTasksEarly(true);
        mImageLoader.flushCache();
    }

    @Override
    public void onAttach(Activity activity) {
        Log.i(TAG, "Fragment onAttached");
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        Log.i(TAG, "Fragment onDetached");
        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "Fragment onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Fragment onDestroy");
        super.onDestroy();
        mImageLoader.closeCache();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mGridViewPosition = mGridView.getFirstVisiblePosition();
        outState.putInt("position", mGridViewPosition);
    }
}
