package ImageLoaderPackage;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by desmond on 7/5/14.
 */
public class ImageCache {
    private static final String TAG = "ImageCache";

    // Default memory cache size in kilobytes
    private static final int DEFAULT_MEM_CACHE_SIZE = (int) ((Runtime.getRuntime().maxMemory() / 1024) / 7); // 1/7 of the max size available

    // Default disk cache size in bytes
    private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB

    // Compression settings when writing images to disk cache
    private static final CompressFormat DEFAULT_COMPRESS_FORMAT = CompressFormat.JPEG;
    private static final int DEFAULT_COMPRESS_QUALITY = 70;
    private static final int DISK_CACHE_INDEX = 0;

    //Constants to easily toggle various caches
    private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;
    private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;
    private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE = false;

    private DiskLruCache mDiskLruCache;
    private LruCache<String, BitmapDrawable> mMemoryCache;
    private ImageCacheParams mCacheParams;
    private final Object mDiskCacheLock = new Object();
    private final Object mMemCacheLock = new Object();
    private boolean mDiskCacheStarting = true;
    private Set<SoftReference<Bitmap>> mReusableBitmaps;

    /**
     * ImageCache object across configuration changes such as a change in device orientation.
     */
    public static ImageCache getInstance(
            FragmentManager fragmentManager, ImageCacheParams cacheParams) {

        //Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

        //See if we already have an ImageCache stored in RetainFragment
        ImageCache imageCache = (ImageCache) mRetainFragment.getObject();

        //No existing imagecache, create one and store it in RetainFragment
        if (imageCache == null) {
            imageCache = new ImageCache(cacheParams);
            mRetainFragment.setObject(imageCache);
        }

        return imageCache;
    }

    /**
     * Create a new ImageCache object using the specified parameters. This
     * should not be called directly by other classes.
     * @param cacheParams
     */
    private ImageCache(ImageCacheParams cacheParams) {
        init(cacheParams);
    }

    /**
     * Initialize the cache, providing all parameters
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void init(ImageCacheParams cacheParams) {
        mCacheParams = cacheParams;

        //Set up memory cache
        if (mCacheParams.memoryCacheEnabled) {

            if (BackgroundUtils.hasHoneycomb()) {
                mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
            }

            mMemoryCache = new LruCache<String, BitmapDrawable>(mCacheParams.memCacheSize) {
                /**
                 * Notify the removed entry that is no longer being cached
                  */
                @Override
                protected void entryRemoved(boolean evicted, String key, BitmapDrawable oldValue, BitmapDrawable newValue) {
                    if (RecyclingBitmapDrawable.class.isInstance(oldValue)) {
                        // The removed entry is a recycling drawable, so notify it
                        // that it has been removed from the memory cache
                        ((RecyclingBitmapDrawable) oldValue).setIsCached(false);
                    } else {
                        // The removed entry is a standard BitmapDrawable
                        if (BackgroundUtils.hasHoneycomb()) {
                            // We're running on Honeycomb or later, so add the bitmap
                            // to a SoftReference set for possible use with inBitmap later
                            addBitmapIntoReusableSet(oldValue.getBitmap());
                        }
                    }
                }

