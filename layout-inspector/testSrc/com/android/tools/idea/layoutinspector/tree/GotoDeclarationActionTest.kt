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
package com.android.tools.idea.layoutinspector.tree

import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.FileOpenCaptureRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class GotoDeclarationActionTest {

  private val projectRule = AndroidProjectRule.withSdk()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule).around(EdtRule())!!

  @Before
  fun setup() {
    loadComposeFiles()
  }

  @RunsInEdt
  @Test
  fun testViewNode() {
    val model = createModel()
    model.setSelection(model["title"], SelectionOrigin.INTERNAL)
    val stats = SessionStatistics(model, FakeTreeSettings())
    val event = createEvent(model, stats)
    GotoDeclarationAction.actionPerformed(event)
    fileOpenCaptureRule.checkEditor("demo.xml", 9, "<TextView")
    checkStats(stats, clickCount = 1)
  }

  @RunsInEdt
  @Test
  fun testComposeViewNode() {
    val model = createModel()
    model.setSelection(model[-2], SelectionOrigin.INTERNAL)
    val stats = SessionStatistics(model, FakeTreeSettings())
    val event = createEvent(model, stats, fromShortcut = true)
    GotoDeclarationAction.actionPerformed(event)
    fileOpenCaptureRule.checkEditor("MyCompose.kt", 17,
                                    "Column(modifier = Modifier.padding(20.dp).clickable(onClick = { selectColumn() }),")
    checkStats(stats, keyStrokeCount = 1)
  }

  @RunsInEdt
  @Test
  fun testComposeViewNodeInOtherFileWithSameName() {
    val model = createModel()
    model.setSelection(model[-5], SelectionOrigin.INTERNAL)
    val stats = SessionStatistics(model, FakeTreeSettings())
    val event = createEvent(model, stats)
    GotoDeclarationAction.actionPerformed(event)
    fileOpenCaptureRule.checkEditor("MyCompose.kt", 8, "Text(text = \"Hello \$name!\")")
    checkStats(stats, clickCount = 1)
  }

  private fun loadComposeFiles() {
    val fixture = projectRule.fixture
    fixture.testDataPath = resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
    fixture.copyFileToProject("java/com/example/composable/MyCompose.kt")
  }

  private fun createModel(): InspectorModel =
    model(projectRule.project, FakeTreeSettings(), DemoExample.setUpDemo(projectRule.fixture) {
      view(0, qualifiedName = "androidx.ui.core.AndroidComposeView") {
        compose(-2, "Column", "MyCompose.kt", 49835523, 532, 17) {
          compose(-3, "Text", "MyCompose.kt", 49835523, 585, 18)
          compose(-4, "Greeting", "MyCompose.kt", 49835523, 614, 19) {
            compose(-5, "Text", "MyCompose.kt", 1216697758, 156, 3)
          }
        }
      }
    })

  private fun createEvent(model: InspectorModel, stats: SessionStatistics, fromShortcut: Boolean = false): AnActionEvent {
    val inspector: LayoutInspector = mock()
    `when`(inspector.layoutInspectorModel).thenReturn(model)
    `when`(inspector.stats).thenReturn(stats)
    val dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return if (key == LAYOUT_INSPECTOR_DATA_KEY) inspector as T else null
      }
    }
    val actionManager:ActionManager = mock()
    val inputEvent = if (fromShortcut) mock<KeyEvent>() else mock<InputEvent>()
    return AnActionEvent(inputEvent, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }

  private fun checkStats(stats: SessionStatistics, clickCount: Int = 0, keyStrokeCount: Int = 0) {
    val data = DynamicLayoutInspectorSession.newBuilder()
    stats.save(data)
    assertThat(data.gotoDeclaration.clicksMenuAction).isEqualTo(clickCount)
    assertThat(data.gotoDeclaration.keyStrokesShortcut).isEqualTo(keyStrokeCount)
  }
}
