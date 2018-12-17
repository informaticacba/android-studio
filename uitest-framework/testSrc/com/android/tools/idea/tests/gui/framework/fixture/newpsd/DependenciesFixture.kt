/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.find
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.fixture.TextInputFixture
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.awt.Container
import javax.swing.JButton

class DependenciesFixture(
  override val ideFrameFixture: IdeFrameFixture,
  override val container: Container
) : ConfigPanelFixture() {

  fun findDependenciesTable(): JTableFixture =
    JTableFixture(robot(), robot().finder().findByType<TableView<*>>(container))

  fun clickAddLibraryDependency(): AddLibraryDependencyDialogFixture {
    clickToolButton("Add Dependency")
    val listFixture = JListFixture(robot(), getList())
    listFixture.clickItem(0 /* 1 Library Dependency */)  // Search by title does not work here.
    return AddLibraryDependencyDialogFixture.find(ideFrameFixture, "Add Library Dependency")
  }

  fun clickAddModuleDependency(): AddModuleDependencyDialogFixture {
    clickToolButton("Add Dependency")
    val listFixture = JListFixture(robot(), getList())
    listFixture.clickItem(2 /* 3 Module Dependency */)  // Search by title does not work here.
    return AddModuleDependencyDialogFixture.find(ideFrameFixture, "Add Module Dependency")
  }
}