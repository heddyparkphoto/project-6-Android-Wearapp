package com.learn.heddy.sunshinewearever;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.learn.heddy.sunshinewearever.SunshineWatchFaceUtil.HIGH_LOW_KEY;
import static com.learn.heddy.sunshinewearever.SunshineWatchFaceUtil.IMAGE_KEY;

/**
 * Created by hyeryungpark on 2/16/17.
 */

public class SunshineDataListenerService extends WearableListenerService
{
    private static final String TAG = "SunshineDataListener";

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "onDataChanged: " + dataEventBuffer);

        final List<DataEvent> freezableLocalizedData = FreezableUtils
                .freezeIterable(dataEventBuffer);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        } else {
            Log.d(TAG, "Success: connected to GoogleApiClient.");
        }

        for (DataEvent event : freezableLocalizedData) {
            Log.d(TAG, "onDataChanged(): event not empty?? " + dataEventBuffer);

            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (SunshineWatchFaceUtil.PATH_SUNSHINE_WALLPAPER.equals(path)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    DataMap dataMap = dataMapItem.getDataMap();
                    String high_low = dataMap.getString(HIGH_LOW_KEY);
                    int imageId = dataMap.getInt(IMAGE_KEY);
                    SunshineWatchFaceUtil.setTodayData(high_low, imageId);
                } else {
                    Log.w(TAG, "Unknown URI path: " + path);
                }
            } else {Log.w(TAG, "Unknown event Type = " + event.getType());
            }
        }
    }

}
