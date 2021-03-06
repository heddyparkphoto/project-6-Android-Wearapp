package com.learn.heddy.sunshinewearever;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.learn.heddy.sunshinewearever.SunshineWatchFaceUtil.BITMAP_KEY;
import static com.learn.heddy.sunshinewearever.SunshineWatchFaceUtil.HIGH_LOW_KEY;

/**
 * Created by hyeryungpark on 2/16/17.
 *
 * This class's onDataChanged() method is called when the Phone sends the weather data
 * When matching path and keys are found, their values are saved using the
 * SunshineWatchFaceUtil class for the WatchFace service class to display them
 */

public class SunshineDataListenerService extends WearableListenerService
{
    private static final String TAG = "SunshineDataListener";
    GoogleApiClient mGoogleApiClient;

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        final List<DataEvent> freezableLocalizedData = FreezableUtils
                .freezeIterable(dataEventBuffer);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        for (DataEvent event : freezableLocalizedData) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (SunshineWatchFaceUtil.PATH_SUNSHINE_WALLPAPER.equals(path)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    DataMap dataMap = dataMapItem.getDataMap();
                    String high_low = dataMap.getString(HIGH_LOW_KEY);
                    Asset bitmapAsset = dataMapItem.getDataMap()
                            .getAsset(BITMAP_KEY);
                    LoadBitmapAsyncTask asyncTask = (LoadBitmapAsyncTask) new LoadBitmapAsyncTask().execute(bitmapAsset);
                    Bitmap weatherImage = null;
                    try {
                        weatherImage = asyncTask.get();
                    } catch (Exception allEx) {
                        Log.e(TAG, " LoadBitmapAsyncTask async task exception " + allEx);
                    }

                    // Save values for the drawing methods
                    SunshineWatchFaceUtil.setTodayData(high_low, weatherImage);
                } else {
                    Log.w(TAG, "Unknown URI path: " + path);
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED){
                Log.i(TAG, "Event Type: DELETE received.");
            } else {
                Log.w(TAG, "Other Event Type " + event.getType() + " received.");
            }
        }
    }

    /*
     * Extracts {@link android.graphics.Bitmap} data from the
     * {@link com.google.android.gms.wearable.Asset}
     */
    private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Asset... params) {
            if (params.length > 0) {
                Asset asset = params[0];

                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream);
            } else {
                Log.e(TAG, "Asset must be non-null");
                return null;
            }
        }
    }
}
