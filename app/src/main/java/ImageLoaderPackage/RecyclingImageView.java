package ImageLoaderPackage;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by desmond on 7/5/14.
 */
public class RecyclingImageView extends ImageView {

    public RecyclingImageView(Context context) {
        super(context);
    }

    public RecyclingImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDetachedFromWindow() {
        setImageDrawable(null);
        super.onDetachedFromWindow();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        //Keep hold of previous Drawable
        final Drawable previousDrawable = getDrawable();

        //Call super to set new drawable
        super.setImageDrawable(drawable);

        //Notify new Drawable that it is being displayed
        notifyDrawable(drawable, true);

        //Notify old Drawable so it is no longer being displayed
        notifyDrawable(previousDrawable, false);
    }

    private void notifyDrawable(Drawable drawable, final boolean isDisplayed) {
        if (drawable instanceof RecyclingBitmapDrawable) {
            ((RecyclingBitmapDrawable) drawable).setIsDisplayed(isDisplayed);
        } else if (drawable instanceof LayerDrawable) {
            LayerDrawable layerDrawable = (LayerDrawable) drawable;
            for (int i = 0, z = layerDrawable.getNumberOfLayers(); i < z; ++i) {
                notifyDrawable(layerDrawable.getDrawable(i), isDisplayed);
            }
        }
    }
}
