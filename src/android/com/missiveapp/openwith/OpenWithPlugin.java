package com.missiveapp.openwith;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import android.util.Log;
//import android.util.SparseArray;
//
//import com.google.android.gms.vision.Frame;
//import com.google.android.gms.vision.barcode.Barcode;
//import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is the entry point of the openwith plugin
 *
 * @author Jean-Christophe Hoelt
 */
public class OpenWithPlugin extends CordovaPlugin {

  /**
   * How the plugin name shows in logs
   */
  private final String PLUGIN_NAME = "OpenWithPlugin";

  /**
   * Maximal verbosity, log everything
   */
  private final int DEBUG = 0;
  /**
   * Default verbosity, log interesting stuff only
   */
  private final int INFO = 10;
  /**
   * Low verbosity, log only warnings and errors
   */
  private final int WARN = 20;
  /**
   * Minimal verbosity, log only errors
   */
  private final int ERROR = 30;

  /**
   * Current verbosity level, changed with setVerbosity
   */
  private int verbosity = INFO;

  /**
   * Log to the console if verbosity level is greater or equal to level
   */
  private void log(final int level, final String message) {
    switch (level) {
      case DEBUG:
        Log.d(PLUGIN_NAME, message);
        break;
      case INFO:
        Log.i(PLUGIN_NAME, message);
        break;
      case WARN:
        Log.w(PLUGIN_NAME, message);
        break;
      case ERROR:
        Log.e(PLUGIN_NAME, message);
        break;
    }
    if (level >= verbosity && loggerContext != null) {
      final PluginResult result = new PluginResult(
        PluginResult.Status.OK,
        String.format("%d:%s", level, message));
      result.setKeepCallback(true);
      loggerContext.sendPluginResult(result);
    }
  }

  /**
   * Callback to the javascript onNewFile method
   */
  private CallbackContext handlerContext;

  /**
   * Callback to the javascript logger method
   */
  private CallbackContext loggerContext;

  /**
   * Intents added before the handler has been registered
   */
  private ArrayList pendingIntents = new ArrayList(); //NOPMD

  /**
   * Track if onNewIntent() was called by the framework (not by init())
   * This helps us understand the sequence of events
   */
  private boolean onNewIntentCalledByFramework = false;

  /**
   * Track if onNewIntent() has been called for the current activity's intent.
   * This prevents duplicate processing.
   * TEMPORARILY DISABLED: We're investigating the root cause of duplicate processing.
   */
  // private String lastProcessedIntentKey = null;

  /**
   * Called when the WebView does a top-level navigation or refreshes.
   * <p>
   * Plugins should stop any long-running processes and clean up internal state.
   * <p>
   * Does nothing by default.
   */
  @Override
  public void onReset() {
    verbosity = INFO;
    handlerContext = null;
    loggerContext = null;
    pendingIntents.clear();
    onNewIntentCalledByFramework = false;
    // lastProcessedIntentKey = null;
  }


//  protected void decodeQR(Intent imgIntent){
//    Context context = this.cordova.getContext();
//    BarcodeDetector detector =
//      new BarcodeDetector.Builder(context)
//        .setBarcodeFormats(Barcode.DATA_MATRIX | Barcode.QR_CODE)
//        .build();
//    if(!detector.isOperational()){
//      Log.d("QR_READ","Could not set up the detector!");
//    }
//    Frame frame = new Frame.Builder().setBitmap(bitmap).build();
//    SparseArray<Barcode> barcodes = detector.detect(frame);
//    Log.d("QR_READ","-barcodeLength-"+barcodes.size());
//    Barcode thisCode=null;
//    if(barcodes.size()==0){
//      Log.d("QR_VALUE","--NODATA");
//    }
//    else if(barcodes.size()==1){
//      thisCode = barcodes.valueAt(0);
//      Log.d("QR_VALUE","--"+thisCode.rawValue);
//    }
//    else{
//      for(int iter=0;iter<barcodes.size();iter++) {
//        thisCode = barcodes.valueAt(iter);
//        Log.d("QR_VALUE","--"+thisCode.rawValue);
//      }
//    }
//  }

