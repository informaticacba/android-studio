load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.debuggers",
    srcs = ["src"],
    iml_files = ["intellij.android.debuggers.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/debuggers:libjava_sites",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.debuggers.tests",
    iml_files = ["intellij.android.debuggers.tests.iml"],
    test_class = "com.android.tools.idea.debuggers.DebuggersTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/debuggers:intellij.android.debuggers[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "libjava_sites",
    jars = ["//tools/base/deploy/sites:libjava_sites.jar"],
    visibility = ["//visibility:public"],
)
