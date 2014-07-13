package BackgroundThreads;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import ImageLoaderPackage.BackgroundUtils;

/**
 * Created by desmond on 19/6/14.
 */
public class ImageCache  {
    private static final String TAG = "ImageCache";

    //Default memory cache size in kilobytes
    private static final int DEFAULT_MEM_CACHE_SIZE = (int) ((Runtime.getRuntime().maxMemory() / 1024) / 7);

    //Default disk cache size in bytes
    private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 10;

    //Default buffer size for reading input stream
    private static final int READ_SIZE = 1024 * 2;

    // Compression settings when writing images to disk cache
    private static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;
    private static final int DEFAULT_COMPRESS_QUALITY = 70;
    private static final int DISK_CACHE_INDEX = 0;

    //Constants to easily toggle various caches
    private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;
    private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;
    private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE = false;

    private DiskLruCache mDiskLruCache;
    private LruCache<String, byte[]> mMemoryCache;
    private ImageCacheParams mCacheParams;
    private final Object mDiskCacheLock = new Object();
    private boolean mDiskCacheStarting = true;

    /**
     * ImageCache object across configuration changes such as a change in device orientation
     */
    public static ImageCache getInstance(
            FragmentManager fragmentManager, ImageCacheParams cacheParams) {

        //Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

        //See if we already have an ImageCache stored in RetainFragment
        ImageCache imageCache = (ImageCache) mRetainFragment.getObject();

        //No existing imageCache, create one and store it in RetainFragment
        if (imageCache == null) {
            imageCache = new ImageCache(cacheParams);
            mRetainFragment.setObject(imageCache);
        } else {
            Log.i(TAG, "ImageCache exists");
        }

        return imageCache;
    }

    /**
     * Create a new ImageCache object using the specified parameters. This
     * should not be called directly by other classes.
     * @param cacheParams
     */
    private ImageCache(ImageCacheParams cacheParams) {
        Log.i(TAG, "trying to load memory cache");
        init(cacheParams);
    }

    /**
     * Initialize the cache, providing all parameters
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void init(ImageCacheParams cacheParams) {
        mCacheParams = cacheParams;

        if (mCacheParams.memoryCacheEnabled) {
            Log.i(TAG, "init memory cache");
            mMemoryCache = new LruCache<String, byte[]>(mCacheParams.memCacheSize) {

                @Override
                protected int sizeOf(String key, byte[] value) {
                    final int size = value.length / 1024;
                    return size;
                }
            };
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
            mDiskCacheStarting = false;
            mDiskCacheLock.notifyAll();
        }
    }

    /**
     * Add a bitmap to both memory and disk cache
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public void addByteToCache(String data, byte[] value) {
        if (data == null || value == null) return;

        //Add to memory cache
        if (mMemoryCache != null) {
            mMemoryCache.put(data, value);
        }

        synchronized (mDiskCacheLock) {
            //Add to disk cache
            if (mDiskLruCache != null) {
                final String key = hashKeyforDisk(data);
                OutputStream out = null;
                Deflater deflater = null;

                try {
                    DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                    if (snapshot == null) {

                        final DiskLruCache.Editor editor = mDiskLruCache.edit(key);

                        if (editor != null) {
                            out = editor.newOutputStream(DISK_CACHE_INDEX);

                            deflater = new Deflater();
                            deflater.setInput(value);
                            deflater.finish();

                            int byteRead;
                            byte[] bufferArray = new byte[value.length];

                            while (!deflater.finished()) {
                                byteRead = deflater.deflate(bufferArray);
                                out.write(bufferArray, 0, byteRead);
                            }

                            editor.commit();
                            deflater.end();
                            out.close();
                        }

                    } else {
                        snapshot.getInputStream(DISK_CACHE_INDEX).close();
                    }

                } catch (IOException e) {
                    Log.i(TAG, "addByteToCache error - " + e);
                } catch (Exception e) {
                    Log.i(TAG, "addByteToCache error - " + e);
                } finally {
                    try {

                        if (out != null) {
                            out.close();
                        }

                        if (deflater != null) {
                            deflater.end();
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
    public byte[] getByteFromMemCache(String data) {
        byte[] result = null;
        if (mMemoryCache != null) {
            result = mMemoryCache.get(data);
        }

        return result;
    }

    /**
     * Get from diskCache
     */
    public byte[] getByteFromDiskCache(String data) {
        final String key = hashKeyforDisk(data);
        byte[] result = null;


        synchronized (mDiskCacheLock) {
            while (mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait();
                } catch (InterruptedException e) {}
            }
        }

        if (mDiskCacheLock != null) {
            InputStream inputStream = null;
            Inflater inflater = null;
            ByteArrayOutputStream outputStream = null;

            try {
                final DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot != null) {
                    inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);

                    /**
                     * Error here
                     */
                    if (inputStream != null) {

                        byte[] tempBuffer = new byte[READ_SIZE];

                        int bufferLeft = tempBuffer.length;
                        int bufferOffset = 0;
                        int readResult = 0;

                        outer: do {

                            while (bufferLeft > 0) {
                                readResult = inputStream.read(tempBuffer, bufferOffset, bufferLeft);

                                if (readResult < 0)
                                    break outer;

                                bufferOffset += readResult;
                                bufferLeft -= readResult;

                            }

                            bufferLeft = READ_SIZE;
                            int newSize = tempBuffer.length + READ_SIZE;
                            byte[] expandedBuffer = new byte[newSize];
                            System.arraycopy(tempBuffer, 0, expandedBuffer, 0, tempBuffer.length);
                            tempBuffer = expandedBuffer;

                        } while (true);

                        inputStream.close();

                        inflater = new Inflater();
                        inflater.setInput(tempBuffer);
                        outputStream = new ByteArrayOutputStream(bufferOffset);

                        tempBuffer = new byte[1024];

                        while (!inflater.finished()) {
                            readResult = inflater.inflate(tempBuffer);
                            outputStream.write(tempBuffer, 0, readResult);
                        }

                        outputStream.close();
                        result = outputStream.toByteArray();
                        inflater.end();
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "getByteFromDiskCache - " + e);
            } finally {
                try {

                    if (inputStream != null)
                        inputStream.close();

                    if (outputStream != null)
                        outputStream.close();

                    if (inflater != null)
                        inflater.end();

                } catch (IOException e) {}
            }
        }
        return result;
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
     * configuration changes. It will be used to retain the ImageCache Object
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

    /**
     * A holder class that contains cache parameters
     */
    public static class ImageCacheParams {
        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;
        public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;
        public File diskCacheDir;
        public Bitmap.CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
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

}
