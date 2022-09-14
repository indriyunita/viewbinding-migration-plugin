# hh-histories-view-binding-migration-plugin

<!-- Plugin description -->
**HH Synthetic** -- plugin for automated migration from Kotlin synthetics to View Binding.
<!-- Plugin description end -->

![Plugin usage](/docs/assets/Plugin_usage.gif)

## Modifications
This is a modified plugin from https://github.com/hhru/hh-histories-view-binding-migration-plugin.
Modifications involved:
- Incorporating another library https://github.com/yogacp/android-viewbinding for ViewBinding delegate 
- Accommodating <include/> 

## Setup for local development

- Create `local.properties` file in root folder with the following content:

```properties
# Properties for launching Android Studio
androidStudioPath=/Applications/Android Studio.app
androidStudioCompilerVersion=212.5712.43
```

Here:

- `androidStudioPath` - Path to your local Android Studio;
- `androidStudioCompilerVersion` - this version you could get from `About` screen of Android Studio (take only MAJOR.MINOR.PATCH) version

## Running/Testing the Plugin
<img src="https://user-images.githubusercontent.com/25334720/190074841-020d3d8d-5f08-4cc2-81ae-79ea67727d21.png" width="400"/>
Click "Run Plugin" on Gradle run toolbar menu. A new instance of Android Studio will appear with the plugin installed. Open a project and test the plugin on it.


## Building and Using the Plugin
<img src="https://user-images.githubusercontent.com/25334720/190076617-bce263a1-2f9e-438d-823c-b75981827139.png" width="300"/>
You can build the jar from Gradle tasks. Then you can find the jar in build/libs folder

<img src="https://user-images.githubusercontent.com/25334720/190077949-84ea576b-12b9-47c7-b943-462f29aa3f1d.png" width="400"/>

Then install the plugin from disk.

<img src="https://user-images.githubusercontent.com/25334720/190078152-905800c9-4d2b-4991-ab98-121394a5cfa8.png" width="400"/>

This plugin has been tested on Android Studio Chipmunk | 2021.2.1 Patch 1

## Steps to Convert a Module from Synthetic to ViewBinding (Order Matters)
1. Add dependency to android-viewbinding delegate lib(https://github.com/yogacp/android-viewbinding)

Add this to your build.gradle:
```
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

And in module's build,gradle:
```dependencies {
    ....
    implementation 'com.github.yogacp:android-viewbinding:x.x.x'
}
```

2. Convert ALL Activities and Fragments using the plugin (as of now, you can only apply one by one).

3. Change module dependency from featuremodule.gradle to featuremoduleviewbing.gradle (suppose this file exists). The difference is this one removes `apply plugin: 'kotlin-android-extensions'` and adding `apply plugin: 'org.jetbrains.kotlin.android'` and `apply plugin: 'kotlin-parcelize'`.

![image](https://user-images.githubusercontent.com/25334720/190081932-410b21e6-14a5-493b-b9ee-2760757a4abe.png)


