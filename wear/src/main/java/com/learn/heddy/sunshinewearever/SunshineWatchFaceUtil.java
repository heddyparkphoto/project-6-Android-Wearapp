package com.learn.heddy.sunshinewearever;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;

/**
 * Created by hyeryungpark on 2/16/17.
 */

public class SunshineWatchFaceUtil {
    public static final String TAG = "SunshineWatchFaceUtil";
    /* *
     * The path for the {@link DataItem} containing {@link DigitalWatchFaceService} configuration.
     */
    public static final String PATH_SUNSHINE_WALLPAPER = "/sunshinewearever";
    public static final String IMAGE_KEY = "image";
    public static final String HIGH_LOW_KEY = "high_low";
    private static final int REQUEST_RESOLVE_ERROR = 1000;

    private static String mHigh_low;
    private static int mImageId;

    public Icon weather_condition_icon;
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    public SunshineWatchFaceUtil(Context context){
        super();

        mContext = context;
    }

    public static void setTodayData(String tempratureString, int wid){
        Log.d(TAG, "setTodayData()");

//        weather_condition_icon = icon;
        mHigh_low = tempratureString;
        mImageId = wid;
    }


    public static String fetchSunshineData(Context context){
        return mHigh_low;
    }
}
