/*
	    Copyright 2014 Giovanni Di Gregorio.

		Licensed under the Apache License, Version 2.0 (the "License");
		you may not use this file except in compliance with the License.
		You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

		Unless required by applicable law or agreed to in writing, software
		distributed under the License is distributed on an "AS IS" BASIS,
		WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		See the License for the specific language governing permissions and
   		limitations under the License.
 */

package com.tmantman.nativecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;

import com.tmantman.nativecamera.ExifHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import static android.R.attr.bitmap;

/**
 * This class launches the camera view, allows the user to take a picture,
 * closes the camera view, and returns the captured image. When the camera view
 * is closed, the screen displayed before the camera view was shown is
 * redisplayed.
 */
public class NativeCameraLauncher extends CordovaPlugin {

	private static final String LOG_TAG = "NativeCameraLauncher";

	private int mQuality;
	private int targetWidth;
	private int targetHeight;
	private Uri imageUri;
	private File photo;
	private static final String _DATA = "_data";
	private CallbackContext callbackContext;
	private String date = null;

	public NativeCameraLauncher() {
	}

	void failPicture(String reason) {
		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, reason));
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";
		this.callbackContext = callbackContext;
		try {
			if (action.equals("takePicture")) {
				this.targetHeight = 0;
				this.targetWidth = 0;
				this.mQuality = 80;
				this.targetHeight = args.getInt(4);
				this.targetWidth = args.getInt(3);
				this.mQuality = args.getInt(0);
				this.takePicture();
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				callbackContext.sendPluginResult(r);
				return true;
			}
			return false;
		} catch (JSONException e) {
			e.printStackTrace();
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
			return true;
		}
	}

	public void takePicture() {
		// Save the number of images currently on disk for later
		Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), CameraActivity.class);
		this.photo = createCaptureFile();
		this.imageUri = Uri.fromFile(photo);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, this.imageUri);
		this.cordova.startActivityForResult((CordovaPlugin) this, intent, 1);
	}

	private File createCaptureFile() {
		File oldFile = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		if(oldFile.exists())
			oldFile.delete();
		Calendar c = Calendar.getInstance();
	    this.date = "" + c.get(Calendar.DAY_OF_MONTH)
					+ c.get(Calendar.MONTH)
					+ c.get(Calendar.YEAR)
					+ c.get(Calendar.HOUR_OF_DAY)
					+ c.get(Calendar.MINUTE)
					+ c.get(Calendar.SECOND);
		File photo = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		return photo;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// If image available
		if (resultCode == Activity.RESULT_OK) {
			int rotate = 0;
			try {
				// Create an ExifHelper to save the exif data that is lost
				// during compression
				ExifHelper exif = new ExifHelper();
				exif.createInFile(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext())
						+ "/Pic-" + this.date + ".jpg");
				exif.readExifData();
				rotate = exif.getOrientation();
				Log.i(LOG_TAG, "Uncompressed image rotation value: " + rotate);

				// Read in bitmap of captured image
				Bitmap bitmap;

				bitmap = scaleBitmap(imageUri);

				if (bitmap == null) {
					Uri uri = intent.getData();
					android.content.ContentResolver resolver = this.cordova.getActivity().getContentResolver();
					bitmap = android.graphics.BitmapFactory
							.decodeStream(resolver.openInputStream(uri));
				}

//				try {
//                    BitmapFactory.Options options = new BitmapFactory.Options();
//                    options.inSampleSize = 8;
//                    bitmap = BitmapFactory.decodeStream(this.cordova.getActivity().getContentResolver().openInputStream(imageUri), null, options);
//					//bitmap = android.provider.MediaStore.Images.Media
//							//.getBitmap(this.cordova.getActivity().getContentResolver(), imageUri);
//				} catch (FileNotFoundException e) {
//					Uri uri = intent.getData();
//					android.content.ContentResolver resolver = this.cordova.getActivity().getContentResolver();
//					bitmap = android.graphics.BitmapFactory
//							.decodeStream(resolver.openInputStream(uri));
//				}

				// If bitmap cannot be decoded, this may return null
				if (bitmap == null) {
					this.failPicture("Error decoding image.");
					return;
				}

				// Log.i(LOG_TAG, "*Memory before scaling: *");
				// Log.i(LOG_TAG, "getAllocationByteCount: " + bitmap.getAllocationByteCount());
				// Log.i(LOG_TAG, "getByteCount: " + bitmap.getByteCount());

//				bitmap = scaleBitmap(bitmap);
				//Immediately clear the memory associated with previous bitmap
//				System.gc();

				// Log.i(LOG_TAG, "*Memory after scaling: *");
				// Log.i(LOG_TAG, "getAllocationByteCount: " + bitmap.getAllocationByteCount());
				// Log.i(LOG_TAG, "getByteCount: " + bitmap.getByteCount());

				// Add compressed version of captured image to returned media
				// store Uri
				// Log.i(LOG_TAG, "*First rotate then compress*");
				// Log.i(LOG_TAG, "getAllocationByteCount: " + bitmap.getAllocationByteCount());
				// Log.i(LOG_TAG, "getByteCount: " + bitmap.getByteCount());
				bitmap = getRotatedBitmap(rotate, bitmap, exif);
				Log.i(LOG_TAG, "URI: " + this.imageUri.toString());
				OutputStream os = this.cordova.getActivity().getContentResolver()
						.openOutputStream(this.imageUri);
				boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
				// Log.i(LOG_TAG, "Compression success: " + success);
				os.close();
				//Clear the memory
				bitmap.recycle();
				bitmap = null;
				System.gc();
				//Reload the bitmap
				try {
					bitmap = android.provider.MediaStore.Images.Media
							.getBitmap(this.cordova.getActivity().getContentResolver(), imageUri);
				} catch (FileNotFoundException e) {
					Uri uri = intent.getData();
					android.content.ContentResolver resolver = this.cordova.getActivity().getContentResolver();
					bitmap = android.graphics.BitmapFactory
							.decodeStream(resolver.openInputStream(uri));
				}
				// Log.i(LOG_TAG, "*After compression*");
				// Log.i(LOG_TAG, "getAllocationByteCount: " + bitmap.getAllocationByteCount());
				// Log.i(LOG_TAG, "getByteCount: " + bitmap.getByteCount());

				// Restore exif data to file
				exif.createOutFile(this.imageUri.getPath());
				exif.writeExifData();

				// Log.i(LOG_TAG, "Final Exif orientation value: " + exif.getOrientation());

				// Send Uri back to JavaScript for viewing image
				this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, this.imageUri.toString()));

				bitmap.recycle();
				bitmap = null;
				System.gc();
			} catch (IOException e) {
				e.printStackTrace();
				this.failPicture("Error capturing image.");
			}
		}

		// If cancelled
		else if (resultCode == Activity.RESULT_CANCELED) {
			this.failPicture("Camera cancelled.");
		}

		// If something else
		else {
			this.failPicture("Did not complete!");
		}
	}

	@Nullable
	private Bitmap scaleBitmap(Uri imageUri) {

        try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;

			BitmapFactory.decodeStream(this.cordova.getActivity().getContentResolver().openInputStream(imageUri), null, options);


            int targetWidth = this.targetWidth;
            int targetHeight = this.targetHeight;
            int origWidth = options.outWidth;
            int origHeight = options.outHeight;

            // If no new width or height were specified return the original bitmap
            if (targetWidth <= 0 && targetHeight <= 0) {
                options.inSampleSize = 1;
            } else {
                // Only the width was specified
                if (targetWidth > 0 && targetHeight <= 0) {
                    targetHeight = (targetWidth * origHeight) / origWidth;
                }
                // only the height was specified
                else if (targetWidth <= 0 && targetHeight > 0) {
                    targetWidth = (targetHeight * origWidth) / origHeight;
                }

                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
            }

			options.inJustDecodeBounds = false;
			return BitmapFactory.decodeStream(this.cordova.getActivity().getContentResolver().openInputStream(imageUri), null, options);
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	private static int calculateInSampleSize(
			BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) >= reqHeight
					&& (halfWidth / inSampleSize) >= reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	public Bitmap scaleBitmap(Bitmap bitmap) {
		int targetWidth = this.targetWidth;
		int targetHeight = this.targetHeight;
		int origWidth = bitmap.getWidth();
		int origHeight = bitmap.getHeight();

		// If no new width or height were specified return the original bitmap
		if (targetWidth <= 0 && targetHeight <= 0) {
			return bitmap;
		}
		// Only the width was specified
		else if (targetWidth > 0 && targetHeight <= 0) {
			targetHeight = (targetWidth * origHeight) / origWidth;
		}
		// only the height was specified
		else if (targetWidth <= 0 && targetHeight > 0) {
			targetWidth = (targetHeight * origWidth) / origHeight;
		}

		Log.i(LOG_TAG, "ScaledBitmap targetW: " + targetWidth);
		Log.i(LOG_TAG, "ScaledBitmap targetH: " + targetHeight);
		Log.i(LOG_TAG, "ScaledBitmap origW: " + origWidth);
		Log.i(LOG_TAG, "ScaledBitmap origH: " + origHeight);
		//Now we know a value for targetHeight and targetWidth has been set.

		float scaleFactor = getMemorySavingDimensions(bitmap, targetWidth, targetHeight);
		if (scaleFactor <= 1) {
			return bitmap;
		} else {
			//Downsample mechanism 1
			int newWidth = (int)(origWidth / scaleFactor);
			int newHeight = (int)(origHeight / scaleFactor);
			Log.i(LOG_TAG, "ScaledBitmap finalW: " + newWidth);
			Log.i(LOG_TAG, "ScaledBitmap finalH: " + newHeight);
			//return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
			// return scaleMechanism2(bitmap, newWidth, newHeight);

			bitmap = ThumbnailUtils.extractThumbnail(bitmap, newWidth, newHeight,
					ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
			return bitmap;

		}
	}

	private Bitmap scaleMechanism2(Bitmap bitmap, int newWidth, int newHeight) {

		Bitmap scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Config.ARGB_8888);

	    float ratioX = newWidth / (float) bitmap.getWidth();
	    float ratioY = newHeight / (float) bitmap.getHeight();
	    float middleX = newWidth / 2.0f;
	    float middleY = newHeight / 2.0f;

	    Matrix scaleMatrix = new Matrix();
	    scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

	    Canvas canvas = new Canvas(scaledBitmap);
	    canvas.setMatrix(scaleMatrix);
	    canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2, middleY - bitmap.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

		Log.i(LOG_TAG, "!Returning new bitmap!");

	    return scaledBitmap;

	}

	private float getMemorySavingDimensions(Bitmap bitmap, int targetWidth, int targetHeight) {
		float ratioX = (float) bitmap.getWidth() / targetWidth;
	    float ratioY = (float) bitmap.getHeight() / targetHeight;
		float scaleFactor = ratioX < ratioY ? ratioX : ratioY;
		if (scaleFactor > 3) {
			scaleFactor = 3;
		}
		Log.i(LOG_TAG, "MemorySavingDimensions ratioX " + ratioX);
		Log.i(LOG_TAG, "MemorySavingDimensions ratioY " + ratioY);
		Log.i(LOG_TAG, "ScaleFactor: " + scaleFactor);
		return scaleFactor;
	}

	private Bitmap getRotatedBitmap(int rotate, Bitmap bitmap, ExifHelper exif) {
        Matrix matrix = new Matrix();
		Log.i(LOG_TAG, "Setting rotation on compressed image: " + rotate);
        matrix.setRotate(rotate);
        try
        {
			Log.d(LOG_TAG, "getRotatedBitmap NOT OUT OF MEMORY");
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            exif.resetOrientation();
        }
        catch (OutOfMemoryError oom)
        {
			Log.d(LOG_TAG, "getRotatedBitmap OUT OF MEMORY");
            // You can run out of memory if the image is very large:
            // http://simonmacdonald.blogspot.ca/2012/07/change-to-camera-code-in-phonegap-190.html
            // If this happens, simply do not rotate the image and return it unmodified.
            // If you do not catch the OutOfMemoryError, the Android app crashes.
        }
        return bitmap;
    }

	private String getTempDirectoryPath(Context ctx) {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			cache = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ "/Android/data/"
					+ ctx.getPackageName() + "/cache/");
		}
		// Use internal storage
		else {
			cache = ctx.getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		if (!cache.exists()) {
			cache.mkdirs();
		}

		return cache.getAbsolutePath();
	}

	@Override
	public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
		this.callbackContext = callbackContext;

		this.mQuality = state.getInt("mQuality");
		this.targetWidth = state.getInt("targetWidth");
		this.targetHeight = state.getInt("targetHeight");

		this.imageUri = state.getParcelable("imageUri");
		this.photo = (File) state.getSerializable("photo");

		this.date = state.getString("date");

		super.onRestoreStateForActivityResult(state, callbackContext);
	}

	@Override
	public Bundle onSaveInstanceState() {

		Bundle state = new Bundle();
		state.putInt("mQuality", mQuality);
		state.putInt("targetWidth", targetWidth);
		state.putInt("targetHeight", targetHeight);
		state.putString("date", date);
		state.putParcelable("imageUri", imageUri);
		state.putSerializable("photo", photo);

		return state;
	}
}
