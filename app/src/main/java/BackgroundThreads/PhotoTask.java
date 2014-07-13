package BackgroundThreads;

import android.graphics.Bitmap;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * Created by desmond on 20/6/14.
 */
public class PhotoTask implements PhotoDecodeRunnable.TaskRunnableDecodeMethods, PhotoDownloadRunnable.TaskRunnableDownloadMethods {

    private static final String TAG = "PhotoTask";
    private String mImageUrl;
    private WeakReference<ImageView> mImageWeakRef;
    private int mTargetHeight;
    private int mTargetWidth;
    private boolean mCacheEnabled;

    private byte[] mImageBuffer;
    private Bitmap mDecodedImage;

    //The Thread on which this task is running
    private Thread mCurrentThread;

    //Thread that this task is running on
    Thread mThreadThis;

    private Runnable mDownloadRunnable;
    private Runnable mDecodeRunnable;

    private static PhotoManager sPhotoManager;

    PhotoTask() {
        mDecodeRunnable = new PhotoDecodeRunnable(this);
        mDownloadRunnable = new PhotoDownloadRunnable(this);
    }

    void initializeDownloaderTask(PhotoManager photoManager, ImageView photoView,
                                  boolean cacherFlag, String url) {

        sPhotoManager = photoManager;
        mImageUrl = url;
        mImageWeakRef = new WeakReference<ImageView>(photoView);
        mCacheEnabled = cacherFlag;
        mTargetHeight = photoView.getHeight();
        mTargetWidth = photoView.getWidth();
    }

    //Implements PhotoDownloaderRunnable.getByteBuffer
    @Override
    public byte[] getByteBuffer() {
        return mImageBuffer;
    }

    void recycle() {
        if (mImageWeakRef != null) {
            mImageWeakRef.clear();
            mImageWeakRef = null;
        }

        mImageBuffer = null;
        mDecodedImage = null;
    }

    //Implements PhotoDecodeRunnable.getTargetHeight
    @Override
    public int getTargetHeight() {
        return mTargetHeight;
    }

    //Implements PhotoDecodeRunnable.getTargetWidth
    @Override
    public int getTargetWidth() {
        return mTargetWidth;
    }

    //Detects the state of caching
    boolean isCacheEnable() {
        return mCacheEnabled;
    }

    //Implements PhotoDownloadRunnable.getImageURL
    @Override
    public String getImageURL() {
        return mImageUrl;
    }

    //Implements PhotoDownloadRunnable.setByteBuffer.
    @Override
    public void setByteBuffer(byte[] buffer) {
        mImageBuffer = buffer;
    }

    void handleState(int state) {
        sPhotoManager.handleState(this, state);
    }

    Bitmap getImage() {
        return mDecodedImage;
    }

    //Returns the instance that downloaded the image
    Runnable getPhotoDownloadRunnable() {
        return mDownloadRunnable;
    }

    //Returns the instance that decode the image
    Runnable getPhotoDecodeRunnable() {
        return mDecodeRunnable;
    }

    //Returns the ImageView that's being constructed
    public ImageView getPhotoView() {
        if (mImageWeakRef != null) {
            return mImageWeakRef.get();
        }
        return null;
    }

    //Implements PhotoDecodeRunnable.setImage(). Sets the Bitmap for the current image
    @Override
    public void setImage(Bitmap decodedImage) {
        mDecodedImage = decodedImage;
    }

    /**
     * Returns the Thread that this Task is running. The method must first get a lock on a
     * static field, in this case the ThreadPool singleton. The lock is needed because the
     * Thread object reference is stored in the Thread object itself, and that object can be
     * changed by processes outside of this app
     */
    public Thread getCurrentThread() {
        synchronized (sPhotoManager) {
            return mCurrentThread;
        }
    }

    /**
     * Sets the identifier for the current Thread this Task is running. This must be a
     * synchronized operation; see the notes for getCurrentThread()
     */
    public void setCurrentThread(Thread thread) {
        synchronized (sPhotoManager) {
            mCurrentThread = thread;
        }
    }

    //Implements PhotoDownloadRunnable.setDownloadThread(). Calls setCurrentThread()
    @Override
    public void setDownloadThread(Thread currentThread) {
        setCurrentThread(currentThread);
    }

    //Implements PhotoDecodeRunnable.setImageDecodeThread()
    @Override
    public void setImageDecodeThread(Thread currentThread) {
        setCurrentThread(currentThread);
    }

    /**
     * Implements PhotoDownloadRunnable.handleDownloadState(). Passes the download
     * state to the ThreadPool object
     */
    @Override
    public void handleDownloadState(int state) {
        int outState;

        //Converts the download state to the overall state
        switch (state) {
            case PhotoDownloadRunnable.HTTP_STATE_COMPLETED:
                outState = PhotoManager.DOWNLOAD_COMPLETE;
                break;
            case PhotoDownloadRunnable.HTTP_STATE_FAILED:
                outState = PhotoManager.DOWNLOAD_FAILED;
                break;
            default:
                outState = PhotoManager.DOWNLOAD_STARTED;
                break;
        }

        //Passes the state to the ThreadPool object
        handleState(outState);
    }

    /**
     * Implements PhotoDecodeRunnable.handleDecodeState(). Passes the decoding state to the
     * ThreadPool object.
     */
    @Override
    public void handleDecodeState(int state) {
        int outState;

        // Converts the decode state to the overall state.
        switch(state) {
            case PhotoDecodeRunnable.DECODE_STATE_COMPLETED:
                outState = PhotoManager.TASK_COMPLETE;
                break;
            case PhotoDecodeRunnable.DECODE_STATE_FAILED:
                outState = PhotoManager.DOWNLOAD_FAILED;
                break;
            default:
                outState = PhotoManager.DECODE_STARTED;
                break;
        }

        // Passes the state to the ThreadPool object.
        handleState(outState);
    }

    @Override
    public ImageCache getImageCache() {
        return sPhotoManager.getImageCache();
    }
}
