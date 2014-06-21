package Adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.replaid.efficientbitmap.app.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ImageLoaderPackage.ImageFetcher;
import ImageLoaderPackage.RecyclingImageView;

/**
 * Created by desmond on 9/5/14.
 */
public class GalleryPhotoAdapter extends BaseAdapter {
    private static final String TAG = "GalleryPhotoAdapter";
    private AQuery mAq;
    private ImageFetcher mImageLoader;
    private Context ctx;
    private JSONArray mData;

    public GalleryPhotoAdapter(Context context, ImageFetcher imageLoader) {
        ctx = context;
        mAq = new AQuery(context);
        mImageLoader = imageLoader;
    }

    @Override
    public int getCount() {
        if (mData == null) {
            return 0;
        }
        return mData.length();
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public Object getItem(int position) {
        JSONObject item = null;
        try {
            item = mData.getJSONObject(position);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return item;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = ((LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                                               .inflate(R.layout.gallery_photo, parent, false);
            holder = new ViewHolder();
            holder.imageView = (RecyclingImageView) convertView.findViewById(R.id.photo);
            convertView.setTag(holder);
        }

        holder = (ViewHolder) convertView.getTag();
        try {
            JSONObject obj = mData.getJSONObject(position);
            JSONObject image = obj.getJSONObject("image");
            String url = image.getString("url");
            mImageLoader.loadImage(url, holder.imageView, R.drawable.empty_photo);
//            holder.imageView.setImageURL(url, true, null);

//            if (position == 5) {
//                mImageLoader.loadCircularImage(url, holder.imageView, 190, 2);
//            } else {
//                mImageLoader.loadImage(url, holder.imageView);
//            }
        } catch (JSONException e) {}

        return convertView;
    }

    public void refreshFromServer() {
        String url = "http://api.qanvast.com/api/photos?skip=0&limit=500&latest=1";
        mAq.ajax(url, JSONArray.class, new AjaxCallback<JSONArray>() {
            @Override
            public void callback(String url, JSONArray object, AjaxStatus status) {
                Log.i(TAG, "code = " + status.getCode() + ", msg = " + status.getError());
                savedToDataBase(object);
            }
        });
    }

    private void savedToDataBase(JSONArray object) {
        Log.i(TAG, "number of photos " + object.length());
        mData = object;
        notifyDataSetChanged();
    }

    public String getData() {
        final String data = mData.toString();
        return data;
    }

    private class ViewHolder {
        public RecyclingImageView imageView;
    }
}
