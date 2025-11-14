package com.missiveapp.openwith;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.common.util.IOUtils;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handle serialization of Android objects ready to be sent to javascript.
 */
class Serializer {

  static final int MAX_ITEMS = 5;

  public interface PopulateItemsAndSendIntent {
    void start(JSONArray items);
  }

  /**
   * Convert an intent to JSON.
   * <p>
   * This actually only exports stuff necessary to see file content
   * (streams or clip data) sent with the intent.
   * If none are specified, null is return.
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  public static void populateAndSendIntent(
    Activity activity,
//            final ContentResolver contentResolver,
    final Intent intent,
    IntentActivity.StartActivityFun startActivityFun
  ) throws JSONException {
//    final ContentResolver contentResolver = activity.getContentResolver();
//    StringBuilder text = new StringBuilder();
    PopulateItemsAndSendIntent sendIntent = (JSONArray items) -> {
      final JSONObject action = new JSONObject();

      try {
        action.put("action", translateAction(intent.getAction()));
        action.put("exit", readExitOnSent(intent.getExtras()));
        action.put("items", items);
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text != null) {
          action.put("text", text);
        }
        startActivityFun.start(action);
      } catch (JSONException e) {
        e.printStackTrace();
      }

    };

    readIntent(activity, intent, sendIntent);

  }

  public static String translateAction(final String action) {
    if ("android.intent.action.SEND".equals(action) ||
      "android.intent.action.SEND_MULTIPLE".equals(action)) {
      return "SEND";
    } else if ("android.intent.action.VIEW".equals(action)) {
      return "VIEW";
    }
    return action;
  }

  /**
   * Read the value of "exit_on_sent" in the intent's extra.
   * <p>
   * Defaults to false.
   */
  public static boolean readExitOnSent(final Bundle extras) {
    if (extras == null) {
      return false;
    }
    return extras.getBoolean("exit_on_sent", false);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public static void readIntent(Activity activity, Intent intent, PopulateItemsAndSendIntent sendIntent) {
    String action = intent.getAction();
    String type = intent.getType();

    if (Intent.ACTION_SEND.equals(action) && type != null) {
      if ("text/plain".equals(type)) {
        handleUrlAndSend(activity, intent, sendIntent);
        return;
      } else if (type.startsWith("image/")) {
        JSONArray items = handleSendImage(activity, intent, type); // Handle single image being sent
        sendIntent.start(items);
        return;
      }
    } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
      if (type.startsWith("image/")) {
        JSONArray items = handleSendMultipleImages(activity, type, intent); // Handle multiple images being sent
        sendIntent.start(items);
        return;
      }
    }
//        return null;
  }

//  public interface PopulateHtmlTextAndSendIntent {
//    void start(String htmltext);
//    }


  @RequiresApi(api = Build.VERSION_CODES.N)
  static void handleUrlAndSend(Activity activity, Intent intent, PopulateItemsAndSendIntent _sendIntent) {

    Bundle extras = intent.getExtras();
    if (extras != null) {
      intent.getBundleExtra(Intent.EXTRA_STREAM);
    }
    // following are all extractable info
    String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
    String browserUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
    int taskId = intent.getIntExtra("org.chromium.chrome.extra.TASK_ID", 0);
    Uri screenshotUri = extras.getParcelable("share_screenshot_as_stream");


//    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//    StrictMode.setThreadPolicy(policy);


    try {
      JSONObject urlItem = new JSONObject();
      JSONArray items = new JSONArray();
//      if (htmlText != null) {
//        urlItem.put("content", htmlText);
//      }

      urlItem.put("url", browserUrl);
      urlItem.put("title", subject);
      urlItem.put("uti", "public.url");
      if (screenshotUri!=null){
        final String dataFromURI = getDataFromURI(activity.getContentResolver(), screenshotUri);
        urlItem.put("base64", dataFromURI);
        urlItem.put("imageType", "image/jpeg");
      }
      items.put(urlItem);

      if (screenshotUri!=null){
        decodeQRFromPreviewUri(urlItem, activity, screenshotUri);
      }

      _sendIntent.start((items));
    } catch (JSONException e) {
      e.printStackTrace();
    }

  }