  /**
   * Generic plugin command executor
   *
   * @param action
   * @param data
   * @param callbackContext
   * @return
   */
  @Override
  public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
    log(DEBUG, "execute() called with action:" + action + " and options: " + data);
    if ("setVerbosity".equals(action)) {
      return setVerbosity(data, callbackContext);
    } else if ("init".equals(action)) {
      return init(data, callbackContext);
    } else if ("setHandler".equals(action)) {
      return setHandler(data, callbackContext);
    } else if ("setLogger".equals(action)) {
      return setLogger(data, callbackContext);
    } else if ("load".equals(action)) {
      return load(data, callbackContext);
    } else if ("exit".equals(action)) {
      return exit(data, callbackContext);
    }
    log(DEBUG, "execute() did not recognize this action: " + action);
    return false;
  }

  public boolean setVerbosity(final JSONArray data, final CallbackContext context) {
    log(DEBUG, "setVerbosity() " + data);
    if (data.length() != 1) {
      log(WARN, "setVerbosity() -> invalidAction");
      return false;
    }
    try {
      verbosity = data.getInt(0);
      log(DEBUG, "setVerbosity() -> ok");
      return PluginResultSender.ok(context);
    } catch (JSONException ex) {
      log(WARN, "setVerbosity() -> invalidAction");
      return false;
    }
  }

  // Initialize the plugin
  public boolean init(final JSONArray data, final CallbackContext context) {
    log(DEBUG, "init() " + data);
    if (data.length() != 0) {
      log(WARN, "init() -> invalidAction");
      return false;
    }
    // Process the intent that launched the app (cold start).
    // Only process if onNewIntent() hasn't already processed it.
    Intent intent = cordova.getActivity().getIntent();
    
    if (intent != null) {
      // Decision logic: Only process in init() if the framework did NOT call onNewIntent().
      // If framework called it, onNewIntent() will process it when handler is set.
      if (onNewIntentCalledByFramework) {
        log(DEBUG, "init() - Framework already called onNewIntent(), skipping");
      } else {
        log(DEBUG, "init() - Processing intent from onCreate (cold start)");
        onNewIntent(intent);
      }
    }
    log(DEBUG, "init() -> ok");
    return PluginResultSender.ok(context);
  }

  // Exit after processing
  public boolean exit(final JSONArray data, final CallbackContext context) {
    log(DEBUG, "exit() " + data);
    if (data.length() != 0) {
      log(WARN, "exit() -> invalidAction");
      return false;
    }
    cordova.getActivity().moveTaskToBack(true);
    log(DEBUG, "exit() -> ok");
    return PluginResultSender.ok(context);
  }

  public boolean setHandler(final JSONArray data, final CallbackContext context) {
    log(DEBUG, "setHandler() " + data);
    if (data.length() != 0) {
      log(WARN, "setHandler() -> invalidAction");
      return false;
    }
    handlerContext = context;
    // Process any pending intents that were added before the handler was set.
    // This handles the case where framework called onNewIntent() before setHandler().
    if (pendingIntents.size() > 0) {
      log(DEBUG, "setHandler() - Processing " + pendingIntents.size() + " pending intent(s)");
      processPendingIntents();
    }
    log(DEBUG, "setHandler() -> ok");
    return PluginResultSender.noResult(context, true);
  }

  public boolean setLogger(final JSONArray data, final CallbackContext context) {
    log(DEBUG, "setLogger() " + data);
    if (data.length() != 0) {
      log(WARN, "setLogger() -> invalidAction");
      return false;
    }
    loggerContext = context;
    log(DEBUG, "setLogger() -> ok");
    return PluginResultSender.noResult(context, true);
  }

  public boolean load(final JSONArray data, final CallbackContext context) {
    log(DEBUG, "load()");
    if (data.length() != 1) {
      log(WARN, "load() -> invalidAction");
      return false;
    }
    final ContentResolver contentResolver = this.cordova
      .getActivity().getApplicationContext().getContentResolver();
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          final JSONObject fileDescriptor = data.getJSONObject(0);
          final Uri uri = Uri.parse(fileDescriptor.getString("uri"));
          final String data = Serializer.getDataFromURI(contentResolver, uri);
          final PluginResult result = new PluginResult(PluginResult.Status.OK, data);
          context.sendPluginResult(result);
          log(DEBUG, "load() " + uri + " -> ok");
        } catch (JSONException e) {
          final PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
          context.sendPluginResult(result);
          log(DEBUG, "load() -> json error");
        }
      }
    });
    return true;
  }

  /**
   * This is called when a new intent is sent while the app is already opened.
   * <p>
   * We also call it manually with the cordova application intent when the plugin
   * is initialized (so all intents will be managed by this method).
   */
  @Override
  public void onNewIntent(final Intent intent) {
    // Check if this is called by the framework or by init()
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    boolean calledByInit = false;
    for (StackTraceElement element : stack) {
      if (element.getClassName().contains("OpenWithPlugin") && element.getMethodName().equals("init")) {
        calledByInit = true;
        break;
      }
    }
    
    if (!calledByInit) {
      onNewIntentCalledByFramework = true;
    }
    
    if (intent == null) {
      log(WARN, "onNewIntent() - Intent is null!");
      return;
    }
    log(DEBUG, "onNewIntent() " + intent.getAction());

    IntentActivity.StartActivityFun startAction = (JSONObject json) -> {
      if (json != null) {
        pendingIntents.add(json);
      }
      processPendingIntents();
    };
    populateInfoAndSend(intent, startAction);
  }

  /**
   * Creates a unique key for an intent to identify duplicates.
   * Uses action, data URI, type, and a hash of the processed JSON (if present) to create the key.
   * 
   * IMPORTANT: The key must be consistent across multiple reads of the same intent.
   * For share intents, we use a combination of action, type, and a stable identifier
   * from the intent's content (URI or stream data) rather than relying on extras hash
   * which can change as the intent is processed.
   */
  private String createIntentKey(final Intent intent) {
    if (intent == null) {
      return null;
    }
    StringBuilder key = new StringBuilder();
    key.append(intent.getAction());
    
    Bundle extras = intent.getExtras();
    // For intents processed by IntentActivity, the "json" extra contains the processed data
    // Use this as the primary identifier to prevent duplicate processing
    if (extras != null && extras.get("json") != null) {
      String jsonString = extras.get("json").toString();
      // Use hash of JSON string to create a unique key for this processed intent
      int jsonHash = jsonString.hashCode();
      key.append("|json:").append(jsonHash);
    } else {
      // For intents not yet processed, use data URI and type
      // These are stable identifiers that don't change as the intent is processed
      if (intent.getData() != null) {
        key.append("|").append(intent.getData().toString());
      }
      if (intent.getType() != null) {
        key.append("|").append(intent.getType());
      }
      // For share intents, also include a hash of the stream URI if present
      // This is more stable than the full extras hash
      if (extras != null) {
        android.net.Uri streamUri = extras.getParcelable(Intent.EXTRA_STREAM);
        if (streamUri != null) {
          key.append("|stream:").append(streamUri.toString().hashCode());
        } else {
          // Fallback to extras hash only if no stream URI
          // But use a more stable approach - hash only the keys, not values
          java.util.Set<String> keys = extras.keySet();
          int keysHash = keys.toString().hashCode();
          key.append("|keys:").append(keysHash);
        }
      }
    }
    return key.toString();
  }

  /**
   * When the handler is defined, call it with all attached files.
   */
  private void processPendingIntents() {
    if (handlerContext == null) {
      log(WARN, "processPendingIntents() - handlerContext is null, cannot send to JavaScript");
      return;
    }
    int pendingSize = pendingIntents.size();
    for (int i = 0; i < pendingSize; i++) {
      JSONObject intent = (JSONObject) pendingIntents.get(i);
      sendIntentToJavascript(intent);
    }
    pendingIntents.clear();
  }

  /**
   * Calls the javascript intent handlers.
   */
  private void sendIntentToJavascript(final JSONObject intent) {
    try {
      final PluginResult result = new PluginResult(PluginResult.Status.OK, intent);
      result.setKeepCallback(true);
      handlerContext.sendPluginResult(result);
    } catch (Exception e) {
      log(ERROR, "sendIntentToJavascript() - Error sending intent to JavaScript: " + e.getMessage());
      e.printStackTrace();
    }
  }


  interface PopulateHtmlText {
    void start(String content);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private void _asyncPopulateHtmlContentAndSend(String _url, PopulateHtmlText _startHtmlText) {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Future future = executor.submit(() -> {
      HttpURLConnection urlConnection = null;
      String htmlText = null;
      try {
        URL url = new URL(_url);


        urlConnection = (HttpURLConnection) url
          .openConnection();

        InputStream in = urlConnection.getInputStream();

        htmlText = new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
          .collect(Collectors.joining("\n"));
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (urlConnection != null) {
          urlConnection.disconnect();
        }
        ;
        _startHtmlText.start(htmlText);
      }
      ;


    });


  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private void populateHtmlContentAndSend(JSONObject urlItem, IntentActivity.StartActivityFun startActivityFun) {
    try {
      if (urlItem.has("items")) {
        JSONArray items = urlItem.getJSONArray("items");
        if (items != null) {
          JSONObject item = items.getJSONObject(0);
          if (item.has("url")) {
            String url = item.getString("url");
            PopulateHtmlText _startHtmlText = (String content) -> {
              if (content != null) {
                try {
                  item.put("content", content);
                } catch (JSONException e) {
                  e.printStackTrace();
                }
              }
              ;
              startActivityFun.start(urlItem);
            };
            this._asyncPopulateHtmlContentAndSend(url, _startHtmlText);
            return;

          }
        }
      }
      ;
      startActivityFun.start(urlItem);
    } catch (JSONException e) {
      e.printStackTrace();
    }


  }

  /**
   * Converts an intent to JSON
   */
  private void populateInfoAndSend(final Intent intent, IntentActivity.StartActivityFun startAction) {
    log(DEBUG, "populateInfoAndSend() called - action: " + intent.getAction());
    Bundle extras = intent.getExtras();
    try {
      if (extras != null && extras.get("json") != null) {
        log(INFO, "populateInfoAndSend() - Found processed JSON in extras (from IntentActivity)");
        JSONObject urlItem = new JSONObject(extras.get("json").toString());
        this.populateHtmlContentAndSend(urlItem, startAction);
        return;
      }
      log(INFO, "populateInfoAndSend() - No processed JSON found, calling Serializer.populateAndSendIntent()");
      Serializer.populateAndSendIntent(this.cordova.getActivity(), intent, startAction);
    } catch (JSONException e) {
      log(ERROR, "populateInfoAndSend() - Error converting intent to JSON: " + e.getMessage());
      log(ERROR, Arrays.toString(e.getStackTrace()));
    }
  }
}
