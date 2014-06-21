package BackgroundThreads;

import android.os.Process;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by desmond on 15/6/14.
 */
public class PhotoDownloadRunnable implements Runnable {
    private static final String TAG = "PhotoDownloadRunnable";
    private static final int READ_SIZE = 1024 * 2;

    public static final int HTTP_STATE_FAILED = -1;
    public static final int HTTP_STATE_STARTED = 0;
    public static final int HTTP_STATE_COMPLETED = 1;

    private final TaskRunnableDownloadMethods mPhotoTask;

    interface TaskRunnableDownloadMethods {

        void setDownloadThread(Thread currentThread);

        byte[] getByteBuffer();

        void setByteBuffer(byte[] buffer);

        void handleDownloadState(int state);

        ImageCache getImageCache();

        String getImageURL();
    }

    PhotoDownloadRunnable(TaskRunnableDownloadMethods photoTask) {
        mPhotoTask = photoTask;
    }

    @Override
    public void run() {

        mPhotoTask.setDownloadThread(Thread.currentThread());

        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        byte[] byteBuffer = mPhotoTask.getByteBuffer();

        try {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            ImageCache imageCache = null;
            //Search from Disk cache
            if (byteBuffer == null) {
                imageCache = mPhotoTask.getImageCache();

                if (imageCache != null) {
                    byteBuffer = imageCache.getByteFromDiskCache(mPhotoTask.getImageURL());
                }
            }

            //Download
            if (byteBuffer == null) {

                mPhotoTask.handleDownloadState(HTTP_STATE_STARTED);

                InputStream bytesStream = null;

                try {
                    URL url = new URL(mPhotoTask.getImageURL());
                    HttpsURLConnection httpConn =
                            (HttpsURLConnection) url.openConnection();

                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }

                    //Gets the input stream containing the image
                    bytesStream = httpConn.getInputStream();

                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }

                    int contentSize = httpConn.getContentLength();

                    //Size isn't available
                    if (contentSize == -1) {

                        byte[] tempBuffer = new byte[READ_SIZE];

                        int bufferLeft = tempBuffer.length;
                        int bufferOffset = 0;
                        int readResult = 0;

                        //The outer loop will continue until all the bytes are downloaded
                        outer: do {

                            while (bufferLeft > 0) {
                                readResult = bytesStream.read(tempBuffer, bufferOffset, bufferLeft);

                                //InputStream.read() returns zero when the file is completely read
                                if (readResult < 0) {
                                    break outer;
                                }

                                bufferOffset += readResult;
                                bufferLeft -= readResult;

                                if (Thread.interrupted()) {
                                    throw new InterruptedException();
                                }
                            }

                            //Temporary buffer is full
                            bufferLeft = READ_SIZE;
                            int newSize = tempBuffer.length + READ_SIZE;
                            byte[] expandedBuffer = new byte[newSize];
                            System.arraycopy(tempBuffer, 0, expandedBuffer, 0, tempBuffer.length);
                            tempBuffer = expandedBuffer;

                        } while (true);

                        //Entire image has been read
                        byteBuffer = new byte[bufferOffset];
                        System.arraycopy(tempBuffer, 0, byteBuffer, 0, bufferOffset);


                        //Download size is available
                    } else {

                        byteBuffer = new byte[contentSize];
                        int remainingLength = contentSize;
                        int bufferOffset = 0;
                        int readResult = 0;
                        while (remainingLength > 0) {

                            readResult = bytesStream.read(byteBuffer, bufferOffset, remainingLength);

                            //EOF should occur because the loop should read the exact # of bytes
                            if (readResult < 0) {
                                throw new EOFException();
                            }

                            bufferOffset += readResult;
                            remainingLength -= readResult;

                            if (Thread.interrupted()) {
                                throw new InterruptedException();
                            }

                        }

                    }

                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }

                    //Save to cache
                    if (imageCache != null) {
                        imageCache.addByteToCache(mPhotoTask.getImageURL(), byteBuffer);
                    }

                } catch (MalformedURLException e) {

                } catch (IOException ioException) {
                    ioException.printStackTrace();
                    return;

                } finally {
                    if (bytesStream != null) {
                        try {
                            bytesStream.close();
                        } catch (Exception e) {

                        }
                    }
                }
            }

            mPhotoTask.setByteBuffer(byteBuffer);

            mPhotoTask.handleDownloadState(HTTP_STATE_COMPLETED);

        } catch (InterruptedException e) {

        } finally {

            if (byteBuffer == null) {
                mPhotoTask.handleDownloadState(HTTP_STATE_FAILED);
            }

            mPhotoTask.setDownloadThread(null);
            Thread.interrupted();
        }
    }
}
