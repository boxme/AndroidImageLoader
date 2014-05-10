package com.replaid.efficientbitmap.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;

import com.androidquery.AQuery;

import Adapter.GalleryPhotoAdapter;
import BackgroundWork.ImageCache;
import BackgroundWork.ImageFetcher;

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";
    private GridView mGridView;
    private GalleryPhotoAdapter mAdapter;
    private ImageFetcher mImageLoader;
    private AQuery mAq;

    public static PhotoGalleryFragment newInstance() {
        PhotoGalleryFragment fragment = new PhotoGalleryFragment();
        return fragment;
    }
    public PhotoGalleryFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImageCache.ImageCacheParams cacheParams = new ImageCache.ImageCacheParams(getActivity(), "cache");
        mImageLoader = new ImageFetcher(getActivity());
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mImageLoader.addImageCache(fm, cacheParams);

        mImageLoader.setLoadingImage(R.drawable.empty_photo);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mGridView = (GridView) view.findViewById(R.id.gallery);
        mAdapter = new GalleryPhotoAdapter(getActivity(), mImageLoader);
        mGridView.setAdapter(mAdapter);
        mAdapter.refreshFromServer();

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
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}

        });
        return view;
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
    public void onDestroy() {
        super.onDestroy();
        mImageLoader.closeCache();
    }
}
