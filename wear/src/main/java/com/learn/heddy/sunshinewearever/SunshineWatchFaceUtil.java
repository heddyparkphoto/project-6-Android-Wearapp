package com.learn.heddy.sunshinewearever;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

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
    public static final String BITMAP_KEY = "bitmap";

    private static String mHigh_low;
    private static int mImageId;
    private static Bitmap mBitmap;

    public Icon weather_condition_icon;
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    public SunshineWatchFaceUtil(Context context){
        super();

        mContext = context;
    }

    public static void setTodayData(String tempratureString, int wid, Bitmap wBitmap){
        mHigh_low = tempratureString;
        mImageId = wid;
        mBitmap = wBitmap;
    }

    public static TodayData fetchSunshineData(Context context){
        return new TodayData(mHigh_low, mBitmap);
    }

    public static class TodayData {

        private String high_low;
        private Bitmap weatherImage;

        public TodayData(){
            super();
        }

        public TodayData(String high_low, Bitmap image){
            this.high_low = high_low;
            this.weatherImage = image;
        }

        public String getHigh_low(){
            return high_low;
        }

        public Bitmap getWeatherImage(){
            return weatherImage;
        }
    }
}
