package BackgroundThreads;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.ImageView;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by desmond on 20/6/14.
 */
public class PhotoManager {
    private static final String TAG = "PhotoManager";
    /**
     * Status indicator
     */
    static final int DOWNLOAD_FAILED = -1;
    static final int DOWNLOAD_STARTED = 1;
    static final int DOWNLOAD_COMPLETE = 2;
    static final int DECODE_STARTED = 3;
    static final int TASK_COMPLETE = 4;

    private static final int MESSAGE_CLEAR = 0;
    private static final int MESSAGE_INIT_DISK_CACHE = 1;
    private static final int MESSAGE_FLUSH = 2;
    private static final int MESSAGE_CLOSE = 3;

    //Sets the amount of time an idle thread will wait for a task before terminating
    private static final int KEEP_ALIVE_TIME = 1;

    //Sets the Time Unit to Seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT;

    //Sets the initial ThreadPool size to 8
    private static final int CORE_POOL_SIZE = 8;

    //Sets the maximum ThreadPool size to 8
    private static final int MAXIMUM_POOL_SIZE = 8;

    private static final int FADE_IN_TIME = 400;

    /**
     * NOTE: This is the number of total available cores. On current versions of
     * Android, with devices that use plug-and-play cores, this will return less
     * than the total number of cores. The total number of cores is not available
     * in current Android implementations.
     */
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    //A queue of Runnables for the image download pool
    private final BlockingQueue<Runnable> mDownloadWorkQueue;

    //A queue of Runnables for the image decoding pool
    private final BlockingQueue<Runnable> mDecodeWorkQueue;

    //A queue of PhotoManager task. Tasks are handed to a ThreadPool
    private final Queue<PhotoTask> mPhotoTaskWorkQueue;

    //A managed pool of background download threads
    private final ThreadPoolExecutor mDownloadThreadPool;

    //A managed pool of background decoder threads
    private final ThreadPoolExecutor mDecodeThreadPool;

    //An object that manages Messages in a Thread
    private Handler mHandler;

    //A single instance of PhotoManager, used to implement the singleton pattern
    private static PhotoManager sInstance = null;

    //A resource
    private static Resources mResources = null;

    //A static block that sets class fields
    static  {
        // The time unit for "keep alive" is in seconds
        KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    }

    private ImageCache mImageCache;
    private ImageCache.ImageCacheParams mImageCacheParams;

    /**
     * Returns the PhotoManager object
     */
    public static PhotoManager getInstance() {
        return sInstance;
    }

    public static void init(FragmentActivity activity) {
        if (sInstance == null) {
            sInstance = new PhotoManager(activity);
            mResources = activity.getResources();
        }
    }


    private PhotoManager(FragmentActivity activity) {
        mDownloadWorkQueue = new LinkedBlockingQueue<Runnable>();
        mDecodeWorkQueue = new LinkedBlockingQueue<Runnable>();
        mPhotoTaskWorkQueue = new LinkedBlockingQueue<PhotoTask>();

        mDownloadThreadPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME,
                KEEP_ALIVE_TIME_UNIT,
                mDownloadWorkQueue
        );

        mDecodeThreadPool = new ThreadPoolExecutor(
                NUMBER_OF_CORES,
                NUMBER_OF_CORES,
                KEEP_ALIVE_TIME,
                KEEP_ALIVE_TIME_UNIT,
                mDecodeWorkQueue
        );

