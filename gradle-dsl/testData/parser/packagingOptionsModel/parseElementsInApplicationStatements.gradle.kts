android {
  packagingOptions {
    doNotStrip("doNotStrip1")
    doNotStrip("doNotStrip2")
    doNotStrip("doNotStrip3")
    exclude("exclude1")
    exclude("exclude2")
    exclude("exclude3")
    merge("merge1")
    merge("merge2")
    merge("merge3")
    pickFirst("pickFirst1")
    pickFirst("pickFirst2")
    pickFirst("pickFirst3")
  }
}
