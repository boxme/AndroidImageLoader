package BackgroundWork;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * Created by desmond on 7/5/14.
 */
public abstract class ImageWorker {
    private static final String TAG = "ImageWorker";
    private static final int FADE_IN_TIME = 200;

    private ImageCache mImageCache;
    private ImageCache.ImageCacheParams mImageCacheParams;
    private Bitmap mLoadingBitmap;
    private boolean mFadeInBitmap = true;
    private boolean mExitTasksEarly = false;
    protected boolean mPauseWork = false;
    private final Object mPauseWorkLock = new Object();

    protected Resources mResources;
    protected Context mContext;

    private static final int MESSAGE_CLEAR = 0;
    private static final int MESSAGE_INIT_DISK_CACHE = 1;
    private static final int MESSAGE_FLUSH = 2;
    private static final int MESSAGE_CLOSE = 3;

    /**
     * Subclasses should override this to define any processing or work that must happen to produce
     * the final bitmap. This will be executed in a background thread and be long running. For
     * example, you could resize a large bitmap here, or pull down an image from the network.
     */
    protected abstract Bitmap processBitmap(Object data);


    protected ImageWorker(Context context) {
        mResources = context.getResources();
        mContext = context;
    }

    /**
     * Load an image specified by the data parameter into an ImageView
     * If the image is found in the memory cache, it is set immediately.
     * Otherwise, AsyncTask created to asynchronously load the bitmap
     */
    public void loadImage(Object data, ImageView imageView) {
        if (data == null) {return;}

        BitmapDrawable value = null;

        if (mImageCache != null) {
            value = mImageCache.getBitmapFromMemCache(String.valueOf(data));
        }

        if (value != null) {
            Log.i(TAG, "Bitmap found in memCache");
            imageView.setImageDrawable(value);

        } else if (cancelPotentialWork(data, imageView)) {
            final BitmapWorkerTask task = new BitmapWorkerTask(data, imageView);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(mResources, mLoadingBitmap, task);
            imageView.setImageDrawable(asyncDrawable);

            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Load circular image
     */
    public void loadCircularImage(Object data, ImageView imageView, int size, int borderWidth) {
        if (data == null) {return;}

        BitmapDrawable value = null;

        if (mImageCache != null) {
            value = mImageCache.getBitmapFromMemCache(String.valueOf(data));
        }

        if (value != null) {
            Log.i(TAG, "Bitmap found in memCache");
            imageView.setImageDrawable(value);

        } else if (cancelPotentialWork(data, imageView)) {
            final BitmapWorkerTask task = new BitmapWorkerTask(data, imageView, size, borderWidth);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(mResources, mLoadingBitmap, task);
            imageView.setImageDrawable(asyncDrawable);

            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void setLoadingImage(Bitmap bitmap) {
        mLoadingBitmap = bitmap;
    }

    public void setLoadingImage(int resId) {
        mLoadingBitmap = BitmapFactory.decodeResource(mResources, resId);
    }

    /**
     * Add ImageCache, initiating disk cache at the same time with a asynctask
     */
    public void addImageCache(FragmentManager fm, ImageCache.ImageCacheParams params) {
        mImageCacheParams = params;
        mImageCache = ImageCache.getInstance(fm, mImageCacheParams);
        new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
    }

    public void setImageFadeIn(boolean fadeIn) {
        mFadeInBitmap = fadeIn;
    }

    public void setExitTasksEarly(boolean exitTasksEarly) {
        mExitTasksEarly = exitTasksEarly;
        //Unpause all work before exiting to prevent paused threads from never finishing
        setPauseWork(false);
    }

    protected ImageCache getImageCache() {
        return mImageCache;
    }

    /**
     * Cancel any pending work attached to the provided ImageView
     */
    public static void cancelWork(ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            bitmapWorkerTask.cancel(true);
            final Object bitmapData = bitmapWorkerTask.mData;
            Log.i(TAG, "cancel work for " + bitmapData);
        }
    }

    /**
     * Returns true if the current work has been canceled or if there
     * was no work in progress on this imageView
     */
    public static boolean cancelPotentialWork(Object data, ImageView imageView) {

        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.mData;

            if (bitmapData == null || !bitmapData.equals(data)) {
                bitmapWorkerTask.cancel(true);
                Log.i(TAG, "Cancel potential work for " + data);
            } else {
                //The same work is already in progress
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieve the currently active work task (if any) associated with this imageview
     */
    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * Called when the processing is completed and the final drawable should be set
     * on the ImageView
     */
    private void setImageDrawable(ImageView imageView, Drawable drawable) {
        if (mFadeInBitmap) {
            //Transition drawable with a transparent drawable and the final drawable
            final TransitionDrawable td =
                    new TransitionDrawable(new Drawable[] {
                       new ColorDrawable(android.R.color.transparent), drawable
                    });
            //Set background to loading bitmap
            imageView.setBackgroundDrawable(new BitmapDrawable(mResources, mLoadingBitmap));

            imageView.setImageDrawable(td);
            td.startTransition(FADE_IN_TIME);
        } else {
            imageView.setImageDrawable(drawable);
        }
    }

    /**
     * The actual AsyncTask that will asynchronously process the image.
     */
    private class BitmapWorkerTask extends AsyncTask<Void, Void, BitmapDrawable> {
        private Object mData;
        private boolean mIsCircular;
        private int mCircleSize, mBorderWidth;
        private final WeakReference<ImageView> imageViewReference;

        public BitmapWorkerTask(Object data, ImageView imageView) {
            mData = data;
            mIsCircular = false;
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        public BitmapWorkerTask(Object data, ImageView imageView, int size, int borderWidth) {
            mData = data;
            mIsCircular = true;
            imageViewReference = new WeakReference<ImageView>(imageView);
            mCircleSize = size;
            mBorderWidth = borderWidth;
        }

        @Override
        protected BitmapDrawable doInBackground(Void... params) {
            final String dataString = String.valueOf(mData);
            Bitmap bitmap = null;
            BitmapDrawable drawable = null;

            //Wait here if work is paused and the task is not cancelled
            synchronized (mPauseWorkLock) {
                while (mPauseWork && !isCancelled()) {
                    try {
                        mPauseWorkLock.wait();
                    } catch (InterruptedException e) {}
                }
            }

            //If the imagecache is available & this task has not been cancelled by
            //another thread and the imageView that was originally bound to this task
            //is still bound bck to this task & "exit early" flag is not set then try
            //and fetch the bitmap from the cache
            if (mImageCache != null && !isCancelled() && getAttachedImageView() != null
                    && !mExitTasksEarly) {
                bitmap = mImageCache.getBitmapFromDiskCache(dataString);
            }

            //If the bitmap was not found in the cache and this task has not been cancelled by
            //another thread and the ImageView that was originally bound to this task is still
            //bounded back to this task and "exit early" flag is not set, then call the main
            //process method
            Log.i(TAG, dataString);

            if (bitmap == null && !isCancelled() && getAttachedImageView() != null
                    && !mExitTasksEarly) {
                bitmap = processBitmap(mData);
            }

            //If the bitmap was processed and the image cache is available, then add the processed
            //bitmap to the cache for future use. Note, we don't check if the task was cancelled here,
            //if it was, and the thread is still running, we may as well add the processed the bitmap to
            //our cache as it might be used again in the future
            if (bitmap != null) {
                if (mIsCircular) {
                    bitmap = getCircularBitmap(bitmap, mCircleSize, mCircleSize/2, mBorderWidth);
                }
                drawable = new RecyclingBitmapDrawable(mResources, bitmap);

                //Save to cache
                if (mImageCache != null) {
                    mImageCache.addBitmapToCache(dataString, drawable);
                }

            } else {
                Log.i(TAG, "Download has failed");
            }
            return drawable;
        }

        /**
         * Once image is processed, associated it to the imageview
         */
        @Override
        protected void onPostExecute(BitmapDrawable value) {
            if (isCancelled() || mExitTasksEarly) {
                value = null;
            }

            final ImageView imageView = getAttachedImageView();
            if (value != null && imageView != null) {
                setImageDrawable(imageView, value);
            }
        }

        @Override
        protected void onCancelled(BitmapDrawable value) {
            super.onCancelled(value);
            synchronized (mPauseWorkLock) {
                mPauseWorkLock.notifyAll();
            }
        }

        private ImageView getAttachedImageView() {
            final ImageView imageView = imageViewReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
            if (this == bitmapWorkerTask) {
                return imageView;
            }
            return null;
        }

        private Bitmap getCircularBitmap(Bitmap bitmap, int size,
                                         float radius, int borderWidth) {
            //Extract the bitmap to the desired size
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, size, size);

            //Draw the bitmap into a circle
            BitmapShader shader = new BitmapShader(bitmap, TileMode.CLAMP, TileMode.CLAMP);

            Paint drawImagePaint = new Paint();
            drawImagePaint.setAntiAlias(true);
            drawImagePaint.setShader(shader);

            RectF bitmapRect = new RectF(0.0f, 0.0f, size, size);

            // rect contains the bounds of the shape
            // radius is the radius in pixels of the rounded corners
            // paint contains the shader that will texture the shape
            Bitmap result = Bitmap.createBitmap(size, size, Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawCircle(size/2, size/2, radius, drawImagePaint);

            //Draw the border
            Paint borderPaint = new Paint();
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStrokeWidth(borderWidth);
            borderPaint.setAntiAlias(true);
            radius -= (borderWidth / 2);
            canvas.drawCircle(size/2, size/2, radius, borderPaint);

            return result;
        }


    }

    /**
     * Returns the imageview associated with this task as long as ImageView's task still
     * points to this task as well.
     */
    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskWeakReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskWeakReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskWeakReference.get();
        }
    }

    /**
     * Pause any ongoing background work. This can be used as a temporary
     * measure to improve performance. For example background work could
     * be paused when a ListView or GridView is being scrolled using a
     * {@link android.widget.AbsListView.OnScrollListener} to keep
     * scrolling smooth.
     * <p>
     * If work is paused, be sure setPauseWork(false) is called again
     * before your fragment or activity is destroyed (for example during
     * {@link android.app.Activity#onPause()}), or there is a risk the
     * background thread will never finish.
     */
    public void setPauseWork(boolean pauseWork) {
        synchronized (mPauseWorkLock) {
            mPauseWork = pauseWork;
            if (!mPauseWork) {
                mPauseWorkLock.notifyAll();
            }
        }
    }

    protected class CacheAsyncTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            switch ((Integer) params[0]) {
                case MESSAGE_CLEAR:
                    clearCacheInternal();
                    break;
                case MESSAGE_INIT_DISK_CACHE:
                    initDiskCacheInternal();
                    break;
                case MESSAGE_FLUSH:
                    flushCacheInternal();
                    break;
                case MESSAGE_CLOSE:
                    closeCacheInternal();
                    break;
            }
            return null;
        }
    }

    protected void initDiskCacheInternal() {
        if (mImageCache != null) {
            mImageCache.initDiskCache();
        }
    }

    /**
     * Clear both the disk and memory cache
     */
    protected void clearCacheInternal() {
        if (mImageCache != null) {
            mImageCache.clearCache();
        }
    }

    /**
     * Clear only the disk cache
     */
    protected void flushCacheInternal() {
        if (mImageCache != null) {
            mImageCache.flush();
        }
    }

    protected void closeCacheInternal() {
        if (mImageCache != null) {
            mImageCache.close();
            mImageCache = null;
        }
    }

    public void clearCache() {
        new CacheAsyncTask().execute(MESSAGE_CLEAR);
    }

    public void flushCache() {
        new CacheAsyncTask().execute(MESSAGE_FLUSH);
    }

    public void closeCache() {
        new CacheAsyncTask().execute(MESSAGE_CLOSE);
    }
}
