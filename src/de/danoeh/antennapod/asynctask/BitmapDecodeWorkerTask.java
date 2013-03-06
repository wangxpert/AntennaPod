package de.danoeh.antennapod.asynctask;

import java.io.File;

import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import de.danoeh.antennapod.AppConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.util.BitmapDecoder;

public class BitmapDecodeWorkerTask extends Thread {

	protected int PREFERRED_LENGTH;

	/** Can be thumbnail or cover */
	protected int imageType;

	private static final String TAG = "BitmapDecodeWorkerTask";
	private ImageView target;
	protected CachedBitmap cBitmap;

	protected String fileUrl;

	private Handler handler;

	private final int defaultCoverResource;

	public BitmapDecodeWorkerTask(Handler handler, ImageView target,
			String fileUrl, int length, int imageType) {
		super();
		this.handler = handler;
		this.target = target;
		this.fileUrl = fileUrl;
		this.PREFERRED_LENGTH = length;
		this.imageType = imageType;
		TypedArray res = target.getContext().obtainStyledAttributes(
				new int[] { R.attr.default_cover });
		this.defaultCoverResource = res.getResourceId(0, 0);
		res.recycle();
	}

	/**
	 * Should return true if tag of the imageview is still the same it was
	 * before the bitmap was decoded
	 */
	protected boolean tagsMatching(ImageView target) {
		return target.getTag() == null || target.getTag() == fileUrl;
	}

	protected void onPostExecute() {
		// check if imageview is still supposed to display this image
		if (tagsMatching(target) && cBitmap.getBitmap() != null) {
			target.setImageBitmap(cBitmap.getBitmap());
		} else {
			if (AppConfig.DEBUG)
				Log.d(TAG, "Not displaying image");
		}
	}

	@Override
	public void run() {
		File f = null;
		if (fileUrl != null) {
			f = new File(fileUrl);
		}
		if (fileUrl != null && f.exists()) {
			cBitmap = new CachedBitmap(BitmapDecoder.decodeBitmap(
					PREFERRED_LENGTH, fileUrl), PREFERRED_LENGTH);
			if (cBitmap.getBitmap() != null) {
				storeBitmapInCache(cBitmap);
			} else {
				Log.w(TAG, "Could not load bitmap. Using default image.");
				cBitmap = new CachedBitmap(BitmapFactory.decodeResource(
						target.getResources(), defaultCoverResource),
						PREFERRED_LENGTH);
			}
			if (AppConfig.DEBUG)
				Log.d(TAG, "Finished loading bitmaps");
		} else {
			if (fileUrl == null) {
				Log.w(TAG, "File URL is null");
			} else {
				Log.w(TAG, "File does not exist anymore.");
			}
			onInvalidFileUrl();
		}
		endBackgroundTask();
	}

	protected final void endBackgroundTask() {
		handler.post(new Runnable() {

			@Override
			public void run() {
				onPostExecute();
			}

		});
	}

	protected void onInvalidFileUrl() {
		Log.e(TAG, "FeedImage has no valid file url. Using default image");
		cBitmap = new CachedBitmap(BitmapFactory.decodeResource(
				target.getResources(), defaultCoverResource), PREFERRED_LENGTH);
	}

	protected void storeBitmapInCache(CachedBitmap cb) {
		ImageLoader loader = ImageLoader.getInstance();
		if (imageType == ImageLoader.IMAGE_TYPE_COVER) {
			loader.addBitmapToCoverCache(fileUrl, cb);
		} else if (imageType == ImageLoader.IMAGE_TYPE_THUMBNAIL) {
			loader.addBitmapToThumbnailCache(fileUrl, cb);
		}
	}
}
