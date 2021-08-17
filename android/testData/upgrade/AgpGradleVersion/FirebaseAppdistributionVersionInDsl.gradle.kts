buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.5.0")
    }
}

plugins {
  id("com.google.firebase.appdistribution") version "1.1.0" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
