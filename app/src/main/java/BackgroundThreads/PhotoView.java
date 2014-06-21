package BackgroundThreads;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.replaid.efficientbitmap.app.R;

import java.lang.ref.WeakReference;

/**
 * This class extends the standard Android ImageView View class with some features
 * that are useful for downloading, decoding, and displaying Picasa images.
 */
public class PhotoView extends ImageView {
    //Indicates if caching should be used
    private boolean mCacheFlag;

    //Status flag that indicates if onDraw has completed
    private boolean mIsDrawn;

    /**
     * Creates a weak reference to the ImageView in this object. The weak
     * reference prevents memory leaks and crashes, because it automatically tracks the "state" of
     * the variable it backs. If the reference becomes invalid, the weak reference is garbage-
     * collected.
     * This technique is important for referring to objects that are part of a component lifecycle.
     * Using a hard reference may cause memory leaks as the value continues to change; even worse,
     * it can cause crashes if the underlying component is destroyed. Using a weak reference to
     * a View ensures that the reference is more transitory in nature.
     */
    private WeakReference<View> mThisView;

    //Contains the ID of the internal View
    private int mHideShowResId = -1;

    //The URL that points to the source of the image for this ImageView
    private String mImageURL;

    //The Thread that will be used to download the image for this imageView
    private PhotoTask mDownloadThread;

    /**
     * Creates an ImageDownloadView with no settings
     */
    public PhotoView(Context context) {
        super(context);
    }

