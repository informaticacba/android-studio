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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

/**
 * This class contains all data common to the timeline chart
 * including the ourBorder insets
 * timing cursors etc.
 */
public class Chart {
  public float myPlayBackSpeed = 1;
  Gantt myGantt;
  int myChartLeftInset = JBUI.scale(40);
  int myChartRightInset = JBUI.scale(25);
  public int myBottomInsert = JBUI.scale(20);
  static final int ourViewListWidth = JBUI.scale(150);

  int myContainerWidth;
  int myContainerHeight;
  private float myTimeCursorMs = 300f; // The point where the Animation is
  float myPixelsPerMs;
  int myAnimationTotalTimeMs = 600;
  int[] myXTicksPixels = new int[20]; // calculated by TimeLine
  int myXTickCount = 0;

  public float myZoom = 1;
  float myPixelsPerPercent = 5;
  private GanttCommands.Mode myMode = GanttCommands.Mode.UNKNOWN;

  // Selection
  public String mySelectedKeyView;
  Selection mySelection = Selection.NONE;
  MotionSceneModel.KeyFrame mySelectedKeyFrame;
  SmartPsiElementPointer<XmlTag> mySelectedKeyFrameTag;
  MotionSceneModel.ConstraintView mySelectedConstraint; // if a <constraint> is selected null otherwise
  SmartPsiElementPointer<XmlTag> mySelectedConstraintTag;

  public void select(MotionSceneModel.ConstraintView constraint) {
    mySelectedConstraint = constraint;
    if (constraint == null) {
      mySelectedConstraintTag = null;
    }
    else {
      mySelection = Selection.CONSTRAINT;
      mySelectedConstraintTag = constraint.getTag();
      mySelectedKeyView = constraint.mId;
      mySelectedKeyFrameTag = null;
      mySelectedKeyFrame = null;
    }
  }

  public void select(MotionSceneModel.KeyFrame keyFrame) {
    mySelectedKeyFrame = keyFrame;
    if (keyFrame == null) {
      mySelectedConstraintTag = null;
    }
    else {
      mySelection = Selection.KEY;
      mySelectedKeyView = keyFrame.target;
      mySelectedKeyFrameTag = keyFrame.getTag();
      mySelectedConstraint = null;
      mySelectedConstraintTag = null;
    }
  }

  public void selectView(String view) {
    mySelectedKeyView = view;
    mySelectedKeyFrame = null;
    mySelectedConstraintTag = null;
    mySelectedKeyFrameTag = null;
    mySelectedConstraint = null;
    mySelection = Selection.VIEW;
  }

  MotionSceneModel.KeyFrame myCopyBuffer;
  // ========================= s

  MotionSceneModel myModel;

  ArrayList<Gantt.ChartElement> myChartElements = new ArrayList<>();
  ArrayList<Gantt.ViewElement> myViewElements = new ArrayList<>();

  static private Color myTimeCursorColor = new JBColor(0xff3d81e1, 0xff3d81e1);
  static private Color myTimeCursorStartColor = new JBColor(0xff3da1f1, 0xff3dd1f1);
  static private Color myTimeCursorEndColor = new JBColor(0xff3da1f1, 0xff3dd1f1);
  static Color myGridColor = new Color(0xff838383);
  static Color myUnSelectedLineColor = new Color(0xe0759a);
  static Color ourMySelectedLineColor = new Color(0x3879d9);
  static Color ourPrimaryPanelBackground = new JBColor(0xf5f5f5, 0x2D2F31);
  static Color ourSecondaryPanelBackground = new JBColor(0xfcfcfc, 0x313435);
  static Color ourAvgBackground = new JBColor(0xf8f8f8, 0x2f3133);
  static Color ourBorder = new JBColor(0xc9c9c9, 0x242627);
  static Color ourBorderLight = new JBColor(0xe8e6e6, 0x3c3f41);
  static int ourGraphHeight = JBUI.scale(60);
  static Color ourAddConstraintColor = new JBColor(0xff838383, 0xff666666);
  static Color ourAddConstraintPlus = new JBColor(0xffc9c9c9, 0xff333333);

  GraphElements myGraphElements;

  public float getTimeCursorMs() {
    return myTimeCursorMs;
  }

  public void setTimeCursorMs(float timeCursorMs) {
    myTimeCursorMs = timeCursorMs;
    myGantt.updateLabel();
  }

  public float getAnimationTotalTimeMs() {
    return myAnimationTotalTimeMs;
  }

  public void setAnimationTotalTimeMs(int time) {
    myAnimationTotalTimeMs = time;
    update(Gantt.ChartElement.Reason.ZOOM);
  }

