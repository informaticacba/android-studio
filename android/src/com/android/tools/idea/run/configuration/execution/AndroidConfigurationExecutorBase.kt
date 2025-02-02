/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.LaunchableAndroidDevice
import com.android.tools.idea.run.configuration.ComponentSpecificConfiguration
import com.android.tools.idea.run.configuration.isDebug
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.wearpairing.await
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

abstract class AndroidConfigurationExecutorBase(protected val environment: ExecutionEnvironment) : RunProfileState {
  abstract val configuration: ComponentSpecificConfiguration
  protected val project = environment.project
  protected val appId
    get() = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName
            ?: throw RuntimeException("Cannot get ApplicationIdProvider")

  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }

  @WorkerThread
  fun execute(stats: RunStats): RunContentDescriptor? {
    val facet = AndroidFacet.getInstance(configuration.module!!)!!
    stats.setDebuggable(LaunchUtils.canDebugApp(facet))
    stats.setExecutor(environment.executor.id)
    stats.setPackage(appId)
    stats.setAppComponentType(configuration.componentType)

    ProgressManager.checkCanceled()
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.text = "Waiting for all target devices to come online"
    val devices = getDevices(stats)
    devices.forEach { LaunchUtils.initiateDismissKeyguard(it) }
    stats.beginLaunchTasks()
    val runContentDescriptor = doOnDevices(devices)
    stats.endBeforeRunTasks()
    return runContentDescriptor
  }

  @VisibleForTesting
  abstract fun doOnDevices(devices: List<IDevice>): RunContentDescriptor?

  private fun getDevices(stats: RunStats): List<IDevice> {
    val facet = AndroidFacet.getInstance(configuration.module!!)!!
    val devices = runBlocking {
      val provider = DeviceAndSnapshotComboBoxTargetProvider()
      val deployTarget = if (provider.requiresRuntimePrompt(project)) invokeAndWaitIfNeeded {
        provider.showPrompt(facet)
      }
      else provider.getDeployTarget(project)
      val deviceFutureList = deployTarget?.getDevices(facet) ?: return@runBlocking emptyList()

      // Record stat if we launched a device.
      stats.setLaunchedDevices(deviceFutureList.devices.any { it is LaunchableAndroidDevice })
      return@runBlocking deviceFutureList.get().map {
        stats.beginWaitForDevice()
        val device = it.await()
        stats.endWaitForDevice(device)
        device
      }
    }
    if (devices.isEmpty()) {
      throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))
    }
    return devices
  }

  fun getApplicationInstaller(): ApplicationInstaller {
    return ApplicationInstallerImpl(project)
  }

  @VisibleForTesting
  fun getDebugSessionStarter(): DebugSessionStarter {
    return DebugSessionStarter(environment)
  }

  protected fun getApkPaths(device: IDevice): List<String> {
    val apkProvider = project.getProjectSystem().getApkProvider(configuration) ?: throw ExecutionException(
      AndroidBundle.message("android.run.configuration.not.supported",
                            configuration.name)) // There is no test ApkInfo for AndroidWatchFaceConfiguration, thus it should be always single ApkInfo. Only App.
    return apkProvider.getApks(device).single().files.map { it.apkFile.path }
  }

  private fun startDebugger(device: IDevice, processHandler: AndroidProcessHandlerForDevices, console: ConsoleView): RunContentDescriptor {
    return getDebugSessionStarter().attachDebuggerToClient(device, processHandler, console)
  }

  protected fun createRunContentDescriptor(
    devices: List<IDevice>,
    processHandler: AndroidProcessHandlerForDevices,
    console: ConsoleView
  ): RunContentDescriptor? {
    return if (environment.executor.isDebug) {
      processHandler.startNotify()
      startDebugger(devices.single(), processHandler, console)
    }
    else {
      invokeAndWaitIfNeeded { showRunContent(DefaultExecutionResult(console, processHandler), environment) }
    }
  }

  open class AndroidLaunchReceiver(private val isCancelledCheck: () -> Boolean,
                                   private val consoleView: ConsoleView) : MultiLineReceiver() {
    override fun isCancelled() = isCancelledCheck()

    override fun processNewLines(lines: Array<String>) = lines.forEach {
      consoleView.print(it + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }
  }
}