                @Override
                protected int sizeOf(String key, BitmapDrawable value) {
                    final int bitmapSize = getBitmapSize(value) / 1024;
//                    return bitmapSize == 0 ? 1 : bitmapSize;
                    return  bitmapSize;
                }
            };
        }

        // By default the disk cache is not initialized here as it should be initialized
        // on a separate thread due to disk access.
        if (cacheParams.initDiskCacheOnCreate) {
            // Set up disk cache
            initDiskCache();
        }
    }

    /**
     * Initializes the disk cache.  Note that this includes disk access so this should not be
     * executed on the main/UI thread. By default an ImageCache does not initialize the disk
     * cache when it is created, instead you should call initDiskCache() to initialize it on a
     * background thread.
     */
    public void initDiskCache() {
        //Set up DiskCache
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
                File diskCacheDir = mCacheParams.diskCacheDir;
                if (mCacheParams.diskCacheEnabled && diskCacheDir != null) {
                    if (!diskCacheDir.exists()) {
                        diskCacheDir.mkdirs();
                    }

                    if (getUsableSpace(diskCacheDir) >= mCacheParams.diskCacheSize) {
                        try {
                            mDiskLruCache = DiskLruCache.open(
                                    diskCacheDir, 1, 1, mCacheParams.diskCacheSize);
                            Log.i(TAG, "Disk cache initialized");
                        } catch (IOException e) {
                            mCacheParams.diskCacheDir = null;
                        }
                    }
                }
            }
            mDiskCacheStarting = false;
            mDiskCacheLock.notifyAll();
        }
    }

    /**
     * Add a bitmap to both memory and disk cache
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public void addBitmapToCache(String data, BitmapDrawable value) {
        if (data == null || value == null) {return;}

        //Add to memory cache
        if (mMemoryCache != null) {
            if (RecyclingBitmapDrawable.class.isInstance(value)) {
                //The removed entry is a recycling drawable, so notify it
                //that it has been added into the memory cache
                ((RecyclingBitmapDrawable) value).setIsCached(true);
            }
            mMemoryCache.put(data, value);
        }

        synchronized (mDiskCacheLock) {

            //Add to disk cache
            if (mDiskLruCache != null) {
                final String key = hashKeyforDisk(data);
                OutputStream out = null;
                try {
                    DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                    if (snapshot == null) {
                        final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                        if (editor != null) {
                            out = editor.newOutputStream(DISK_CACHE_INDEX);
                            value.getBitmap().compress(mCacheParams.compressFormat,
                                    mCacheParams.compressQuality, out);
                            editor.commit();
                            out.close();
                        }
                    } else {
                        snapshot.getInputStream(DISK_CACHE_INDEX).close();
                    }
                } catch (IOException e) {
                    Log.i(TAG, "addBitmapToCache error - " + e);
                } catch (Exception e) {
                    Log.i(TAG, "addBitmapToCache error - " + e);
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (Exception e) {}
                }
            }
        }
    }

    /**
     * Get from memory cache
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public BitmapDrawable getBitmapFromMemCache(String data) {
        BitmapDrawable memValue = null;

        if (mMemoryCache != null) {
            memValue = mMemoryCache.get(data);
        }

        return memValue;
    }

    /**
     * Get from diskCache
     */
    public Bitmap getBitmapFromDiskCache(String data) {
        final String key = hashKeyforDisk(data);
        Bitmap bitmap = null;

        synchronized (mDiskCacheLock) {
            while (mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait();
                } catch (InterruptedException e) {}
            }

            if (mDiskLruCache != null) {
                InputStream inputStream = null;
                try {
                    final DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                    if (snapshot != null) {
                        Log.i(TAG, "Bitmap found in Disk cache");

                        inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);

                        if (inputStream != null) {
                            FileDescriptor fd = ((FileInputStream) inputStream).getFD();

                            //Decode bitmap, but we don't want to sample so give MAX_VALUE as the
                            //target dimensions
                            bitmap = ImageResizer.decodeSampledBitmapFromDescriptor(
                                    fd, Integer.MAX_VALUE, Integer.MAX_VALUE, this);
                        }
                    }
                } catch (IOException e) {
                    Log.i(TAG, "getBitmapFromDiskCache - " + e);
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (IOException e) {}
                }
            }
            return bitmap;
        }
    }

    protected void addBitmapIntoReusableSet(final Bitmap bitmap) {
        if (mReusableBitmaps != null) {
            synchronized (mReusableBitmaps) {
                if (bitmap != null) {
                    mReusableBitmaps.add(new SoftReference<Bitmap>(bitmap));
                }
            }
        }
    }

    protected Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
        Bitmap bitmap = null;

        if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
            synchronized (mReusableBitmaps) {
                final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps.iterator();
                Bitmap item;

                while (iterator.hasNext()) {
                    item = iterator.next().get();

                    if (item != null && item.isMutable()) {
                        //Check to see if the item can be used for inBitmap
                        if (canUseForInBitmap(item, options)) {
                            bitmap = item;

                            //Remove from reusable set so it can't be used again
                            iterator.remove();
                            break;
                        }
                    } else {
                        //Remove from the set if the reference has been cleared
                        iterator.remove();
                    }
                }
            }
        }
        return bitmap;
    }

    /**
     * Clears both memory and disk cache associated with this ImageCache Object.
     * This includes disk access, so this should not be executed
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public void clearCache() {
        if (mMemoryCache != null) {
            mMemoryCache.evictAll();
            Log.i(TAG, "Memory cache cleared");
        }

        synchronized (mDiskCacheLock) {
            mDiskCacheStarting = true;
            if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
                try {
                    mDiskLruCache.delete();
                    Log.i(TAG, "Disk cache cleared");
                } catch (IOException e) {
                    Log.i(TAG, "clear diskcache e - " + e);
                }
                mDiskLruCache = null;
                initDiskCache();
            }
        }
    }

    /**
     * Flushes the disk cache associated with this ImageCache object.
     * Includes disk access, so this should not be executed on the main thread
     */
    public void flush() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                try {
                    mDiskLruCache.flush();
                    Log.i(TAG, "Disk cache flushed");
                } catch (IOException e) {
                    Log.e(TAG, "flush disk cache e -" + e);
                }
            }
        }
    }

    /**
     * Closes the disk cache associated with this ImageCache object.
     * Includes disk access
     */
    public void close() {
        synchronized (mDiskCacheLock) {
            if (mDiskLruCache != null) {
                try {
                    if (!mDiskLruCache.isClosed()) {
                        mDiskLruCache.close();
                        mDiskLruCache = null;
                        Log.i(TAG, "Disk cache closed");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "close disk cache - " + e);
                }
            }
        }
    }

    /**
     * A holder class that contains cache parameters
     */
    public static class ImageCacheParams {
        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;
        public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;
        public File diskCacheDir;
        public CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
        public int compressQuality = DEFAULT_COMPRESS_QUALITY;
        public boolean memoryCacheEnabled = DEFAULT_MEM_CACHE_ENABLED;
        public boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;
        public boolean initDiskCacheOnCreate = DEFAULT_INIT_DISK_CACHE_ON_CREATE;

        public ImageCacheParams(Context context, String diskCacheDirectoryName) {
            diskCacheDir = getDiskCacheDir(context, diskCacheDirectoryName);
        }

        /**
         * Change the default memcache size (optional)
         * @param percent
         */
        public void setMemCacheSizePercentage(float percent) {
            if (percent < 0.01f || percent > 0.8f) {
                throw new IllegalArgumentException("setMemCacheSizePercent - percent must be "
                        + "between 0.01 and 0.8 (inclusive)");
            }
            memCacheSize = Math.round(percent * Runtime.getRuntime().maxMemory() / 1024);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static boolean canUseForInBitmap(final Bitmap candidate, BitmapFactory.Options targetOptions) {

        if (!BackgroundUtils.hasKitKat()) {
            //On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
            return candidate.getWidth() == targetOptions.outWidth
                    && candidate.getHeight() == targetOptions.outHeight
                    && targetOptions.inSampleSize == 1;
        }

        //From KitKat onward, can re-use if the byte size of the new bitmap is smaller
        //than reusable bitmap candidate allocation byte count
        int width = targetOptions.outWidth / targetOptions.inSampleSize;
        int height = targetOptions.outHeight / targetOptions.inSampleSize;
        int byteCount = width * height * getBytesPerPixel(candidate.getConfig());
        return byteCount <= candidate.getAllocationByteCount();
    }

    /**
     * Return the byte usage per pixel of a bitmap based on its configuration
     */
    private static int getBytesPerPixel(Config config) {
        if (config == Config.ARGB_8888) {
            return 4;
        } else if (config == Config.RGB_565) {
            return 2;
        } else if (config == Config.ARGB_4444) {
            return 2;
        } else if (config == Config.ALPHA_8) {
            return 1;
        }
        return 1;
    }

    /**
     * Get an usable directory (external if possible, internal otherwise)
     */
    public static File getDiskCacheDir(Context context, String uniqueName) {
        //Check if media is mounted or storage is built-in, if so, try and use
        //external cache dir. Otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? getExternalCacheDir(context).getPath() :
                        context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    public static boolean isExternalStorageRemovable() {
        if (BackgroundUtils.hasGingerbread()) {
            return Environment.isExternalStorageRemovable();
        }
        return true;
    }

    /**
     * Get the external app cache directory
     */
    public static File getExternalCacheDir(Context context) {
        if (BackgroundUtils.hasFroyo()) {
            return context.getExternalCacheDir();
        }

        //Before Froyo, we need to construct the external cache dir ourselves
        final String cacheDir = "Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    /**
     * A hashing method that changes a string (like a URL) into a hash suitable for using as a
     * disk filename.
     */
    public static String hashKeyforDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDisgest = MessageDigest.getInstance("MD5");
            mDisgest.update(key.getBytes());
            cacheKey = bytesToHexString(mDisgest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private static String bytesToHexString(byte[] bytes) {
        // http://stackoverflow.com/questions/332079
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Get the size in bytes of a bitmap in a BitmapDrawable. Note that from KitKat
     * onward, this returns the allocated memory size of the bitmap which can be
     * larger than the actual bitmap data byte count
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static int getBitmapSize(BitmapDrawable value) {
        Bitmap bitmap = value.getBitmap();

        if (BackgroundUtils.hasKitKat()) {
            return bitmap.getAllocationByteCount();
        }

        if (BackgroundUtils.hasHoneycombMR1()) {
            return bitmap.getByteCount();
        }

        //Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    /**
     * Check how much usable space is available at a given path
     */
    public static long getUsableSpace(File path) {
        if (BackgroundUtils.hasGingerbread()) {
            return path.getUsableSpace();
        }
        //StatFs can retrieve overall info about the space on a filesystem
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

    /**
     * Locate an existing instance of this fragment, if not found, create a new one
     */
    private static RetainFragment findOrCreateRetainFragment(FragmentManager fm) {
        RetainFragment mRetainFragment = (RetainFragment) fm.findFragmentByTag(TAG);

        //If not retained (or first time running), we need to create and add it
        if (mRetainFragment == null) {
            mRetainFragment = new RetainFragment();
            fm.beginTransaction().add(mRetainFragment, TAG).commitAllowingStateLoss();
        }

        return mRetainFragment;
    }

    /**
     * A simple non-UI Fragment that stores a single Object and is retained over
     * configuration chagnes. It will be used to retain the ImageCache Object
     */
    public static class RetainFragment extends Fragment {
        private Object mObject;

        public RetainFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            //Make sure this Fragment is retained over a configuration change
            setRetainInstance(true);
        }

        /**
         * Store a single object in this fragment
         */
        public void setObject(Object object) {
            mObject = object;
        }

        /**
         * Get the stored object
         */
        public Object getObject() {
            return mObject;
        }
    }
}
