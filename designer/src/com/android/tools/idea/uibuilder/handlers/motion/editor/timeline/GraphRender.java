/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.MonotoneSpline;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Oscillator;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Class for rendering the graphs that show in the timeline
 */
public class GraphRender {
  private static final boolean DEBUG = false;
  private Cycle mCycle = null;
  private Attribute[] mAttribute = null;
  private String[] mStartEndString = new String[2];

  static String[] ourWaveTypes = {"sin", "square", "triangle", "sawtooth", "reverseSawtooth", "cos", "bounce"};
  static HashMap<String, Integer> ourWaveTypeMap = new HashMap<>();

  static {
    for (int i = 0; i < ourWaveTypes.length; i++) {
      ourWaveTypeMap.put(ourWaveTypes[i], i);
    }
  }

  public boolean setUp(MeModel model, TimeLineRowData row) {
    if (DEBUG) {
      Debug.log("row = " + row.mType);
    }
    mAttribute = null;
    mCycle = null;
    switch (row.mType) {
      case TimeLineRowData.TYPE_KEY_ATTRIBUTE:
        return buildAttributeGraph(model, row);
      case TimeLineRowData.TYPE_KEY_CYCLE:
        return buildCycleGraph(row);
      case TimeLineRowData.TYPE_KEY_POSITION:
        break;
      case TimeLineRowData.TYPE_KEY_TIME_CYCLE:
        break;
      case TimeLineRowData.TYPE_KEY_TRIGGER:
        break;
    }
    return false;
  }

  /**
   * Return false if we do not know how to render it
   *
   * @param row
   * @return
   */
  private boolean buildCycleGraph(TimeLineRowData row) {
    MTag[] keyFrames = row.mKeyFrames.toArray(new MTag[0]);
    if (row.mKeyProp.contains(",")) {
      return false;
    }

    Arrays.sort(keyFrames, new Comparator<MTag>() {
      @Override
      public int compare(MTag t1, MTag t2) {
        int p1 = Integer.parseInt(t1.getAttributeValue("framePosition"));
        int p2 = Integer.parseInt(t2.getAttributeValue("framePosition"));

        return Integer.compare(p1, p2);
      }
    });

    double[] pos = new double[keyFrames.length];
    double[] period = new double[keyFrames.length];
    double[] amp = new double[keyFrames.length];
    double[] offset = new double[keyFrames.length];
    int curveType = 0;
    for (int i = 0; i < pos.length; i++) {
      MTag kf = keyFrames[i];
      pos[i] = Integer.parseInt(kf.getAttributeValue("framePosition")) / 100.0;
      offset[i] = parse(0, kf.getAttributeValue("waveOffset"));
      period[i] = parse(0, kf.getAttributeValue("wavePeriod"));
      amp[i] = parse(0, kf.getAttributeValue(row.mKeyProp));

      String str = kf.getAttributeValue("waveShape");
      if (str == null) {
        str = "sin";
      }
      if (ourWaveTypeMap.containsKey(str)) {
        curveType = Math.max(curveType, ourWaveTypeMap.get(str));
      }
    }

    mCycle = new Cycle();
    mCycle.setCycle(pos, period, amp, offset, curveType);
    mCycle.fixRange(row.mKeyProp);
    return true;
  }

  static double parse(double def, String str) {
    if (str == null) return def;
    if (str.endsWith("dp")) {
      str = str.substring(0, str.length() - 2);
    }
    return Double.parseDouble(str);
  }

  /**
   * @param model
   * @param row
   * @return
   */
  private boolean buildAttributeGraph(MeModel model, TimeLineRowData row) {
    String[] attrs = {row.mKeyProp};
    if (row.mKeyProp.contains(",")) {
      attrs = attrs[0].split(",");
    }
    mAttribute = new Attribute[attrs.length];
    for (int i = 0; i < attrs.length; i++) {
      String attr = attrs[i];
      model.findStartAndEndValues(model.layout, attr, row.mKeyFrames.get(0), mStartEndString);
      if (DEBUG) {
        Debug.log("                        " + row.mKeyProp + " =  " + Arrays.toString(mStartEndString));
      }
      if (mStartEndString[0] == null || mStartEndString[1] == null) {
        return false;
      }
      double startValue = parse(mStartEndString[0]);
      double endValue = parse(mStartEndString[0]);
      mAttribute[i] = new Attribute(row, attr, startValue, endValue);
    }

    return true;
  }