    /**
     * Creates an ImageDownloadView and gets attribute values
     */
    public PhotoView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        //Gets attributes associated with the attribute set
        getAttributes(attributeSet);
    }

    /**
     * Creates an ImageDownloadView, gets attribute values, and applies a default style
     * @param context
     * @param attributeSet
     * @param defaultStyle The default style to use with the View
     */
    public PhotoView(Context context, AttributeSet attributeSet, int defaultStyle) {
        super(context, attributeSet, defaultStyle);

        //Gets attribute associated with the attribute set
        getAttributes(attributeSet);
    }

    /**
     * Gets the resource ID for the hideShowSibling resource
     * @param attributeSet
     */
    private void getAttributes(AttributeSet attributeSet) {

        //Gets an array of attributes for the View
        TypedArray attributes =
                getContext().obtainStyledAttributes(attributeSet, R.styleable.ImageDownloaderView);

        //Gets the resource Id of the View to hide or show
//        mHideShowResId =
//                attributes.getResourceId(R.styleable.ImageDownloaderView_hideShowSibling, -1);

        //Returns the array for re-use
        attributes.recycle();
    }

    /**
     * Sets the visiblity of the PhotoView
     * @param visState The visibility state (see View.setVisibility)
     */
    private void showView(int visState) {
        //If the View contains something
        if (mThisView != null) {
            //Gets a local hard reference to the View
            View localView = mThisView.get();

            //If the weak reference actually contains something, set the visibility
            if (localView != null)
                localView.setVisibility(visState);
        }
    }

    /**
     * Sets the image in this ImageView to null, and makes the View visible
     */
    public void clearImage() {
        setImageDrawable(null);
        showView(View.VISIBLE);
    }

    /**
     * Returns the URL of the picture associated with this ImageView
     * @return a URL
     */
    final String getLocation() {
        return mImageURL;
    }

    /**
     * This call back is invoked when the system attaches the ImageView to a Window. The callback
     * is invoked before onDraw(), but may be invoked after onMeasure()
     */
    @Override
    protected void onAttachedToWindow() {
        //Always call the super method first
        super.onAttachedToWindow();

        //If the sibling view is set and the parent of the ImageView is itself a View
        if ((this.mHideShowResId != -1) && (getParent() instanceof View)) {

            //Gets a handle to the sibling view
            View localView = ((View) getParent()).findViewById(this.mHideShowResId);

            //If the sibling View contains something, make it the weak reference for this View
            if (localView != null) {
                this.mThisView = new WeakReference<View>(localView);
            }
        }
    }

    /**
     * This callback is invoked when the ImageView is removed from a Window. It "unsets" variables
     * to prevent memory leaks
     */
    @Override
    protected void onDetachedFromWindow() {

        //Clears out the image drawable, turns off the cache, disconnects the view from a URL
        setImageURL(null, false, null);

        //Gets the current Drawable, or null if no Drawable is attached
        Drawable localDrawable = getDrawable();

        //If the drawable is null, unbind it from this View
        if (localDrawable != null) {
            localDrawable.setCallback(null);
        }

        //If this View still exists, clears the weak reference, then sets the reference to null
        if (mThisView != null) {
            mThisView.clear();
            mThisView = null;
        }

        //Sets the downloader thread to null
        this.mDownloadThread = null;

        super.onDetachedFromWindow();
    }

    /**
     * This callback is invoked when the system tells the view to draw itself. If the View isn't
     * already drawn, and its URL isn't null, it invokes a Thread to download the image. Otherwise,
     * it simply passes the existing Canvas to the super method
     */
    @Override
    protected void onDraw(Canvas canvas) {
        //If the image isn't already drawn, and the URL is set
        if ((!mIsDrawn && (mImageURL != null))) {

            //Starts downloading this View, using the current cache setting
            mDownloadThread = PhotoManager.startDownload(this, mImageURL, mCacheFlag);

            //After successfully downloading the image, this marks that it's available
            mIsDrawn = true;

        }

        super.onDraw(canvas);
    }

    /**
     * Sets the current View weak reference to be the incoming View. See the definition
     * of mThisView
     * @param view the Vieew to use as the new WeakReference
     */
    public void setHideView(View view) {
        this.mThisView = new WeakReference<View>(view);
    }

    @Override
    public void setImageBitmap(Bitmap paramBitmap) {
        super.setImageBitmap(paramBitmap);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        //The visibility of the View
        int viewState;

        /**
         * Sets the View state to visible if the method is called with a null argument (the
         * image is being cleared). Otherwise, sets the View state to invisible before refreshing it.
         */
        if (drawable == null) {
            viewState = View.VISIBLE;
        } else {
            viewState = View.INVISIBLE;
        }

        //Either hides or shows the view, depending on the view state
        showView(viewState);

        //Invokes the super method with the provided drawable
        super.setImageDrawable(drawable);
    }

    /**
     * Displays a drawable in the View
     * @param resId
     */
    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
    }

    /**
     * Sets the URI for the Image
     */
    @Override
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
    }

    /**
     * Attempts to set the picture URL for this ImageView and then download the picture
     *
     * If the picture URL for this view is already set, and the input URL is not the same as
     * the store URL, hen the picture has moved and any existing downloads are stopped
     *
     * If the input URL is the same as the stored URL, then nothing need to be done
     *
     * If the stored URL is null, then this method starts a download and decode of the picture
     * @param pictureUrl An incoming URL for a Picasa photo
     * @param cacheFlag Whether to use caching when doing downloading and decoding
     * @param imageDrawable The Drawable to use for this ImageView
     */
    public void setImageURL(String pictureUrl, boolean cacheFlag, Drawable imageDrawable) {

        //If the picture URL for this ImageView is already set
        if (mImageURL != null) {

            //If the stored URL doesn't match the incoming URL, then the picture has changed.
            if (!mImageURL.equals(pictureUrl)) {

                //Stops any ongoing downloads for this ImageView
                PhotoManager.removeDownload(mDownloadThread, mImageURL);
            } else {

                //The stored URL matches the incoming URL. Returns without doing any work
                return;
            }
        }

        //Sets the Drawable fo this ImageView
        if (imageDrawable != null) {
            setImageDrawable(imageDrawable);
        }

        //Stores the picture URL for this ImageView
        mImageURL = pictureUrl;

        //If the draw operation for this ImageView has completed, and the picture URL isn't empty
        if (mIsDrawn && (pictureUrl != null)) {

            //Sets the cache flag
            mCacheFlag = cacheFlag;

            /**
             * Starts a download of the picture file. Notice that if caching is on, the picture
             * file's content may be taken from the cache
             */
            mDownloadThread = PhotoManager.startDownload(this, mImageURL, cacheFlag);
        }
    }

    /**
     * Sets the Drawable for this ImageView
     */
    public void setStatusDrawable(Drawable drawable) {

        //If the View is empty, sets a Drawable as its content
        if (mThisView == null) {
            setImageDrawable(drawable);
        }
    }

    /**
     * Sets the content of this ImageView to be a Drawable resource
     */
    public void setStatusResource(int resId) {

        //If the View is empty, provides it with a Drawable resource as its content
        if (mThisView == null) {
            setImageResource(resId);
        }
    }
}
