package Adapter;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import BackgroundWork.ImageFetcher;
import BackgroundWork.RecyclingImageView;

/**
 * Created by desmond on 11/5/14.
 */
public class ViewPagerPhotoAdapter extends PagerAdapter {
    private static final String TAG = "ViewPagerPhotoAdapter";
    private JSONArray mData;
    private Context ctx;
    private ImageFetcher mImageLoader;

    public ViewPagerPhotoAdapter(Context context, JSONArray data,
                                 ImageFetcher imageLoader) {
        ctx = context;
        mData = data;
        mImageLoader = imageLoader;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == ((RecyclingImageView) object);
    }

    @Override
    public int getCount() {
        if (mData == null) return 0;

        return mData.length();
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        ((ViewPager) container).removeView((RecyclingImageView) object);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final RecyclingImageView imageView = new RecyclingImageView(ctx);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        try {
            final JSONObject obj = mData.getJSONObject(position);
            final JSONObject image = obj.getJSONObject("image");
            final String url = image.getString("url");
            mImageLoader.loadImage(url, imageView);

        } catch (JSONException e) {}

        ((ViewPager) container).addView(imageView);

        return imageView;
    }
}
