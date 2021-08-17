android {
  packagingOptions {
    doNotStrip("doNotStrip1")
    doNotStrip("doNotStrip2")
    doNotStrip("doNotStripX")
    exclude("excludeX")
    exclude("exclude2")
    exclude("exclude3")
    merges = mutableSetOf("merge1", "mergeX")
    merge("merge3")
    pickFirsts = mutableSetOf("pickFirst1", "pickFirst2")
    pickFirst("pickFirstX")
  }
}