  static double parse(String str) {
    if (str.endsWith("dp")) {
      str = str.substring(0, str.length() - 2);
    }
    return Double.parseDouble(str);
  }

  int[] xPoints = new int[1000];
  int[] yPoints = new int[1000];

  public void draw(Graphics g, TimelineStructure mTimelineStructure, int x, int y, int w, int h) {
    if (mAttribute == null && mCycle == null) {
      return;
    }
    int gx = x + mTimelineStructure.mTimeLineInsetLeft;
    int gw = w - mTimelineStructure.mTimeLineInsetLeft - mTimelineStructure.mTimeLineInsetRight;
    Color c = g.getColor();

    g.setColor(MEUI.Graph.ourG_Background);
    g.fillRect(x + 1, y, w - 1, h);
    g.setColor(MEUI.ourBorder);
    g.drawRect(x, y, w, h - 1);
    g.setColor(MEUI.Graph.ourG_line);

    if (mCycle != null) {
      mCycle.plot(g, gx, y, gw, h);
    }
    if (mAttribute != null) {
      for (int i = 0; i < mAttribute.length; i++) {
        Attribute attribute = mAttribute[i];
        attribute.plot(g, gx, y, gw, h);
      }
    }

    g.setColor(c);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  static double lockAttributeMin(String attr, double current) {
    switch (attr) {
      case "alpha":
        return 0;
      case "scaleX":
        return 0;
      case "scaleY":
        return 0;
      case "elevation":
        return 0;
    }
    return current;
  }

  static double lockAttributeMax(String attr, double current) {
    switch (attr) {
      case "alpha":
        return 1;
    }
    return current;
  }

  static float lockAttributeMax(String attr, float current) {
    switch (attr) {
      case "alpha":
        return 1;
    }
    return current;
  }

  static float lockAttributeMin(String attr, float current) {
    switch (attr) {
      case "alpha":
        return 0;
      case "scaleX":
        return 0;
      case "scaleY":
        return 0;
      case "elevation":
        return 0;
    }
    return current;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  static class Attribute {
    String mType;
    MonotoneSpline spline;
    int[] xPoints = new int[1000];
    int[] yPoints = new int[1000];
    double mMin;
    double mMax;

    public Attribute(TimeLineRowData row, String attr, double startValue, double endValue) {
      mType = attr;
      setup(row, attr, startValue, endValue);
    }

    public boolean setup(TimeLineRowData row, String attr, double startValue, double endValue) {
      MTag[] keyFrames = row.mKeyFrames.toArray(new MTag[0]);
      if (DEBUG) {
        Debug.log(attr + " no of keyFrames = " + keyFrames.length);
      }
      Arrays.sort(keyFrames, new Comparator<MTag>() {
        @Override
        public int compare(MTag t1, MTag t2) {
          int p1 = Integer.parseInt(t1.getAttributeValue("framePosition"));
          int p2 = Integer.parseInt(t2.getAttributeValue("framePosition"));

          return Integer.compare(p1, p2);
        }
      });
      if (attr.contains(",")) {
        return false;
      }
      if (DEBUG) {
        Debug.log(mType + "  " + keyFrames.length + 2);
      }
      double[] pos = new double[keyFrames.length + 2];
      int[] int_pos = new int[pos.length];
      double[][] values = new double[pos.length][1];

      double min = Math.min(startValue, endValue);
      double max = Math.max(startValue, endValue);
      pos[0] = 0;
      pos[pos.length - 1] = 1;
      values[0][0] = startValue;
      values[pos.length - 1][0] = endValue;
      for (int i = 0; i < keyFrames.length; i++) {
        MTag kf = row.mKeyFrames.get(i);
        int x = Integer.parseInt(kf.getAttributeValue("framePosition"));
        double y = parse(kf.getAttributeValue(attr));
        if (DEBUG) {
          Debug.log(mType + " value " + y);
        }
        int_pos[i + 1] = x;
        pos[i + 1] = x / 100.0;
        values[i + 1][0] = y;
        if (y > max) {
          max = y;
        }
        if (y < min) {
          min = y;
        }
      }
      mMin = min;
      mMax = max;
      mMin = lockAttributeMin(attr, mMin);
      mMax = lockAttributeMax(attr, mMax);
      mMin -= 0.06 * (mMax - mMin);
      mMax += 0.06 * (mMax - mMin);

      if (DEBUG) {
        Debug.log(mType + " values " + Arrays.deepToString(values));
        Debug.log(mType + " pos    " + Arrays.toString(pos));
      }
      spline = new MonotoneSpline(pos, values);
      return true;
    }

    // plot the attribute
    public void plot(Graphics g, int x, int y, int w, int h) {

      if (spline != null) {
        double steps = 1.0 / w;
        int count = 0;
        for (double i = 0; i <= 1; i += steps) {
          double yp = spline.getPos(i, 0);
          xPoints[count] = (int)(x + i * w);
          yPoints[count] = (int)(y + h - (yp - mMin) * h / (mMax - mMin));
          count++;
        }
        g.drawPolyline(xPoints, yPoints, count);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  static class Cycle {
    MonotoneSpline mMonotoneSpline;
    Oscillator mOscillator;
    float[] xpos = new float[400];
    double[] ypos = new double[xpos.length];
    float[] yMax = new float[xpos.length];
    float[] yMin = new float[xpos.length];
    float mMaxY, mMinY;
    int[] xPoints = new int[xpos.length];
    int[] yPoints = new int[xpos.length];

    void setCycle(double[] pos, double[] period, double[] amplitude, double[] offset,
                  int curveType) {
      if (pos.length == 1) {
        pos = new double[]{0.0, pos[0], 1.0};
        period = new double[]{period[0], period[0], period[0]};
        amplitude = new double[]{amplitude[0], amplitude[0], amplitude[0]};
        offset = new double[]{offset[0], offset[0], offset[0]};
      }
      if (DEBUG) {
        for (int i = 0; i < pos.length; i++) {
          Debug.log(i + " " + pos[i] + " " + period[i] + " " + amplitude[i] + " " + offset[i]);
        }
      }
      double[] t = new double[pos.length];
      double[][] v = new double[pos.length][2];

      for (int i = 0; i < pos.length; i++) {
        t[i] = pos[i];
        v[i][0] = amplitude[i];
        v[i][1] = offset[i];
      }
      MonotoneSpline ms = new MonotoneSpline(t, v);
      Oscillator osc = new Oscillator();

      osc.setType(curveType);
      for (int i = 0; i < pos.length; i++) {

        osc.addPoint(pos[i], (float)period[i]);
      }
      osc.normalize();

      mMonotoneSpline = ms;
      mOscillator = osc;
      mMaxY = -Float.MAX_VALUE;
      mMinY = Float.MAX_VALUE;

      for (int i = 0; i < xpos.length; i++) {
        xpos[i] = (float)(i / (xpos.length - 1.0f));
        double amp = mMonotoneSpline.getPos(xpos[i], 0);
        double off = mMonotoneSpline.getPos(xpos[i], 1);
        try {
          ypos[i] = mOscillator.getValue(xpos[i]) * amp + off;
        }
        catch (Exception e) {
          ypos[i] = Math.random(); // visual hint that it is broken
        }

        yMax[i] = (float)(amp + off);
        yMin[i] = (float)(-amp + off);
        mMaxY = Math.max(mMaxY, (float)ypos[i]);
        mMaxY = Math.max(mMaxY, yMax[i]);
        mMinY = Math.min(mMinY, yMin[i]);
      }

      if (DEBUG) {
        Debug.log(mMinY + " " + mMaxY);
      }
    }

    void fixRange(String attr) {
      mMinY = lockAttributeMin(attr, mMinY);
      mMaxY = lockAttributeMax(attr, mMaxY);
      mMaxY += 0.06 * (mMaxY - mMinY);
      mMinY -= 0.06 * (mMaxY - mMinY);
    }

    void plot(Graphics g, int x, int y, int w, int h) {
      g.setColor(MEUI.Graph.ourG_line);

      for (int i = 0; i < xpos.length; i++) {
        int xp = (int)(w * xpos[i] + x);
        int yp = y + (int)(h - h * (ypos[i] - mMinY) / (mMaxY - mMinY));
        xPoints[i] = xp;
        yPoints[i] = yp;
      }
      g.drawPolyline(xPoints, yPoints, xPoints.length);
    }

    float getComputedValue(float v) {
      if (mMonotoneSpline == null) {
        return 0;
      }
      double amp = mMonotoneSpline.getPos(v, 0);
      double off = mMonotoneSpline.getPos(v, 1);
      return (float)(mOscillator.getValue(v) * amp + off);
    }
  }
}
