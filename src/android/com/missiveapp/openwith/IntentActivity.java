package com.missiveapp.openwith;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import android.util.Log;

import org.json.JSONException;

/**
 * Activity that intercepts share intents and processes them before launching MainActivity.
 * 
 * This activity is registered in AndroidManifest to receive ACTION_SEND and ACTION_SEND_MULTIPLE intents.
 * It processes the intent (extracts files, scans QR codes, fetches HTML content) and then
 * launches MainActivity with the processed data in JSON format.
 */
public class IntentActivity extends Activity {
  private static String LOG_TAG = "Shared_IntentActivity";

  /**
   * Interface for launching MainActivity with processed intent data.
   */
  public interface StartActivityFun {
    void start(org.json.JSONObject extraObj);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.v(LOG_TAG, "onCreate - Processing share intent");

    // Process the intent and launch MainActivity
    forceMainActivityReload();

    // Finish this activity immediately (it's just an intermediary)
    finish();
  }

  /**
   * Processes the share intent and launches MainActivity with the processed data.
   * 
   * The intent is processed by Serializer.populateAndSendIntent() which:
   * - Extracts files/images from the intent
   * - Scans for QR codes in images
   * - Fetches HTML content for URLs
   * - Converts everything to JSON format
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  private void forceMainActivityReload() {
    Bundle extras = getIntent().getExtras();
    if (extras == null) {
      Log.w(LOG_TAG, "No extras found in intent");
      return;
    }

    StartActivityFun sendIntent = (org.json.JSONObject json) -> {
      PackageManager pm = getPackageManager();
      Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());

      if (json != null) {
        launchIntent.putExtra("json", json.toString());
      }

      launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      launchIntent.putExtra("cdvStartInBackground", false);
      this.startActivity(launchIntent);
    };

    try {
      Serializer.populateAndSendIntent(this, getIntent(), sendIntent);
    } catch (JSONException e) {
      Log.e(LOG_TAG, "Error processing intent: " + e.getMessage(), e);
    }
  }
}