        //Initialize Cache
        mImageCacheParams = new ImageCache.ImageCacheParams(activity, "cache");
        mImageCache = ImageCache.getInstance(activity.getSupportFragmentManager(), mImageCacheParams);
        new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);

        mHandler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message inputMsg) {
                PhotoTask photoTask = (PhotoTask) inputMsg.obj;

                ImageView imageView = photoTask.getPhotoView();

                if (imageView != null) {

                    switch (inputMsg.what) {
                        //If the download has started, sets background color to dark green
                        case DOWNLOAD_STARTED:
//                            imageView.setStatusResource(R.drawable.imagedownloading);
                            break;

                        /**
                         * If download is completed, but the decode is waiting, sets the
                         * background color to golden yellow
                         */
                        case DOWNLOAD_COMPLETE:
//                            imageView.setStatusResource(R.drawable.decodequeued);
                            break;

                        //If the decode has started, sets background color to orange
                        case DECODE_STARTED:
//                            imageView.setStatusResource(R.drawable.decodedecoding);
                            break;

                        /**
                         * The decoding is done, so this sets the ImageView's bitmap
                         * to the bitmap in the incoming message
                         */
                        case TASK_COMPLETE:

                            imageView.setImageBitmap(photoTask.getImage());
                            setImageDrawable(imageView, photoTask.getImage());
                            recycleTask(photoTask);
                            break;

                        //Download failed, sets the background color to dark red
                        case DOWNLOAD_FAILED:
//                            localView.setStatusResource(R.drawable.imagedownloadfailed);

                            //Attempts to re-use the Task object
                            recycleTask(photoTask);
                            break;

                        default:
                            //Otherwise, calls the super method
                            super.handleMessage(inputMsg);
                    }
                }
            }
        };
    }

    /**
     * Handles state messages for a particular task object
     */
    public void handleState(PhotoTask photoTask, int state) {
        switch(state) {

            //Task finished downloading and decoding the image
            case TASK_COMPLETE:

                //Gets a Message object, stores the state in it, and sends it to the Handler
                //Handler.obtainMessage() will set the message to be sent to this Handler

                Message completeMessage = mHandler.obtainMessage(state, photoTask);
                completeMessage.sendToTarget();
                break;

            //The task finished downloading the image
            case DOWNLOAD_COMPLETE:
                /**
                 * Decodes the image, by queuing the decoder object to run in the decoder
                 * thread pool
                 */
                mDecodeThreadPool.execute(photoTask.getPhotoDecodeRunnable());

                //In all other cases, pass along the message without any other action
            default:
                mHandler.obtainMessage(state, photoTask).sendToTarget();
                break;
        }
    }

    static public PhotoTask startDownload(ImageView imageView, String url,
                                          boolean cacherFlag) {

        PhotoTask downloadTask = sInstance.mPhotoTaskWorkQueue.poll();

        if (downloadTask == null) {
            downloadTask = new PhotoTask();
        }

        downloadTask.initializeDownloaderTask(PhotoManager.sInstance, imageView,
                                                cacherFlag, url);

        downloadTask.setByteBuffer(sInstance.mImageCache.getByteFromMemCache(url));

        //Not found in the memory cache
        if (downloadTask.getByteBuffer() == null) {

            sInstance.mDownloadThreadPool.execute(downloadTask.getPhotoDownloadRunnable());

        } else {
            Log.i(TAG, "found in memory cache");
            sInstance.handleState(downloadTask, DOWNLOAD_COMPLETE);

        }

        return downloadTask;
    }

    /**
     * Stops a downloadThread and removes it from the ThreadPool
     */
    static public void removeDownload(PhotoTask downloaderTask, String pictureURL) {
        //If the Thread object still exists and the download matches the specified URL
        if (downloaderTask != null && downloaderTask.getImageURL().equals(pictureURL)) {

            /**
             * Locks on this class to ensure that other processes aren't mutating Threads
             */
            synchronized (sInstance) {
                //Gets the Thread that the downloader task is running on
                Thread thread = downloaderTask.getCurrentThread();

                //If the Thread exists, posts in interrupt to it
                if (thread != null)
                    thread.interrupt();
            }

            /**
             * Removes the download Runnable from the ThreadPool. This opens a Thread
             * in the ThreadPool's work queue, allowing a task in the queue to start
             */
            sInstance.mDownloadThreadPool.remove(downloaderTask.getPhotoDownloadRunnable());
        }
    }

    /**
     * Cancels all Threads in the ThreadPool
     */
    public static void cancelAll() {
        /**
         * Creates an array of tasks that's the same size as the task work queue
         */
        PhotoTask[] taskArray = new PhotoTask[sInstance.mDownloadWorkQueue.size()];

        //Populates the array with the task objects in the queue
        sInstance.mDownloadWorkQueue.toArray(taskArray);

        //Stores the array length in order to iterate the array
        int taskArraylen = taskArray.length;

        /**
         * Locks on the singleton to ensure taht other processes aren't mutating Threads, then
         * iterates over the array of tasks and interrupt the task's current Thread
         */
        synchronized (sInstance) {
            //Iterates over the array of tasks
            for (int taskArrayIndex = 0; taskArrayIndex < taskArraylen; taskArrayIndex++) {

                //Gets the task's current thread
                Thread thread = taskArray[taskArrayIndex].mThreadThis;

                //If the Thread exists, post an interrupt to it
                if (thread != null) {
                    thread.interrupt();
                }
            }
        }
    }

    private void setImageDrawable(ImageView imageView, Bitmap decodeBitmap) {
        BitmapDrawable drawable = new BitmapDrawable(mResources, decodeBitmap);

        final TransitionDrawable td =
                new TransitionDrawable(new Drawable[] {
                     new ColorDrawable(android.R.color.transparent), drawable
                });

        imageView.setImageDrawable(td);
        td.startTransition(FADE_IN_TIME);
    }

    /**
     * Recycles tasks by calling their internal recycle() method and then putting them back into
     * the task queue
     * @param downloadTask The task to recycle
     */
    void recycleTask(PhotoTask downloadTask) {
        //Frees up memory in the task
        downloadTask.recycle();

        //Puts the task object back into the queue for re-use
        mPhotoTaskWorkQueue.offer(downloadTask);
    }

    ImageCache getImageCache() {
        return mImageCache;
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