  static JSONArray handleSendImage(Activity activity, Intent intent, String type) {
    Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    if (imageUri != null) {
      JSONObject[] items = new JSONObject[1];
      try {
        items[0] = imgToJson(activity, type, imageUri);
        return new JSONArray(items);
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }


  static JSONArray handleSendMultipleImages(Activity activity, String type, Intent intent) {
    ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    List<JSONObject> items = new LinkedList<>();
    if (imageUris != null) {
      for (Uri uri : imageUris) {
        try {
          items.add(imgToJson(activity, type, uri));
          if (items.size() == MAX_ITEMS) {
            break;
          }
        } catch (Exception e) {
          // Do nothing here
        }
      }
    }
    if (items.size() > 0) {
      return new JSONArray(items);
    } else {
      return null;
    }
  }

  @Nullable
  private static JSONObject imgToJson(Activity activity, String type, Uri imageUri) throws Exception {
    JSONObject items = new JSONObject();
    items.put("type", type);
    items.put("uti", "public.image");
    populatePathInfo(items, activity.getContentResolver(), imageUri);
    items.put("data", getDataFromURI(activity.getContentResolver(), imageUri));
    decodeQR(items, activity, imageUri);
    return items;
  }


  /**
   * Extract the list of items from clip data (if available).
   * <p>
   * Defaults to null.
   */
  public static JSONArray itemsFromClipData(
    final ContentResolver contentResolver,
    final ClipData clipData)
    throws JSONException {
    if (clipData != null) {
      final int clipItemCount = Math.max(MAX_ITEMS, clipData.getItemCount());
      JSONObject[] items = new JSONObject[clipItemCount];
      for (int i = 0; i < clipItemCount; i++) {
        items[i] = toJSONObject(contentResolver, clipData.getItemAt(i).getUri());
      }
      return new JSONArray(items);
    }
    return null;
  }

  /**
   * Extract the list of items from the intent's extra stream.
   * <p>
   * See Intent.EXTRA_STREAM for details.
   */
  public static JSONArray itemsFromExtras(
    final ContentResolver contentResolver,
    final Bundle extras)
    throws JSONException {
    if (extras == null) {
      return null;
    }
    final JSONObject item = toJSONObject(
      contentResolver,
      (Uri) extras.get(Intent.EXTRA_STREAM));
    if (item == null) {
      return null;
    }
    final JSONObject[] items = new JSONObject[1];
    items[0] = item;
    return new JSONArray(items);
  }

  /**
   * Convert an Uri to JSON object.
   * <p>
   * Object will include:
   * "type" of data;
   * "uri" itself;
   * "path" to the file, if applicable.
   * "data" for the file.
   */
  public static JSONObject toJSONObject(
    final ContentResolver contentResolver,
    final Uri uri)
    throws JSONException {
    if (uri == null) {
      return null;
    }
    final JSONObject json = new JSONObject();
    final String type = contentResolver.getType(uri);
    json.put("type", type);
    json.put("uri", uri);
    populatePathInfo(json, contentResolver, uri);
//        json.put("path", getRealPathFromURI(contentResolver, uri));
    return json;
  }

  /**
   * Return data contained at a given Uri as Base64. Defaults to null.
   */
  public static String getDataFromURI(
    final ContentResolver contentResolver,
    final Uri uri) {
    InputStream inputStream = null;// = contentResolver.openInputStream(uri);

    try {
      inputStream = contentResolver.openInputStream(uri);
      final byte[] bytes = ByteStreams.toByteArray(inputStream);
      inputStream.close();
      return Base64.encodeToString(bytes, Base64.DEFAULT);
    } catch (IOException e) {
      return "";
    }
  }

  interface BitmapResolver {
    Bitmap start(ContentResolver resolver, Uri uri) throws IOException;
  }

  protected static void decodeQR(JSONObject json, final Activity activity, Uri imageUri) {
    BitmapResolver bitmapResover = MediaStore.Images.Media::getBitmap;
    _decodeQR(json, activity, imageUri, bitmapResover);
  }

  protected static void decodeQRFromPreviewUri(JSONObject json, final Activity activity, Uri previewUri) {
    BitmapResolver bitmapResolver = (ContentResolver contentResolver, Uri uri) -> {
      BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
      final InputStream input = contentResolver.openInputStream(uri);
      Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
      input.close();
      return bitmap;
    };
    _decodeQR(json, activity, previewUri, bitmapResolver);

  }

  protected static void _decodeQR(JSONObject json, final Activity activity, Uri imageUri, BitmapResolver bitmapResolver) {
    Context context = activity.getApplicationContext();
    try {
      json.put("processed", true);
      Bitmap bitmap = bitmapResolver.start(activity.getContentResolver(), imageUri);
      BarcodeDetector detector =
        new BarcodeDetector.Builder(context)
          .setBarcodeFormats(Barcode.DATA_MATRIX | Barcode.QR_CODE)
          .build();
      if (!detector.isOperational()) {
        Log.d("QR_READ", "Could not set up the detector!");
      }
      Frame frame = new Frame.Builder().setBitmap(bitmap).build();
      SparseArray<Barcode> barcodes = detector.detect(frame);
      Log.d("QR_READ", "-barcodeLength-" + barcodes.size());
      Barcode thisCode = null;
      if (barcodes.size() == 0) {
        return;
      }
      JSONArray barcodeArray = new JSONArray();
      for (int iter = 0; iter < barcodes.size(); iter++) {
        thisCode = barcodes.valueAt(iter);
        Log.d("QR_VALUE", "--" + thisCode.rawValue);
        barcodeArray.put(thisCode.rawValue);
      }
      ;
//      try {
      json.put("qrStrings", barcodeArray);
//      } catch (JSONException e) {
//        e.printStackTrace();
//      }


      if (barcodes.size() == 0) {
        Log.d("QR_VALUE", "--NODATA");
      } else if (barcodes.size() == 1) {
        thisCode = barcodes.valueAt(0);
        Log.d("QR_VALUE", "--" + thisCode.rawValue);
      } else {
        for (int iter = 0; iter < barcodes.size(); iter++) {
          thisCode = barcodes.valueAt(iter);
          Log.d("QR_VALUE", "--" + thisCode.rawValue);
        }
      }

    } catch (IOException | JSONException e) {
      e.printStackTrace();
    }

  }


  /**
   * Convert the Uri to the direct file system path of the image file.
   * <p>
   * source: https://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/20402190?noredirect=1#comment30507493_20402190
   */
  public static void populatePathInfo(
    final JSONObject json,
    final ContentResolver contentResolver,
    final Uri uri) {
    try {
      final String[] proj = {MediaStore.Images.Media.DATA};
      final Cursor cursor = contentResolver.query(uri, proj, null, null, null);
      if (cursor == null) {
        return;
      }
      final int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
      if (column_index < 0) {
        cursor.close();
        return;
      }
      cursor.moveToFirst();
      final String result = cursor.getString(column_index);
      cursor.close();
      File file = new File(result);
      String path = file.getName();
      json.put("url", result);
      json.put("name", file.getName());
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }
}
