package de.danoeh.antennapod.util;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import de.danoeh.antennapod.AppConfig;

public class BitmapDecoder {
	private static final String TAG = "BitmapDecoder";

	private static int calculateSampleSize(int preferredLength, int length) {
		int sampleSize = 1;
		if (length > preferredLength) {
			sampleSize = Math.round(((float) length / (float) preferredLength));
		}
		return sampleSize;
	}

	public static Bitmap decodeBitmap(int preferredLength, String fileUrl) {
		if (fileUrl != null && new File(fileUrl).exists()) {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(fileUrl, options);
			int srcWidth = options.outWidth;
			int srcHeight = options.outHeight;
			int length = Math.max(srcWidth, srcHeight);
			int sampleSize = calculateSampleSize(preferredLength, length);
			if (AppConfig.DEBUG)
				Log.d(TAG, "Using samplesize " + sampleSize);
			options.inJustDecodeBounds = false;
			options.inSampleSize = sampleSize;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;

			Bitmap decodedBitmap = BitmapFactory.decodeFile(fileUrl, options);
			if (decodedBitmap == null) {
				Log.i(TAG,
						"Bitmap could not be decoded in custom sample size. Trying default sample size (path was "
								+ fileUrl + ")");
				decodedBitmap = BitmapFactory.decodeFile(fileUrl);
			}
			return decodedBitmap;
		} else {
			return null;
		}
	}
}
