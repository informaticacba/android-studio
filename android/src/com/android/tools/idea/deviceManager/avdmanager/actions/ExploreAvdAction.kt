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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.tools.idea.deviceManager.DEVICE_MANAGER_ID
import com.android.tools.idea.deviceManager.avdmanager.AvdOptionsModel
import com.android.tools.idea.deviceManager.avdmanager.AvdWizardUtils
import com.android.tools.idea.explorer.DeviceExplorerToolWindowFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.event.ActionEvent

/**
 * Open the Device File Explorer tool window with a selected device
 */
class ExploreAvdAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "Explore Device Filesystem...", "Open Device File Explorer for the device", AllIcons.General.OpenDiskHover
) {
  override fun actionPerformed(e: ActionEvent?) {
    // TODO(qumeric): it should be possible to open it even when project = null
    val project = avdInfoProvider.project ?: return

    ToolWindowManager.getInstance(project).getToolWindow(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID)?.show(null)
  }

  override fun isEnabled(): Boolean = true
}