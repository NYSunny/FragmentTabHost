# FragmentTabHost

自定义FragmentTabHost，改变了内部Fragment切换时的生命周期

## 添加FragmentTabHost依赖

项目build.gradle：
```
allprojects {
    repositories {
        google()
        jcenter()
        maven { url 'https://jitpack.io' }
    }
}
```

依赖module下的build.gradle：
```
dependencies {
    // ...
    implementation 'com.github.NYSunny:FragmentTabHost:v1.0-alpha'
}
```