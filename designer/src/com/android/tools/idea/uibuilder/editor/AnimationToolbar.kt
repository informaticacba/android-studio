/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.adtui.stdui.CommonButton
import javax.swing.JPanel
import javax.swing.JButton
import javax.swing.JSlider
import javax.swing.DefaultBoundedRangeModel
import com.android.tools.idea.uibuilder.analytics.AnimationToolbarAnalyticsManager
import java.lang.Runnable
import java.awt.event.ActionEvent
import com.intellij.util.ui.JBUI
import com.intellij.util.concurrency.EdtExecutorService
import icons.StudioIcons
import java.awt.FlowLayout
import com.android.tools.adtui.ui.DesignSurfaceToolbarUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal const val DEFAULT_PLAY_TOOLTIP = "Play"
internal const val DEFAULT_PAUSE_TOOLTIP = "Pause"
internal const val DEFAULT_STOP_TOOLTIP = "Reset"
internal const val DEFAULT_FRAME_FORWARD_TOOLTIP = "Step forward"
internal const val DEFAULT_FRAME_BACK_TOOLTIP = "Step backward"

/**
 * Control that provides controls for animations (play, pause, stop and frame-by-frame steps).
 *
 * @param parentDisposable Parent [Disposable]
 * @param listener         [AnimationListener] that will be called in every tick
 * @param tickStepMs       Number of milliseconds to advance in every animator tick
 * @param minTimeMs        Start milliseconds for the animation
 * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
 */
