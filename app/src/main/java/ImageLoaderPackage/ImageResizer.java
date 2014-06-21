package ImageLoaderPackage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.FileDescriptor;

/**
 * Created by desmond on 8/5/14.
 */
public class ImageResizer extends ImageWorker {
    private static final String TAG = "ImageResizer";
    protected int mImageWidth;
    protected int mImageHeight;

    /**
     * Initialize provide a single target image size
     */
    public ImageResizer(Context context, int imageWidth, int imageHeight) {
        super(context);
        setImageSize(imageWidth, imageHeight);
    }

    public ImageResizer(Context context, int imageSize) {
        super(context);
        setImageSize(imageSize);
    }

    /**
     * Set the target image width and height
     */
    public void setImageSize(int width, int height) {
        mImageWidth = width;
        mImageHeight = height;
    }

    public void setImageSize(int size) {
        setImageSize(size, size);
    }

    private Bitmap processBitmap(int resID) {
        Log.i(TAG, "ImageResizer ProcessBitmap - " + resID);
        return decodeSampledBitmapFromResource(mResources, resID, mImageWidth, mImageHeight, getImageCache());
    }

    @Override
    protected Bitmap processBitmap(Object data) {
        return processBitmap(Integer.parseInt(String.valueOf(data)));
    }

    /**
     * Decode and sample down a bitmap from a byte stream
     */
    @SuppressLint("NewApi")
    public static Bitmap decodeSampledBitmapFromByte(Context context, byte[] bitmapBytes, ImageCache cache) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int reqWidth;
        int reqHeight;

        if (BackgroundUtils.hasHoneycombMR2()) {
            Point outSize = new Point();
            display.getSize(outSize);
            reqWidth = outSize.x;
            reqHeight = outSize.y;
        } else {
            reqWidth = display.getWidth();
            reqHeight = display.getHeight();
        }


        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;          //Query bitmap without allocating memory for its pixel
        BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length, options);

        //Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        //Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        if (BackgroundUtils.hasHoneycomb()) {
            addInBitmapOptions(options, cache);
        }

        return BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length, options);
    }

    /**
     * Decode and sample down a bitmap from a file input stream to the requested width and height.
     */
    public static Bitmap decodeSampledBitmapFromDescriptor(FileDescriptor fileDescriptor, int reqWidth,
                                                           int reqHeight, ImageCache cache) {

        //First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

        //Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        //Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        if (BackgroundUtils.hasHoneycomb()) {
            addInBitmapOptions(options, cache);
        }

        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
    }

    /**
     * Decode and sample down a bitmap from resources to the requested width and height.
     */
    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                         int reqWidth, int reqHeight, ImageCache cache) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);


        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        // If we're running on Honeycomb or newer, try to use inBitmap
        if (BackgroundUtils.hasHoneycomb()) {
            addInBitmapOptions(options, cache);
        }

        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * Decode and sample down a bitmap from a Uri path
     */
    public static Bitmap decodeSampledBitmapFromPath(String path, int reqWidth, int reqHeight,
                                                     ImageCache cache) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;

        // If we're running on Honeycomb or newer, try to use inBitmap
        if (BackgroundUtils.hasHoneycomb()) {
            addInBitmapOptions(options, cache);
        }

        return BitmapFactory.decodeFile(path, options);
    }

    private static void addInBitmapOptions(BitmapFactory.Options options, ImageCache cache) {
        //inBitmap only works with mutable bitmap so force decoder to return
        //mutable bitmaps
        options.inMutable = true;

        if (cache != null) {
            //Try and find a bitmap to use for inBitmap
            Bitmap inBitmap = cache.getBitmapFromReusableSet(options);

            if (inBitmap != null) {
                Log.i(TAG, "Use inBitmap");
                options.inBitmap = inBitmap;
            }
        }
    }

    /**
     * Calculate an inSampleSize for use in a {@link android.graphics.BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link android.graphics.BitmapFactory}. This implementation calculates
     * the closest inSampleSize that is a power of 2 and will result in the final decoded bitmap
     * having a width and height equal to or larger than the requested width and height
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            //Calculate the largest inSampleSize value that is a power of 2 and keeps both
            //height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).
            long totalPixels = width * height / inSampleSize;

            //Anything more than 2x the requested pixels we'll sample down further
            final long totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels > totalReqPixelsCap) {
                inSampleSize *= 2;
                totalPixels /= 2;
            }

        }
        return inSampleSize;
    }
}
