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
package com.android.tools.idea.emulator.actions

import com.android.tools.idea.emulator.actions.dialogs.ManageSnapshotsDialog
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project

/**
 * Opens the manage Snapshots dialog.
 */
class EmulatorManageSnapshotsAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getRequiredData(CommonDataKeys.PROJECT)
    val emulatorController = getEmulatorController(event) ?: return
    val dialog = ManageSnapshotsDialog(emulatorController, getEmulatorView(event))
    dialog.createWrapper(project).show()
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isVisible = StudioFlags.EMBEDDED_EMULATOR_NEW_SNAPSHOT_UI.get()
    event.presentation.isEnabled = getEmulatorController(event) != null
  }
}