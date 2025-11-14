# cordova-plugin-shared

> This plugin for [Apache Cordova](https://cordova.apache.org/) registers your app to handle certain types of files.

## Overview

This is a bit modified version of [cordova-plugin-openwith](https://github.com/j3k0/cordova-plugin-openwith) by Jean-Christophe Hoelt for iOS.

#### What's different:

- **Works with several types of shared data** (UTIs). Currently, URLs, text and images are supported. If you would like to remove any of these types, feel free to edit ShareExtension-Info.plist (NSExtensionActivationRule section) after plugin's installation
- **Support of sharing several photos at once is supported**. By default, the maximum number is 10, but this can be easily edited in the plugin's .plist file
- **Ability to check if the user is logged in or not in your app**. If not logged in, a native interface alert message will be displayed instead of starting your app.
- **Does not show native UI with "Post" option**. Having two-step share (enter sharing message and then pick the receiver in the Cordova app) might be a bad user experience, so this plugin opens Cordova application immediately and passes the shared data to it. Thereby, you are expected to implement sharing UI in your Cordova app.

This plugin refers only to iOS, so the Android parts have been cut out both from the plugin and documentation.

You'd like your app to be listed in the **Send to...** section for certain types of files, on both **Android** and **iOS**? This is THE plugin! No need to meddle into Android's manifests and iOS's plist files, it's (almost) all managed for you by a no brainer one liner installation command.

## Table of Contents

- [Installation](#installation)
- [Usage with Capacitor](#usage-with-capacitor)
- [Usage](#usage)
- [API](#api)
- [License](#license)


#### iOS

On iOS, there are many ways apps can communicate. This plugin uses a [Share Extension](https://developer.apple.com/library/content/documentation/General/Conceptual/ExtensibilityPG/Share.html#//apple_ref/doc/uid/TP40014214-CH12-SW1). This is a particular type of App Extension which intent is, as Apple puts it: _"to post to a sharing website or share content with others"_.

A share extension can be used to share any type of content. You have to define which you want to support using an [Universal Type Identifier](https://developer.apple.com/library/content/documentation/FileManagement/Conceptual/understanding_utis/understand_utis_intro/understand_utis_intro.html) (or UTI). For a full list of what your options are, please check [Apple's System-Declared UTI](https://developer.apple.com/library/content/documentation/Miscellaneous/Reference/UTIRef/Articles/System-DeclaredUniformTypeIdentifiers.html#//apple_ref/doc/uid/TP40009259-SW1).

As with all extensions, the flow of events is expected to be handled by a small app, external to your Cordova App but bundled with it. When installing the plugin, we will add a new target called **ShareExtension** to your XCode project which implements this Extension App. The Extension and the Cordova App live in different processes and can only communicate with each other using inter-app communication methods.

When a user posts some content using the Share Extension, the content will be stored in a Shared User-Preferences Container. To enable this, the Cordova App and Share Extension should define a group and add both the app and extension to it.

Once the data is in place in the Shared User-Preferences Container, the Share Extension will open the Cordova App by calling a [Custom URL Scheme](https://developer.apple.com/library/content/documentation/iPhone/Conceptual/iPhoneOSProgrammingGuide/Inter-AppCommunication/Inter-AppCommunication.html#//apple_ref/doc/uid/TP40007072-CH6-SW1). This seems a little borderline as Apple tries hard to prevent this from being possible, but brave iOS developers always find [solutions](https://stackoverflow.com/questions/24297273/openurl-not-work-in-action-extension/24614589#24614589)... So as for now there is one and it seems like people got their app pass the review process with it. At the moment of writing, this method is still working on iOS 11.1. The recommended solution is be to implement the posting logic in the Share Extension, but this doesn't play well with Cordova Apps architecture...

On the Cordova App side, the plugin checks listens for app start or resume events. When this happens, it looks into the Shared User-Preferences Container for any content to share and report it to the javascript application.

## Installation

Here's the promised one liner:

```
cordova plugin add cordova-plugin-shared \
  --variable IOS_URL_SCHEME=cordovaopenwithdemo
```

| variable | example | notes |
|---|---|---|
| `ANDROID_MIME_TYPE` | image/* | Mime type of documents you want to share (wildcards accepted) |
| `IOS_URL_SCHEME` | uniquelonglowercase | Any random long string of lowercase alphabetical characters |
| `DISPLAY_NAME` | My App Name | If you want to use a different name than your project name |
| `PROVISIONING_PROFILE` | a71204b0-8187-4dcb-a343-1d43bd543c76 | The provisioning profile ID of your share extension App ID |
| `DEVELOPMENT_TEAM` | ABCDEFGHIJ | Your Apple team ID |

It shouldn't be too hard. But just in case, Jean-Christophe Hoelt [posted a screencast of it](https://youtu.be/eaE4m_xO1mg).

## Usage with Capacitor

### Android Requirements

**AndroidX Support Required**

This plugin uses AndroidX annotations (`androidx.annotation.RequiresApi` and `androidx.annotation.Nullable`) for API level checking and nullability documentation. Your Capacitor project must have AndroidX enabled.

To enable AndroidX in your Capacitor project, ensure your `android/gradle.properties` file contains:

```properties
android.useAndroidX=true
android.enableJetifier=true
```

If your project doesn't use AndroidX, you have two options:
1. **Enable AndroidX** (recommended): Add the properties above to `gradle.properties`
2. **Remove annotations**: The `@RequiresApi` and `@Nullable` annotations are optional and can be removed if you prefer not to use AndroidX

### iOS Share Extension Setup

When using this plugin with Capacitor, the iOS Share Extension must be manually set up because Capacitor doesn't support Cordova hook scripts that automatically configure the extension. Follow these steps:

#### 1. Copy Share Extension Files

Copy the Share Extension source files from the plugin to your iOS project:

```bash
# From your project root
cp -r node_modules/cordova-plugin-shared/src/ios/ShareExtension ios/App/ShareExt/
```

This will copy:
- `ShareViewController.h` - Header file
- `ShareViewController.m` - Implementation file
- `ShareExtension-Info.plist` - Extension configuration
- `ShareExtension.entitlements` - App group entitlements
- `MainInterface.storyboard` - UI storyboard

#### 2. Create Share Extension Target in Xcode

1. Open your iOS project in Xcode: `ios/App/App.xcworkspace`
2. Go to **File → New → Target**
3. Select **Share Extension** under **iOS → Application Extension**
4. Name it `ShareExt` (or match the name used in your project)
5. Set the language to **Objective-C**
6. Click **Finish**

#### 3. Replace Placeholders in Share Extension Files

The Share Extension files contain placeholders that must be replaced with your app's actual values. 

**Important: Understanding Bundle Identifiers and App Groups**

The plugin's hook scripts (which don't run in Capacitor) replace `__BUNDLE_IDENTIFIER__` consistently with `{MAIN_APP_BUNDLE_ID}.shareextension` in all files. This means:
- **Share Extension Bundle ID**: `{MAIN_APP_BUNDLE_ID}.shareextension` (e.g., `com.example.myapp.shareextension`)
- **App Group ID**: `group.{MAIN_APP_BUNDLE_ID}.shareextension` (e.g., `group.com.example.myapp.shareextension`)

**Important:** When creating the App Group in your Apple Developer account and Xcode, you must include `.shareextension` in the group name to match this pattern. 

**Example:** If your main app's bundle ID is `com.example.myapp`, then:
- Share Extension bundle ID: `com.example.myapp.shareextension`
- App Group ID: `group.com.example.myapp.shareextension`

**Placeholders to replace:**

Replace `__BUNDLE_IDENTIFIER__` with `{MAIN_APP_BUNDLE_ID}.shareextension` in **all files** (the replacement is consistent across all files):

**Files to update:**

1. **ShareExtension-Info.plist**:
   ```xml
   <!-- Replace these values: -->
   <key>CFBundleDisplayName</key>
   <string>__DISPLAY_NAME__</string>  <!-- e.g., "My App Share" -->
   
   <key>CFBundleIdentifier</key>
   <string>__BUNDLE_IDENTIFIER__</string>  <!-- Replace with: {MAIN_APP_BUNDLE_ID}.shareextension (e.g., "com.example.myapp.shareextension") -->
   
   <key>CFBundleShortVersionString</key>
   <string>__BUNDLE_SHORT_VERSION_STRING__</string>  <!-- e.g., "1.0.0" -->
   
   <key>CFBundleVersion</key>
   <string>__BUNDLE_VERSION__</string>  <!-- e.g., "1" -->
   ```

2. **ShareExtension.entitlements**:
   ```xml
   <key>com.apple.security.application-groups</key>
   <array>
       <string>group.__BUNDLE_IDENTIFIER__</string>  <!-- Replace __BUNDLE_IDENTIFIER__ with {MAIN_APP_BUNDLE_ID}.shareextension (e.g., "group.com.example.myapp.shareextension") -->
   </array>
   ```

3. **ShareViewController.h**:
   ```objc
   #define SHAREEXT_GROUP_IDENTIFIER @"group.__BUNDLE_IDENTIFIER__"  // Replace __BUNDLE_IDENTIFIER__ with {MAIN_APP_BUNDLE_ID}.shareextension (e.g., @"group.com.example.myapp.shareextension")
   #define SHAREEXT_URL_SCHEME @"__URL_SCHEME__"  // e.g., @"myapp"
   ```

**Example:** If your main app's bundle ID is `com.example.myapp`, then:
- Replace `__BUNDLE_IDENTIFIER__` with `com.example.myapp.shareextension` in all files
- ShareExtension-Info.plist `CFBundleIdentifier`: `com.example.myapp.shareextension`
- ShareExtension.entitlements app group: `group.com.example.myapp.shareextension`
- ShareViewController.h app group: `group.com.example.myapp.shareextension`
- **App Group to create in Apple Developer/Xcode**: `group.com.example.myapp.shareextension`

**Summary:**
- Replace `__BUNDLE_IDENTIFIER__` with `{MAIN_APP_BUNDLE_ID}.shareextension` in **all files** (consistent replacement)
- Create the App Group with `.shareextension` in the name: `group.{MAIN_APP_BUNDLE_ID}.shareextension`
- Both your main app and the Share Extension must be added to the same App Group in your Apple Developer account
- The URL scheme should match what you configure in your main app's `Info.plist`

#### 4. Configure App Groups

1. In Xcode, select your **main app target**
2. Go to **Signing & Capabilities**
3. Click **+ Capability** and add **App Groups**
4. Add a group: `group.{YOUR_MAIN_APP_BUNDLE_ID}.shareextension` (e.g., `group.com.example.myapp.shareextension`)
5. Repeat for the **ShareExt target** with the same group identifier: `group.{YOUR_MAIN_APP_BUNDLE_ID}.shareextension`

**Important:** The App Group name must include `.shareextension` to match the pattern used in the Share Extension files.

#### 5. Update AppDelegate.swift

Ensure your `AppDelegate.swift` handles the share extension URL scheme. The plugin expects the app to open with a custom URL scheme (e.g., `myapp://shared`) when content is shared.

Example implementation in `AppDelegate.swift`:

```swift
func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
    // Handle share extension URL
    if url.scheme == "your-url-scheme" && url.host == "shared" {
        // Read shared data from NSUserDefaults (app group)
        // Process and dispatch to JavaScript
    }
    return true
}
```

#### 6. Sync Capacitor

After making these changes, sync your Capacitor project:

```bash
npx cap sync ios
```

### Troubleshooting

- **Build errors about missing files**: Ensure all Share Extension files are added to the ShareExt target in Xcode
- **App Group not working**: Verify both app and extension are in the same App Group in Xcode and Apple Developer portal
- **URL scheme not opening app**: Check that the URL scheme is registered in your main app's `Info.plist` and matches `SHAREEXT_URL_SCHEME` in `ShareViewController.h`

## Usage

```js
document.addEventListener('deviceready', setupOpenwith, false);

function setupOpenwith() {

  // Increase verbosity if you need more logs
  //cordova.openwith.setVerbosity(cordova.openwith.DEBUG);

  // Initialize the plugin
  cordova.openwith.init(initSuccess, initError);

  function initSuccess()  { console.log('init success!'); }
  function initError(err) { console.log('init failed: ' + err); }

  // Define your file handler
  cordova.openwith.addHandler(myHandler);

  function myHandler(intent) {
    console.log('intent received');
    console.log('  text: ' + intent.text); // description to the sharing, for instance title of the page when shared URL from Safari
    for (var i = 0; i < intent.items.length; ++i) {
      var item = intent.items[i];
      console.log('  type: ', item.uti);    // UTI. possible values: public.url, public.text or public.image
      console.log('  type: ', item.type);   // Mime type. For example: "image/jpeg"
      console.log('  data: ', item.data);   // shared data. For URLs and text - actually the shared URL or text. For image - its base64 string representation.
      console.log('  text: ', item.text);   // text to share alongside the item. as we don't allow user to enter text in native UI, in most cases this will be empty. However for sharing pages from Safari this might contain the title of the shared page.
      console.log('  name: ', item.name);   // suggested name of the image. For instance: "IMG_0404.JPG"
      console.log('  utis: ', item.utis);   // some optional additional info
    }
    // ...
    // Here, you probably want to do something useful with the data
    // ...
  }
}
```

## API

### cordova.openwith.setVerbosity(level)

Change the verbosity level of the plugin.

`level` can be set to:

 - `cordova.openwith.DEBUG` for maximal verbosity, log everything.
 - `cordova.openwith.INFO` for the default verbosity, log interesting stuff only.
 - `cordova.openwith.WARN` for low verbosity, log only warnings and errors.
 - `cordova.openwith.ERROR` for minimal verbosity, log only errors.

### cordova.openwith.addHandler(handlerFunction)

Add an handler function, that will get notified when a file is received.

**Handler function**

The signature for the handler function is `function handlerFunction(intent)`. See below for what an intent is.

**Intent**

`intent` describe the operation to perform, toghether with the associated data. It has the following fields:

 - `text`: text to share alongside the item, in most cases this will be an empty string.
 - `items`: an array containing one or more data descriptor.

**Data descriptor**

A data descriptor describe one file. It is a javascript object with the following fields:

 - `uti`: Unique Type Identifier. possible values: public.url, public.text or public.image
 - `type`: Mime type. For example: "image/jpeg"
 - `text`: test description of the share, generally empty
 - `name`: suggested file name
 - `utis`: list of UTIs the file belongs to.

### cordova.openwith.load(dataDescriptor, loadSuccessCallback, loadErrorCallback)

Load data for an item. For this modification, it is not necessary,

### cordova.openwith.exit()

Attempt to return the the calling app when sharing is done. Your app will be backgrounded,
it should be able to finish the upload.

Unfortnately, this is not working on iOS. The user can still select the
"Back-to-app" button visible on the top left. Make sure your UI shows the user
that he can now safely go back to what he was doing.

## Contribute

Contributions in the form of GitHub pull requests are welcome. Please adhere to the following guidelines:
  - Before embarking on a significant change, please create an issue to discuss the proposed change and ensure that it is likely to be merged.
  - Follow the coding conventions used throughout the project. Many conventions are enforced using eslint and pmd. Run `npm t` to make sure of that.
  - Any contributions must be licensed under the MIT license.

## License

[MIT](./LICENSE)
