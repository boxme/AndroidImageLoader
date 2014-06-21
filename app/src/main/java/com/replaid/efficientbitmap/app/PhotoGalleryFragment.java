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
import ImageLoaderPackage.ImageCache;
import ImageLoaderPackage.ImageFetcher;

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";
    private GridView mGridView;
    private GalleryPhotoAdapter mAdapter;
    private ImageFetcher mImageLoader;
    private int mGridViewPosition;
    private Bundle mSavedState;

    public static PhotoGalleryFragment newInstance() {
        PhotoGalleryFragment fragment = new PhotoGalleryFragment();
        return fragment;
    }
    public PhotoGalleryFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "PhotoGalleryFragment onCreate");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        ImageCache.ImageCacheParams cacheParams = new ImageCache.ImageCacheParams(getActivity(), "cache");
        mImageLoader = new ImageFetcher(getActivity());
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mImageLoader.addImageCache(fm, cacheParams);

//        PhotoManager.init(getActivity());
        mAdapter = new GalleryPhotoAdapter(getActivity(), mImageLoader);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        Log.i(TAG, "PhotoGalleryFragment onCreateView");
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

//        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                final String data = mAdapter.getData();
//                FragmentManager fm = ((FragmentActivity) getActivity()).getSupportFragmentManager();
//                FragmentTransaction ft = fm.beginTransaction();
//                ft.replace(R.id.fragment_container, ViewPagerPhotoFragment.newInstance(data, position),
//                           "photoViewPager");
//                ft.addToBackStack(null);
//                ft.commit();
//            }
//        });

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


    /**
     * onViewStateRestored is also guaranteed to be called, restore
     * from your explicit state here.
     */
    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        Log.i(TAG, "PhotoGalleryFragment onViewStateRestored");
        super.onViewStateRestored(savedInstanceState);
        if (mSavedState != null) {
            mGridViewPosition = mSavedState.getInt("position", 0);
            Log.i(TAG, "State is restored to " + mGridViewPosition);
        }

        //This is not required for configuration changes
//        mGridView.smoothScrollToPosition(mGridViewPosition);
    }

    @Override
    public void onResume() {
        Log.i(TAG, "PhotoGalleryFragment onResume");
        super.onResume();
        mImageLoader.setExitTasksEarly(false);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "PhotoGalleryFragment onPause");
        super.onPause();
        mImageLoader.setPauseWork(false);
        mImageLoader.setExitTasksEarly(true);
        mImageLoader.flushCache();
//        PhotoManager.getInstance().clearCache();
    }

    @Override
    public void onAttach(Activity activity) {
        Log.i(TAG, "PhotoGalleryFragment onAttached");
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        Log.i(TAG, "PhotoGalleryFragment onDetached");
        super.onDetach();
    }

    /**
     * onDestroyView is guaranteed to be called, save an explicit state
     * here.
     */
    @Override
    public void onDestroyView() {
        Log.i(TAG, "PhotoGalleryFragment onDestroyView");
        if (mSavedState == null) {
            Log.i(TAG, "Create saved state");
            mSavedState = new Bundle();
        }
        mGridViewPosition = mGridView.getFirstVisiblePosition();
        mSavedState.putInt("position", mGridViewPosition);
        Log.i(TAG, "position is " + mGridViewPosition);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "PhotoGalleryFragment onDestroy");
        super.onDestroy();
        mImageLoader.closeCache();
//        PhotoManager.getInstance().closeCache();
    }

//    @Override
//    public void onSaveInstanceState(Bundle outState) {
//        super.onSaveInstanceState(outState);
//        Log.i(TAG, "fragment onSaveInstanceState");
//        mGridViewPosition = mGridView.getFirstVisiblePosition();
//        outState.putInt("position", mGridViewPosition);
//    }
}
