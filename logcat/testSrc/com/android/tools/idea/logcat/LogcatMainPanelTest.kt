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
package com.android.tools.idea.logcat

import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.PopupRule
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.logcat.actions.ClearLogcatAction
import com.android.tools.idea.logcat.actions.HeaderFormatOptionsAction
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.StringFilter
import com.android.tools.idea.logcat.folding.FoldingDetector
import com.android.tools.idea.logcat.hyperlinks.HyperlinkDetector
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.android.tools.idea.logcat.util.isCaretAtBottom
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroup.EMPTY_GROUP
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.SimpleActionGroup
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.awt.BorderLayout
import java.awt.BorderLayout.CENTER
import java.awt.BorderLayout.NORTH
import java.awt.BorderLayout.WEST
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.JPopupMenu

/**
 * Tests for [LogcatMainPanel]
 */
class LogcatMainPanelTest {
  private val projectRule = ProjectRule()
  private val executor = Executors.newCachedThreadPool()
  private val popupRule = PopupRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = executor, ioThreadExecutor = executor)

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), androidExecutorsRule, popupRule, LogcatFilterLanguageRule())

  private val myMockHyperlinkDetector = mock<HyperlinkDetector>()
  private val mockFoldingDetector = mock<FoldingDetector>()
  private val gson = Gson()

  @RunsInEdt
  @Test
  fun createsComponents() {
    val logcatMainPanel = logcatMainPanel()

    val borderLayout = logcatMainPanel.layout as BorderLayout

    assertThat(logcatMainPanel.componentCount).isEqualTo(3)
    assertThat(borderLayout.getLayoutComponent(NORTH)).isInstanceOf(LogcatHeaderPanel::class.java)
    assertThat(borderLayout.getLayoutComponent(CENTER)).isSameAs(logcatMainPanel.editor.component)
    assertThat(borderLayout.getLayoutComponent(WEST)).isInstanceOf(ActionToolbar::class.java)
    val toolbar = borderLayout.getLayoutComponent(WEST) as ActionToolbar
    assertThat(toolbar.actions[0]).isInstanceOf(ClearLogcatAction::class.java)
    assertThat(toolbar.actions[1]).isInstanceOf(ScrollToTheEndToolbarAction::class.java)
    assertThat(toolbar.actions[2]).isInstanceOf(ToggleUseSoftWrapsToolbarAction::class.java)
    assertThat(toolbar.actions[3]).isInstanceOf(HeaderFormatOptionsAction::class.java)
    assertThat(toolbar.actions[4]).isInstanceOf(Separator::class.java)
  }

  @Test
  fun setsDocumentCyclicBuffer() = runBlocking {
    // Set a buffer of 1k
    System.setProperty("idea.cycle.buffer.size", "1")
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    val document = logcatMainPanel.editor.document as DocumentImpl

    // Insert 20 log lines
    logcatMainPanel.messageProcessor.appendMessages(List(20) { logCatMessage() })

    assertThat(document.text.length).isAtMost(1024)
  }

  /**
   * This test can't run in the EDT because it depends on coroutines that are launched in the UI Thread and need to be able to wait for them
   * to complete. If it runs in the EDT, it cannot wait for these tasks to execute.
   */
  @Test
  fun appendMessages() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(zoneId = ZoneId.of("Asia/Yerevan"))
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 I  message2

      """.trimIndent())
    }
  }

  @Test
  fun applyFilter() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)
    logcatMainPanel.processMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.applyFilter(StringFilter("tag1", LINE))
    }

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1

      """.trimIndent())
    }
  }

  @Test
  fun applyFilter_appOnly() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(packageNamesProvider = FakePackageNamesProvider("app1", "app3"))
    }
    logcatMainPanel.processMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag", Instant.ofEpochMilli(1000)), "message2"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app3", "tag", Instant.ofEpochMilli(1000)), "message3"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.setShowOnlyProjectApps(true)
    }

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag                     app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag                     app3                                 I  message3

      """.trimIndent())
    }
  }

  @Test
  fun appendMessages_disposedEditor() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        Disposer.dispose(it)
      }
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
  }

  @Test
  fun appendMessages_scrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      logCatMessage(),
      logCatMessage(),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isTrue()
    }
  }

  @Test
  fun appendMessages_notAtBottom_doesNotScrollToEnd() = runBlocking {
    val logcatMainPanel = runInEdtAndGet(this@LogcatMainPanelTest::logcatMainPanel)

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.editor.caretModel.moveToOffset(0)
    }
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isFalse()
    }
  }

  @RunsInEdt
  @Test
  fun installPopupHandler() {
    val popupActionGroup = SimpleActionGroup().apply {
      add(object : AnAction("An Action") {
        override fun actionPerformed(e: AnActionEvent) {}
      })
    }

    val logcatMainPanel = logcatMainPanel(popupActionGroup = popupActionGroup).apply {
      size = Dimension(100, 100)
    }
    val fakeUi = FakeUi(logcatMainPanel, createFakeWindow = true)

    fakeUi.rightClickOn(logcatMainPanel)

    val popupMenu = popupRule.popupContents as JPopupMenu
    assertThat(popupMenu.components.map { (it as ActionMenuItem).anAction }).containsExactlyElementsIn(popupActionGroup.getChildren(null))
    verify(popupRule.mockPopup).show()
  }

  @RunsInEdt
  @Test
  fun isMessageViewEmpty_emptyDocument() {
    val logcatMainPanel = logcatMainPanel()
    logcatMainPanel.editor.document.setText("")

    assertThat(logcatMainPanel.isMessageViewEmpty()).isTrue()
  }

  @RunsInEdt
  @Test
  fun isMessageViewEmpty_notEmptyDocument() {
    val logcatMainPanel = logcatMainPanel()
    logcatMainPanel.editor.document.setText("not-empty")

    assertThat(logcatMainPanel.isMessageViewEmpty()).isFalse()
  }

  @Test
  fun clearMessageView() {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        it.editor.document.setText("not-empty")
      }
    }

    logcatMainPanel.clearMessageView()

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().ioThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEmpty()
    assertThat(logcatMainPanel.messageBacklog.messages).isEmpty()
    // TODO(aalbert): Test the 'logcat -c' functionality if new adb lib allows for it.
  }

  /**
   *  The purpose this test is to ensure that we are calling the HyperlinkHighlighter with the correct line range. It does not test user on
   *  any visible effect.
   */
  @Test
  fun hyperlinks_range() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(hyperlinkDetector = myMockHyperlinkDetector)
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      verify(myMockHyperlinkDetector).detectHyperlinks(eq(0), eq(1))
      verify(myMockHyperlinkDetector).detectHyperlinks(eq(1), eq(2))
    }
  }

  /**
   *  The purpose this test is to ensure that we are calling the HyperlinkHighlighter with the correct line range. It does not test user on
   *  any visible effect.
   */
  @Test
  fun hyperlinks_rangeWithCyclicBuffer() = runBlocking {
    System.setProperty("idea.cycle.buffer.size", "1")
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(hyperlinkDetector = myMockHyperlinkDetector)
    }
    val longMessage = "message".padStart(1000, '-')

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))

    logcatMainPanel.messageProcessor.onIdle {
      verify(myMockHyperlinkDetector, times(2)).detectHyperlinks(eq(0), eq(1))
    }
  }

  /**
   *  The purpose this test is to ensure that we are calling the FoldingDetector with the correct line range. It does not test user on any
   *  visible effect.
   */
  @Test
  fun foldings_range() = runBlocking {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(foldingDetector = mockFoldingDetector)
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {}
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockFoldingDetector).detectFoldings(eq(0), eq(1))
      verify(mockFoldingDetector).detectFoldings(eq(1), eq(2))
    }
  }

  /**
   *  The purpose this test is to ensure that we are calling the FoldingDetector with the correct line range. It does not test user on any
   *  visible effect.
   */
  @Test
  fun foldings_rangeWithCyclicBuffer() = runBlocking {
    System.setProperty("idea.cycle.buffer.size", "1")
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel(foldingDetector = mockFoldingDetector)
    }
    val longMessage = "message".padStart(1000, '-')

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))
    logcatMainPanel.messageProcessor.onIdle {} // force flush
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage(message = longMessage)))

    logcatMainPanel.messageProcessor.onIdle {
      verify(mockFoldingDetector, times(2)).detectFoldings(eq(0), eq(1))
    }
  }

  @RunsInEdt
  @Test
  fun getState() {
    val logcatMainPanel = logcatMainPanel()
    logcatMainPanel.formattingOptions.tagFormat = TagFormat(15)

    val logcatPanelConfig = gson.fromJson(logcatMainPanel.getState(), LogcatPanelConfig::class.java)
    assertThat(logcatPanelConfig.formattingOptions.tagFormat.maxLength).isEqualTo(15)
  }

  @RunsInEdt
  @Test
  fun appliesState() {
    val logcatMainPanel = logcatMainPanel(
      state = LogcatPanelConfig("device", FormattingOptions(tagFormat = TagFormat(17)), "filter", showOnlyProjectApps = true))

    // TODO(aalbert) : Also assert on device field when the combo is rewritten to allow initializing it.
    assertThat(logcatMainPanel.formattingOptions.tagFormat.maxLength).isEqualTo(17)
    assertThat(logcatMainPanel.messageProcessor.logcatFilter).isEqualTo(StringFilter("filter", LINE))
    assertThat(logcatMainPanel.messageProcessor.showOnlyProjectApps).isTrue()
    assertThat(logcatMainPanel.headerPanel.getFilterText()).isEqualTo("filter")
    assertThat(logcatMainPanel.headerPanel.isShowProjectApps()).isTrue()
  }

  @Test
  fun reloadMessages() {
    val logcatMainPanel = runInEdtAndGet {
      logcatMainPanel().also {
        it.editor.document.setText("Some previous text")
      }
    }
    logcatMainPanel.messageBacklog.addAll(listOf(logCatMessage(message = "message")))

    runInEdtAndWait(logcatMainPanel::reloadMessages)

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().workerThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text)
        .isEqualTo("1970-01-01 04:00:00.000     1-2     ExampleTag              com.example.app                      I  message\n")

    }
  }

  private fun logcatMainPanel(
    popupActionGroup: ActionGroup = EMPTY_GROUP,
    logcatColors: LogcatColors = LogcatColors(),
    state: LogcatPanelConfig? = null,
    hyperlinkDetector: HyperlinkDetector? = null,
    foldingDetector: FoldingDetector? = null,
    packageNamesProvider: PackageNamesProvider = FakePackageNamesProvider(),
    zoneId: ZoneId = ZoneId.of("Asia/Yerevan"),
  ): LogcatMainPanel =
    LogcatMainPanel(
      projectRule.project,
      popupActionGroup,
      logcatColors,
      state,
      hyperlinkDetector,
      foldingDetector,
      packageNamesProvider,
      zoneId
    ).also {
      Disposer.register(projectRule.project, it)
    }
}
