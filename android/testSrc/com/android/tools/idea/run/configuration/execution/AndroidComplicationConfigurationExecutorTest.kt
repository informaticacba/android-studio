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


import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt.any
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.AndroidWearProgramRunner
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.times


class AndroidComplicationConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  private object TestWatchFaceInfo : ComplicationWatchFaceInfo {
    override val complicationSlots: List<ComplicationSlot> = emptyList()
    override val apk: String = "/path/to/watchface.apk"
    override val appId: String = "com.example.watchface"
    override val watchFaceFQName: String = "com.example.watchface.MyWatchFace"
  }

  fun test() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidComplicationConfigurationType().configurationFactories.single())
    val androidComplicationConfiguration = configSettings.configuration as AndroidComplicationConfiguration
    androidComplicationConfiguration.watchFaceInfo = TestWatchFaceInfo
    androidComplicationConfiguration.setModule(myModule)
    androidComplicationConfiguration.componentName = componentName
    androidComplicationConfiguration.chosenSlots = listOf(
      AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
      AndroidComplicationConfiguration.ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE)
    )
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidWearProgramRunner(), configSettings, project)

    val device = getMockDevice()
    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val executor = Mockito.spy(AndroidComplicationConfigurationExecutor(env))
    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).`when`(executor).getApplicationInstaller()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, times(4)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues
    // ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT).
    assertThat(commands[0]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                      " --ecn component 'com.example.app/com.example.app.Component'" +
                                      " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                      " --ei slot 1 --ei type 3")
    // ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE).
    assertThat(commands[1]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                      " --ecn component 'com.example.app/com.example.app.Component'" +
                                      " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                      " --ei slot 3 --ei type 5")
    // Set watch face.
    assertThat(commands[2]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE" +
                                      " --es operation set-watchface" +
                                      " --ecn component com.example.watchface/com.example.watchface.MyWatchFace")
    // Show watch face.
    assertThat(commands[3]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface")
  }

  fun testDebug() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidComplicationConfigurationType().configurationFactories.single())
    val androidComplicationConfiguration = configSettings.configuration as AndroidComplicationConfiguration
    androidComplicationConfiguration.watchFaceInfo = TestWatchFaceInfo
    androidComplicationConfiguration.setModule(myModule)
    androidComplicationConfiguration.componentName = componentName
    androidComplicationConfiguration.chosenSlots = listOf(
      AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
      AndroidComplicationConfiguration.ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE)
    )
    // Use DefaultDebugExecutor executor.
    val env = ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), AndroidWearProgramRunner(), configSettings, project)

    val device = getMockDevice()
    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val executor = Mockito.spy(AndroidComplicationConfigurationExecutor(env))
    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).`when`(executor).getApplicationInstaller()
    doReturn(Mockito.mock(DebugSessionStarter::class.java)).`when`(executor).getDebugSessionStarter()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, times(6)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Set debug app.
    assertThat(commands[0]).isEqualTo("am set-debug-app -w 'com.example.app'")
    // ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT).
    assertThat(commands[1]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                      " --ecn component 'com.example.app/com.example.app.Component'" +
                                      " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                      " --ei slot 1 --ei type 3")
    // Set debug app.
    assertThat(commands[2]).isEqualTo("am set-debug-app -w 'com.example.app'")
    // ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE).
    assertThat(commands[3]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                      " --ecn component 'com.example.app/com.example.app.Component'" +
                                      " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                      " --ei slot 3 --ei type 5")
    // Set watch face.
    assertThat(commands[4]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE" +
                                      " --es operation set-watchface" +
                                      " --ecn component com.example.watchface/com.example.watchface.MyWatchFace")
    // Show watch face.
    assertThat(commands[5]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface")
  }

  fun testComplicationProcessHandler() {
    val processHandler = ComplicationProcessHandler(AppComponent.getFQEscapedName(appId, componentName),
                                                    Mockito.mock(ConsoleView::class.java))
    val device = getMockDevice()
    processHandler.addDevice(device)

    processHandler.startNotify()

    processHandler.destroyProcess()

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, times(2)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Unset complication
    assertThat(commands[0]).isEqualTo(
      "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-complication --ecn component com.example.app/com.example.app.Component")
    // Unset debug watchFace
    assertThat(commands[1]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface")
  }
}
