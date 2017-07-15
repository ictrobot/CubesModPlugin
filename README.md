CubesModPlugin
=============

cubesVersion, modVersion, modClass & modName *must* be set.

```
cubes {
    cubesVersion = '0.0.5-SNAPSHOT'
    modVersion = '1.0'
    modClass = 'ethanjones.test.Test'
    modName = 'Test'
    
    # optional (defaults below)
    assetsFolder = 'assets/'
    jsonFolder = 'json/'
    androidSDKDir = System.getenv("ANDROID_HOME")
    buildAndroid = true
    forceAndroid = false
    buildDesktop = true
}
```

Cubes 0.0.5 & higher automatically compile jars to dex files on android and therefore if your project is targeting 0.0.5 or higher, an android dex will not be built unless forceAndroid is set to true.

## Sample build.gradle
```
buildscript {
    repositories {
        maven { url "http://ethanjones.me/maven/snapshots" }
        maven { url "http://ethanjones.me/maven/releases" }
    }
    dependencies {
        classpath "ethanjones.cubes:modplugin:0.0.1"
    }
}
apply plugin: 'java'
apply plugin: 'cubes-mod'

cubes {
    cubesVersion = '0.0.4'
    modVersion = '1.0'
    modClass = 'sample.SampleMod'
    modName = 'SampleMod'
}
```