  public void setMotionSceneModel(MotionSceneModel model) {
    myModel = model;
    if (myModel != null) {
      int duration = myModel.getTransitionTag(0).duration;
      setAnimationTotalTimeMs(duration);
      myGantt.setDurationMs(duration);
    }

    if (mySelectedKeyFrameTag != null && mySelectedKeyView != null) {
      MotionSceneModel.MotionSceneView m = myModel.getMotionSceneView(mySelectedKeyView);
      m.myKeyPositions.stream()
        .filter(keyframe -> keyframe.getTag() == mySelectedKeyFrameTag)
        .forEach(keyframe -> mySelectedKeyFrame = keyframe);
      m.myKeyAttributes.stream()
        .filter(keyframe -> keyframe.getTag() == mySelectedKeyFrameTag)
        .forEach(keyframe -> mySelectedKeyFrame = keyframe);
      m.myKeyCycles.stream()
        .filter(keyframe -> keyframe.getTag() == mySelectedKeyFrameTag)
        .forEach(keyframe -> mySelectedKeyFrame = keyframe);
    }

    if (mySelectedConstraintTag != null && mySelectedKeyView != null) {
      MotionSceneModel.ConstraintView startConstraint = myModel.getStartConstraintSet().myConstraintViews.get(mySelectedKeyView);
      MotionSceneModel.ConstraintView endConstraint = myModel.getEndConstraintSet().myConstraintViews.get(mySelectedKeyView);
      if (startConstraint != null && startConstraint.getTag() == mySelectedConstraintTag) {
        mySelectedConstraint = startConstraint;
      } else if (endConstraint != null && endConstraint.getTag() == mySelectedConstraintTag) {
        mySelectedConstraint = endConstraint;
      }
    }
    update(Gantt.ChartElement.Reason.SELECTION_CHANGED);
  }

  public SmartPsiElementPointer<XmlTag> getSelectedConstraint() {
    return mySelectedConstraintTag;
  }

  public static Color getColorForPosition(int position) {
    return Chart.myTimeCursorColor;
  }

  // ===================================GraphElements=================================== //
  static class GraphElements {
    final String myElement;
    final String myViewId;

    enum Type {
      ATTRIBUTES, CYCLES
    }

    final Type myType;

    GraphElements(Type type, String viewId, String element) {
      myViewId = viewId;
      myElement = element;
      myType = type;
    }

    @Override
    public String toString() {
      return myElement;
    }
  }
  // ===================================GraphElements=================================== //

  public void setMode(GanttCommands.Mode mode) {
    myMode = mode;
    update(Gantt.ChartElement.Reason.MODE_CHANGE);
  }

  public GanttCommands.Mode getMode() {
    return myMode;
  }

  @Nullable
  public String getSelectedKeyView() { return mySelectedKeyView; }

  @Nullable
  public MotionSceneModel.KeyFrame getSelectedKeyFrame() { return mySelectedKeyFrame; }

  enum Selection {
    NONE,
    KEY,
    VIEW,
    CONSTRAINT
  }

  public float getPlayBackSpeed() {
    return myPlayBackSpeed;
  }

  public float getAnimationTimeInMs() {
    return myAnimationTotalTimeMs;
  }

  public Chart(Gantt gantt) {myGantt = gantt;}

  public void setCursorPosition(float position) {
    setTimeCursorMs(position * myAnimationTotalTimeMs);
    update(Gantt.ChartElement.Reason.CURSOR_POSITION_CHANGED);
  }

  public float getProgress() {
    return getTimeCursorMs() / myAnimationTotalTimeMs;
  }

  public int getFramePosition() {
    return (int)((0.5f + 100 * getTimeCursorMs()) / myAnimationTotalTimeMs);
  }

  public int getCursorPosition() {
    float time = getTimeCursorMs();
    return myChartLeftInset + (int)(time * myPixelsPerMs);
  }

  public void add(Gantt.ChartElement element) {
    myChartElements.add(element);
  }

  public void update(Gantt.ChartElement.Reason reason) {
    switch (reason) {
      case RESIZE:
      case ZOOM:
        updateZoom();
        break;
      case SELECTION_CHANGED:
        myGantt.selectionChanged();
        break;
      default:
    }
    for (Gantt.ChartElement chartElement : myChartElements) {
      chartElement.update(reason);
    }
  }

  public void setZoom(float zoom) {
    myZoom = zoom;
    update(Gantt.ChartElement.Reason.ZOOM);
  }

  public void updateZoom() {
    int width = myContainerWidth - myChartRightInset - myChartLeftInset;
    myPixelsPerPercent = (myZoom * width) / 100;
    myPixelsPerMs = (myZoom * width) / myAnimationTotalTimeMs;
  }

  public int getmNumberOfViews() {
    return myViewElements.size();
  }

  public void clear() {
    myViewElements.clear();
  }

  public void addView(Gantt.ViewElement viewElement) {
    myViewElements.add(viewElement);
  }

  public int getGraphWidth() {
    return (int)(myPixelsPerPercent * 100f) + myChartLeftInset + myChartRightInset;
  }
}
