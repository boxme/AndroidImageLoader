## **AndroidImageLoader**

### Description:
Helps to download an image from its URL and load onto the ImageView of your choice.
Majority of the code is from the google sample code: http://developer.android.com/training/displaying-bitmaps/index.html
Modifications have been made to remove pre-existing bugs within the original code, and to also include the ability to create circular images with the choice of a border around it.

### Modifications:
- Removed http caching. 
- Included circular image. (as seen in google+ profile picture)
- Changed to byte stream with the download as it gives a better performance.
- Forced the customised RecyclingBitmapDrawable to be the default in use.
- Binded placeholder loading image to each task so that the right loading image will be used.

### Usage Sample Code:
Instantiation:
	
    ImageFetcher imageLoader = new ImageFetcher(context);
	ImageCacheParams cacheParams = new ImageCacheParams(context, <name of cache file>);
	imageLoader.addImageCache(FragmentManager, cacheParams);
    
Loading Normal Image:
	
    imageLoader.loadImage(url, imageView, placeholderDrawable);
    
Loading Circular Image:
	
    imageLoader.loadCircularImage(url, imageview, size, borderSize, placeholderDrawable);

Bookkeeping:

	@Override
    public void onResume() {
        super.onResume();
        mImageLoader.setExitTasksEarly(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageLoader.setPauseWork(false);
        mImageLoader.setExitTasksEarly(true);
        mImageLoader.flushCache();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mImageLoader.closeCache();
    }




