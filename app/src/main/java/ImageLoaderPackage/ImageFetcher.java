package ImageLoaderPackage;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by desmond on 8/5/14.
 */
public class ImageFetcher extends ImageResizer {
    private static final String TAG = "ImageFetcher";

    public ImageFetcher(Context context, int imageWidth, int imageHeight) {
        super(context, imageWidth, imageHeight);
        init(context);
    }

    public ImageFetcher(Context context, int imageSize) {
        super(context, imageSize);
        init(context);
    }

    public ImageFetcher(Context context) {
        super(context, Integer.MAX_VALUE);
        init(context);
    }

    private void init(Context context) {
        checkConnection(context);
    }

    private void checkConnection(Context context) {
        final ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            Toast.makeText(context, "No Connection Found", Toast.LENGTH_LONG).show();
            Log.e(TAG, "checkConnection - no connection found");
        }
    }

    /**
     * Main process method
     */
    private Bitmap processBitmap(String url) {
        Log.i(TAG, "Process bitmap - " + url);

        try {
            if (url == null) return null;

            byte[] bitmapBytes = getUrlBytes(url);

            return decodeSampledBitmapFromByte(mContext, bitmapBytes, getImageCache());
        } catch (IOException e) {
            Log.e(TAG, "Error downloading photo - " + e);
        }
        return null;
    }

    @Override
    protected Bitmap processBitmap(Object data) {
        Log.i(TAG, "Downloading bitmap");
        return processBitmap(String.valueOf(data));
    }

    /**
     * Download image byte from url
     */
    private byte[] getUrlBytes(String urlSpec) throws IOException {
        final URL url = new URL(urlSpec);
        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                Log.e(TAG, "Connection Error");
                return null;
            }

            int byteRead;
            byte[] buffer = new byte[1024];
            while ((byteRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, byteRead);
            }
            out.close();
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