open class AnimationToolbar protected constructor(parentDisposable: Disposable,
                                                  listener: AnimationListener,
                                                  tickStepMs: Long,
                                                  minTimeMs: Long,
                                                  initialMaxTimeMs: Long,
                                                  toolbarType: AnimationToolbarType)
  : JPanel(), Disposable {
  private val myListener: AnimationListener
  private val myPlayButton: JButton
  private val myPauseButton: JButton
  private val myStopButton: JButton
  private val myFrameFwdButton: JButton
  private val myFrameBckButton: JButton

  // TODO: Add speed selector button.
  private val myTickStepMs: Long
  private val myMinTimeMs: Long
  protected val controlBar: JPanel

  /**
   * Slider that allows stepping frame by frame at different speeds
   */
  private val myFrameControl: JSlider
  private var myTimeSliderModel: DefaultBoundedRangeModel? = null

  /**
   * The progress bar to indicate the current progress of animation. User can also click/drag the indicator to set the progress.
   */
  protected var myTimeSlider: JSlider? = null
  private var myTimeSliderChangeModel: ChangeListener? = null
  private var myMaxTimeMs: Long
  private var myLoopEnabled = true

  /**
   * Ticker to control "real-time" animations and the frame control animations (the slider that allows moving at different speeds)
   */
  private var myTicker: ScheduledFuture<*>? = null
  private var myFramePositionMs: Long = 0
  private var myLastTickMs = 0L
  val toolbarType: AnimationToolbarType
  protected val myAnalyticsManager = AnimationToolbarAnalyticsManager()

  /**
   * Creates a new toolbar control button
   */
  private fun newControlButton(baseIcon: Icon,
                               label: String,
                               tooltip: String?,
                               action: AnimationToolbarAction,
                               callback: Runnable)
  : JButton {
    val button: JButton = CommonButton()
    button.name = label
    button.icon = baseIcon
    button.addActionListener { e: ActionEvent? ->
      myAnalyticsManager.trackAction(toolbarType, action)
      // When action is performed, some buttons are disabled or become invisible, which may make the focus move to the next component in the
      // editor. We move the focus to toolbar here, so the next traversed component is still in the toolbar after action is performed.
      // In practice, when user presses tab after action performed, the first enabled button in the toolbar will gain the focus.
      this@AnimationToolbar.requestFocusInWindow()
      callback.run()
    }
    button.minimumSize = JBUI.size(22, 22)
    button.isBorderPainted = false
    button.font = BUTTON_FONT
    button.isEnabled = false
    button.toolTipText = tooltip
    return button
  }

  /**
   * Set the enabled states of all the toolbar controls
   */
  protected fun setEnabledState(play: Boolean, pause: Boolean, stop: Boolean, frame: Boolean) {
    myPlayButton.isEnabled = play
    myPauseButton.isEnabled = pause
    myStopButton.isEnabled = stop
    myFrameFwdButton.isEnabled = frame
    myFrameBckButton.isEnabled = frame
    myFrameControl.isEnabled = frame
  }

  /**
   * Set the visibilities of all the toolbar controls
   */
  protected fun setVisibilityState(play: Boolean, pause: Boolean, stop: Boolean, frame: Boolean) {
    myPlayButton.isVisible = play
    myPauseButton.isVisible = pause
    myStopButton.isVisible = stop
    myFrameFwdButton.isVisible = frame
    myFrameBckButton.isVisible = frame
    myFrameControl.isVisible = frame
  }

  /**
   * Set the tooltips of all the toolbar controls
   */
  protected fun setTooltips(play: String?,
                            pause: String?,
                            stop: String?,
                            frameForward: String?,
                            frameback: String?) {
    myPlayButton.toolTipText = play
    myPauseButton.toolTipText = pause
    myStopButton.toolTipText = stop
    myFrameFwdButton.toolTipText = frameForward
    myFrameBckButton.toolTipText = frameback
  }

  private fun onPlay() {
    stopFrameTicker()
    setEnabledState(false, true, true, false)
    setVisibilityState(false, true, true, true)
    myLastTickMs = System.currentTimeMillis()
    myTicker = EdtExecutorService.getScheduledExecutorInstance()
      .scheduleWithFixedDelay({
        val now = System.currentTimeMillis()
        val elapsed = now - myLastTickMs
        myLastTickMs = now
        onTick(elapsed) }, 0L, TICKER_STEP.toLong(), TimeUnit.MILLISECONDS)
  }

  private fun onPause() {
    setEnabledState(true, false, true, true)
    setVisibilityState(true, false, true, true)
    stopFrameTicker()
  }

  private fun stopFrameTicker() {
    if (myTicker != null) {
      myTicker!!.cancel(false)
      myTicker = null
    }
  }

  private fun onStop() {
    stopFrameTicker()
    setEnabledState(true, false, false, false)
    setVisibilityState(true, false, true, true)
    setFramePosition(myMinTimeMs, false)
  }

  private fun doFrame() {
    myListener.animateTo(this, myFramePositionMs)
  }

  private fun onFrameFwd() {
    onTick(myTickStepMs)
  }

  private fun onFrameBck() {
    onTick(-myTickStepMs)
  }

  /**
   * Called after a new frame position has been set
   */
  private fun onNewFramePosition(setByUser: Boolean) {
    if (isUnlimitedAnimationToolbar) {
      return
    }
    if (myFramePositionMs >= myMaxTimeMs) {
      if (!setByUser && !myLoopEnabled) {
        // We've reached the end. Stop.
        onPause()
      }
    }
    myStopButton.isEnabled = myFramePositionMs - myTickStepMs >= myMinTimeMs
    myFrameFwdButton.isEnabled = myFramePositionMs + myTickStepMs <= myMaxTimeMs
    myFrameBckButton.isEnabled = myFramePositionMs - myTickStepMs >= myMinTimeMs

    val timeSliderModel = myTimeSliderModel
    if (timeSliderModel != null) {
      timeSliderModel.removeChangeListener(myTimeSliderChangeModel)
      timeSliderModel.value = ((myFramePositionMs - myMinTimeMs) / (myMaxTimeMs - myMinTimeMs).toFloat() * 100).toInt()
      timeSliderModel.addChangeListener(myTimeSliderChangeModel)
    }
  }

  /**
   * Sets a new frame position. If newPositionMs is outside of the min and max values, the value will be truncated to be within the range.
   *
   * @param newPositionMs new position in ms
   * @param setByUser     true if this new position was set by the user. In those cases we might want to automatically loop
   */
  private fun setFramePosition(newPositionMs: Long, setByUser: Boolean) {
    myFramePositionMs = newPositionMs
    if (myFramePositionMs < myMinTimeMs) {
      myFramePositionMs = if (myLoopEnabled) myMaxTimeMs else myMinTimeMs
    } else if (!isUnlimitedAnimationToolbar && myFramePositionMs > myMaxTimeMs) {
      myFramePositionMs = if (myLoopEnabled) myMinTimeMs else myMaxTimeMs
    }
    onNewFramePosition(setByUser)
    doFrame()
  }

  /**
   * User triggered new position in the animation
   *
   * @param newPositionMs
   */
  private fun seek(newPositionMs: Long) {
    setFramePosition(myMinTimeMs + newPositionMs, true)
  }

  /**
   * Called for every automatic tick in the animation
   *
   * @param elapsed
   */
  private fun onTick(elapsed: Long) {
    setFramePosition(myFramePositionMs + elapsed, false)
  }

  fun setMaxtimeMs(maxTimeMs: Long) {
    myMaxTimeMs = maxTimeMs
  }

  fun setLoop(enabled: Boolean) {
    myLoopEnabled = enabled
  }

  /**
   * Stop any running animations
   */
  fun stop() {
    onStop()
  }

  /**
   * True if this is an animation toolbar for an unlimited toolbar
   */
  private val isUnlimitedAnimationToolbar: Boolean
    get() = myMaxTimeMs == -1L

  override fun dispose() {
    stopFrameTicker()
  }

  companion object {
    private const val TICKER_STEP = 1000 / 30 // 30 FPS
    private val BUTTON_FONT = UIUtil.getLabelFont(UIUtil.FontSize.MINI)

    /**
     * Constructs a new AnimationToolbar
     *
     * @param parentDisposable Parent [Disposable]
     * @param listener         [AnimationListener] that will be called in every tick
     * @param tickStepMs       Number of milliseconds to advance in every animator tick
     * @param minTimeMs        Start milliseconds for the animation
     */
    fun createUnlimitedAnimationToolbar(parentDisposable: Disposable,
                                        listener: AnimationListener,
                                        tickStepMs: Long,
                                        minTimeMs: Long): AnimationToolbar {
      return AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, -1, AnimationToolbarType.UNLIMITED)
    }

    /**
     * Constructs a new AnimationToolbar
     *
     * @param parentDisposable Parent [Disposable]
     * @param listener         [AnimationListener] that will be called in every tick
     * @param tickStepMs       Number of milliseconds to advance in every animator tick
     * @param minTimeMs        Start milliseconds for the animation
     * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
     */
    fun createAnimationToolbar(parentDisposable: Disposable,
                               listener: AnimationListener,
                               tickStepMs: Long,
                               minTimeMs: Long,
                               initialMaxTimeMs: Long): AnimationToolbar {
      return AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, initialMaxTimeMs, AnimationToolbarType.LIMITED)
    }
  }

  init {
    Disposer.register(parentDisposable, this)
    myListener = listener
    myTickStepMs = tickStepMs
    myMinTimeMs = minTimeMs
    myMaxTimeMs = initialMaxTimeMs
    this.toolbarType = toolbarType
    myPlayButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.PLAY, "Play", DEFAULT_PLAY_TOOLTIP,
      AnimationToolbarAction.PLAY
    ) { onPlay() }
    myPlayButton.isEnabled = true
    myPauseButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.PAUSE, "Pause", DEFAULT_PAUSE_TOOLTIP,
      AnimationToolbarAction.PAUSE
    ) { onPause() }
    myPauseButton.isEnabled = true
    // TODO(b/176806183): Before having a reset icon, use refresh icon instead.
    myStopButton = newControlButton(
      StudioIcons.LayoutEditor.Toolbar.REFRESH, "Stop", DEFAULT_STOP_TOOLTIP,
      AnimationToolbarAction.STOP
    ) { onStop() }
    myFrameFwdButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.GO_TO_END, "Step forward", DEFAULT_FRAME_FORWARD_TOOLTIP,
      AnimationToolbarAction.FRAME_FORWARD
    ) { onFrameFwd() }
    myFrameBckButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.GO_TO_START, "Step backward", DEFAULT_FRAME_BACK_TOOLTIP,
      AnimationToolbarAction.FRAME_BACKWARD
    ) { onFrameBck() }
    controlBar = object : JPanel(FlowLayout()) {
      override fun updateUI() {
        setUI(DesignSurfaceToolbarUI())
      }
    }
    val buttonsPanel = Box.createHorizontalBox()
    buttonsPanel.add(myStopButton)
    buttonsPanel.add(myFrameBckButton)
    buttonsPanel.add(myPlayButton)
    buttonsPanel.add(myPauseButton)
    buttonsPanel.add(myFrameFwdButton)
    controlBar.add(buttonsPanel)
    if (isUnlimitedAnimationToolbar) {
      myTimeSlider = null
      myTimeSliderModel = null
      myTimeSliderChangeModel = null
    } else {
      myTimeSliderModel = DefaultBoundedRangeModel(0, 0, 0, 100)
      myTimeSliderChangeModel = ChangeListener { e: ChangeEvent? ->
        myAnalyticsManager.trackAction(this.toolbarType, AnimationToolbarAction.FRAME_CONTROL)
        val newPositionMs = ((myMaxTimeMs - myMinTimeMs) * (myTimeSliderModel!!.value / 100f)).toLong()
        seek(newPositionMs)
      }
      myTimeSlider = object : JSlider(0, 100, 0) {
        override fun updateUI() {
          setUI(AnimationToolbarSliderUI(this))
          updateLabelUIs()
        }
      }
      myTimeSlider!!.isOpaque = false
      myTimeSlider!!.border = JBUI.Borders.empty()
      myTimeSliderModel!!.addChangeListener(myTimeSliderChangeModel)
      myTimeSlider!!.model = myTimeSliderModel
      buttonsPanel.add(JSeparator(SwingConstants.VERTICAL))
      controlBar.add(myTimeSlider)
    }
    myFrameControl = JSlider(-5, 5, 0)
    myFrameControl.snapToTicks = true
    add(controlBar)
    myFrameControl.addChangeListener { e: ChangeEvent? ->
      stopFrameTicker()
      val value = myFrameControl.value
      if (value == 0) {
        stopFrameTicker()
        return@addChangeListener
      }
      val frameChange = myTickStepMs * value
      myTicker = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(
        { onTick(frameChange) },
        0L, TICKER_STEP.toLong(), TimeUnit.MILLISECONDS
      )
    }
    myFrameControl.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (!myFrameControl.isEnabled) {
          return
        }
        stopFrameTicker()
        myFrameControl.value = 0
      }
    })
    onStop()
  }
}
