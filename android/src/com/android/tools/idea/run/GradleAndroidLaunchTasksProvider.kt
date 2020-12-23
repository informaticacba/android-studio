/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.editor.AndroidDebuggerState
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.DebugConnectorTask
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.methodTest
import com.android.tools.idea.testartifacts.instrumented.GradleConnectedAndroidTestInvoker
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/**
 * LaunchTasksProvider that provides GradleAndroidTestApplicationLaunchTasks for instrumentation tests
 */
class GradleAndroidLaunchTasksProvider(private val myRunConfig: AndroidRunConfigurationBase,
                                       private val myEnv: ExecutionEnvironment,
                                       facet: AndroidFacet,
                                       applicationIdProvider: ApplicationIdProvider,
                                       launchOptions: LaunchOptions,
                                       testingType: Int,
                                       packageName: String,
                                       className: String,
                                       methodName: String) : LaunchTasksProvider {
  private val myFacet: AndroidFacet = facet
  private val myApplicationIdProvider: ApplicationIdProvider = applicationIdProvider
  private val myLaunchOptions: LaunchOptions = launchOptions
  private val myProject: Project = facet.module.project
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker = GradleConnectedAndroidTestInvoker(myRunConfig.getNumberOfSelectedDevices(facet))
  private val TESTINGTYPE: Int = testingType
  private val PACKAGENAME: String = packageName
  private val CLASSNAME: String = className
  private val METHODNAME: String = methodName

  @NotNull
  override fun getTasks(@NotNull device: IDevice, @NotNull launchStatus: LaunchStatus, @NotNull consolePrinter: ConsolePrinter): List<LaunchTask> {
    val launchTasks: MutableList<LaunchTask> = Lists.newArrayList()
    val testAppId: String?
    try {
      testAppId = myApplicationIdProvider.testPackageName
      if (testAppId == null) {
        launchStatus.terminateLaunch("Unable to determine test package name", true)
        return launchTasks
      }
    }
    catch (e: ApkProvisionException) {
      launchStatus.terminateLaunch("Unable to determine test package name", true)
      return launchTasks
    }
    val appLaunchTask: AppLaunchTask?
    when (TESTINGTYPE) {
      TEST_ALL_IN_MODULE -> {
        appLaunchTask = allInModuleTest(myProject,
                        testAppId,
                        myLaunchOptions.isDebug,
                        consolePrinter,
                        device,
                        myGradleConnectedAndroidTestInvoker)
      }
      TEST_ALL_IN_PACKAGE -> {
        appLaunchTask = allInPackageTest(myProject,
                         testAppId,
                         myLaunchOptions.isDebug,
                         consolePrinter,
                         device,
                         PACKAGENAME,
                         myGradleConnectedAndroidTestInvoker)
      }
      TEST_CLASS -> {
        appLaunchTask = classTest(myProject,
                  testAppId,
                  myLaunchOptions.isDebug,
                  consolePrinter,
                  device,
                  CLASSNAME,
                  myGradleConnectedAndroidTestInvoker)
      }
     TEST_METHOD -> {
       appLaunchTask = methodTest(myProject,
                  testAppId,
                  myLaunchOptions.isDebug,
                  consolePrinter,
                  device,
                  CLASSNAME,
                  METHODNAME,
                  myGradleConnectedAndroidTestInvoker)
     } else -> {
      launchStatus.terminateLaunch("Unknown testing type is selected, testing type is $TESTINGTYPE", true)
      appLaunchTask = null
     }
    }
    if (appLaunchTask != null) {
      launchTasks.add(appLaunchTask)
    }
    return launchTasks
  }

  @Nullable
  override fun getConnectDebuggerTask(@NotNull launchStatus: LaunchStatus, @Nullable version: AndroidVersion?): DebugConnectorTask? {
    if (!myLaunchOptions.isDebug) {
      return null
    }
    val logger = Logger.getInstance(AndroidLaunchTasksProvider::class.java)
    val packageIds: MutableSet<String> = Sets.newHashSet()
    var packageName: String? = null
    try {
      packageName = myApplicationIdProvider.packageName
      packageIds.add(packageName)
    }
    catch (e: ApkProvisionException) {
      logger.error(e)
    }
    try {
      val testPackageName = myApplicationIdProvider.testPackageName
      if (testPackageName != null) {
        packageIds.add(testPackageName)
      }
    }
    catch (e: ApkProvisionException) {
      // not as severe as failing to obtain package id for main application
      logger
        .warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application")
    }
    val androidDebuggerContext = myRunConfig.androidDebuggerContext
    val debugger = androidDebuggerContext.androidDebugger
    if (debugger == null) {
      logger.warn("Unable to determine debugger to use for this launch")
      return null
    }
    logger.info("Using debugger: " + debugger.id)
    val androidDebuggerState = androidDebuggerContext.getAndroidDebuggerState<AndroidDebuggerState>()
    return if (androidDebuggerState != null) {
      debugger.getConnectDebuggerTask(myEnv,
                                      version,
                                      packageIds,
                                      myFacet,
                                      androidDebuggerState,
                                      myRunConfig.type.id,
                                      packageName)
    }
    else null
  }

  companion object {
    private const val TEST_ALL_IN_MODULE = 0
    private const val TEST_ALL_IN_PACKAGE = 1
    private const val TEST_CLASS = 2
    private const val TEST_METHOD = 3
  }
}