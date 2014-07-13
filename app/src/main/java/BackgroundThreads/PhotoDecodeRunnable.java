package BackgroundThreads;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import ImageLoaderPackage.ImageResizer;

/**
 * Created by desmond on 15/6/14.
 */
public class PhotoDecodeRunnable implements Runnable {
    private static final String TAG = "PhotoDecodeRunnable";
    private static final long SLEEP_TIME_MILLISECONDS = 250;
    private static final int NUMBER_OF_DECODE_TRIES = 2;

    public static final int DECODE_STATE_FAILED = -1;
    public static final int DECODE_STATE_STARTED = 0;
    public static final int DECODE_STATE_COMPLETED = 1;

    private final TaskRunnableDecodeMethods mPhotoTask;

    interface TaskRunnableDecodeMethods {

        void setImageDecodeThread(Thread currentThread);

        byte[] getByteBuffer();

        void handleDecodeState(int state);

        int getTargetWidth();

        int getTargetHeight();

        void setImage(Bitmap image);
    }

    PhotoDecodeRunnable(TaskRunnableDecodeMethods downloadTask) {
        mPhotoTask = downloadTask;
    }

    @Override
    public void run() {
        mPhotoTask.setImageDecodeThread(Thread.currentThread());

        byte[] imageBuffer = mPhotoTask.getByteBuffer();

        Bitmap returnBitmap = null;

        try {

            mPhotoTask.handleDecodeState(DECODE_STATE_STARTED);

            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();

            int targetWidth = mPhotoTask.getTargetWidth();
            int targetHeight = mPhotoTask.getTargetHeight();

            if (Thread.interrupted()) {
                return;
            }

            bitmapOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.length, bitmapOptions);

            bitmapOptions.inSampleSize = ImageResizer.calculateInSampleSize(bitmapOptions, targetWidth, targetHeight);

            if (Thread.interrupted()) {
                return;
            }

            bitmapOptions.inJustDecodeBounds = false;

            for (int i = 0; i < NUMBER_OF_DECODE_TRIES; ++i) {

                try {

                    returnBitmap = BitmapFactory.decodeByteArray(imageBuffer,
                            0, imageBuffer.length, bitmapOptions);

                } catch (Throwable e) {
                    e.printStackTrace();

                    java.lang.System.gc();

                    if (Thread.interrupted()) return;

                    try {
                      Thread.sleep(SLEEP_TIME_MILLISECONDS);
                    }  catch (InterruptedException interruptedException) {
                        return;
                    }
                }
            }
        } finally {

            if (returnBitmap == null) {

                mPhotoTask.handleDecodeState(DECODE_STATE_FAILED);

            } else {

                mPhotoTask.setImage(returnBitmap);

                mPhotoTask.handleDecodeState(DECODE_STATE_COMPLETED);
            }

            mPhotoTask.setImageDecodeThread(null);
            Thread.interrupted();
            
        }

    }
}
