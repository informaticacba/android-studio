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

import com.android.SdkConstants;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString;
import com.android.tools.idea.uibuilder.model.NlModelHelper;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NamedNodeMap;

import java.util.*;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.*;

/**
 * The model of the Motion scene file.
 * This parses the file and provide hooks to write the file.
 */
public class MotionSceneModel {
  public static final boolean BROKEN = true;

  HashMap<String, MotionSceneView> mySceneViews = new HashMap<>();
  ArrayList<ConstraintSet> myConstraintSets;
  ArrayList<TransitionTag> myTransition;
  OnSwipeTag myOnSwipeTag;
  private VirtualFile myVirtualFile;
  private Project myProject;
  private NlModel myNlModel;

  public TransitionTag getTransitionTag(int i) {
    return myTransition.get(i);
  }

  public OnSwipeTag getOnSwipeTag() {
    return myOnSwipeTag;
  }

  // Represents a single view in the motion scene
  public static class MotionSceneView {
    String mid;
    MotionSceneModel myModel;
    public ArrayList<KeyPosition> myKeyPositions = new ArrayList<>();
    public ArrayList<KeyAttributes> myKeyAttributes = new ArrayList<>();
    public ArrayList<KeyCycle> myKeyCycles = new ArrayList<>();
  }

  public MotionSceneView getMotionSceneView(@NotNull String viewId) {
    return mySceneViews.get(viewId);
  }

  /**
   * Create a new Keyframe tag
   *
   * @param nlModel
   * @param type
   * @param framePosition
   * @param name
   */
  public void createKeyFrame(String type, int framePosition, String name) {
    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, myVirtualFile);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        XmlTag keyFrame = null;
        XmlTag[] tags = xmlFile.getRootTag().getSubTags();
        for (int i = 0; i < tags.length; i++) {
          XmlTag tag = tags[i];
          String keyNodeName = tag.getName();
          if (keyNodeName.equals(MotionSceneKeyFrames)) {
            keyFrame = tag;
            break;
          }
        }
        if (keyFrame == null) { // no keyframes need to create
          keyFrame =
            xmlFile.getRootTag().createChildTag(MotionSceneKeyFrames, null, null, false);
          keyFrame = xmlFile.getRootTag().addSubTag(keyFrame, false);
        }

        XmlTag createdTag = keyFrame.createChildTag(type, null, "", false);
        createdTag = keyFrame.addSubTag(createdTag, false);
        createdTag.setAttribute(KeyAttributes_framePosition, AUTO_URI, Integer.toString(framePosition));
        createdTag.setAttribute(KeyAttributes_target, AUTO_URI, "@id/" + name);
      }
    });
    if (myNlModel != null) {
      // TODO: we may want to do live edits instead, but LayoutLib needs
      // anyway to save the file to disk, so...
      LayoutPullParsers.saveFileIfNecessary(xmlFile);
      myNlModel.notifyModified(NlModel.ChangeType.EDIT);
    }
  }

  /* ===========================KeyFrame===================================*/

  public static abstract class KeyFrame {
    protected final MotionSceneModel myMotionSceneModel;
    protected String mType;

    int framePosition;
    String target;
    HashMap<String, Object> myAttributes = new HashMap<>();
    protected String[] myPossibleAttr;

    public KeyFrame(MotionSceneModel motionSceneModel) {
      myMotionSceneModel = motionSceneModel;
    }

    public abstract String[] getDefault(String key);

    public MotionSceneModel getModel() {
      return myMotionSceneModel;
    }

    public String getString(String type) {
      if ("target".equals(type)) {
        return target;
      }
      return null;
    }

    public boolean isAndroidAttribute(String str) {
      return false;
    }

    public String[] getPossibleAttr() {
      return myPossibleAttr;
    }

    public int getFramePosition() { return framePosition; }

    public float getFloat(String type) {
      String val = myAttributes.get(type).toString();
      if (val.endsWith("dp")) { // TODO check for px etc.
        val = val.substring(0, val.length() - 2);
      }
      return Float.parseFloat(val);
    }

    public void fill(HashMap<String, Object> attributes) {
      attributes.put(Key_framePosition, (Integer)framePosition);
      attributes.put(KeyCycle_target, target);
      attributes.putAll(myAttributes);
    }

    void parse(NamedNodeMap att) {
      int attCount = att.getLength();
      for (int i = 0; i < attCount; i++) {
        parse(att.item(i).getNodeName(), att.item(i).getNodeValue());
      }
    }

    float fparse(String v) {
      if (v.endsWith("dp")) {
        return Float.parseFloat(v.substring(0, v.length() - 2));
      }
      return Float.parseFloat(v);
    }

    void parse(String node, String value) {
      if (value == null) {
        myAttributes.remove(node);
        return;
      }
      if (node.endsWith(MotionSceneString.Key_framePosition)) {
        framePosition = Integer.parseInt(value);
      }
      else if (node.endsWith(MotionSceneString.KeyAttributes_target)) {
        target = value.substring(value.indexOf('/') + 1);
      }
      else {
        myAttributes.put(node, value);
      }
    }

    private String trim(String node) {
      return node.substring(node.indexOf(':') + 1);
    }

    public void parse(XmlAttribute[] attributes) {
      for (int i = 0; i < attributes.length; i++) {
        XmlAttribute attribute = attributes[i];
        parse(trim(attribute.getName()), attribute.getValue());
      }
    }

    /**
     * Delete an attribute from a KeyFrame.
     */
    public boolean deleteAttribute(@NotNull NlModel model, @NotNull String attributeName) {
      if (attributeName.equals(KeyAttributes_target) || attributeName.equals(KeyAttributes_framePosition)) {
        // TODO: Find out why these are called in the first place...
        return false;
      }
      XmlFile xmlFile = motionSceneFile();
      XmlTag tag = findKeyFrameTag();
      if (tag == null) {
        return false;
      }
      String command = "Delete " + attributeName + " attribute";
      String namespace = findAttributeNamespace(tag, attributeName);
      if (namespace == null) {
        return false;
      }
      Runnable operation = () -> {
        tag.setAttribute(attributeName, namespace, null);
        model.notifyModified(NlModel.ChangeType.EDIT);
        // Temporary for LayoutLib:
        LayoutPullParsers.saveFileIfNecessary(xmlFile);
      };

      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      myAttributes.remove(attributeName);
      return true;
    }

    public boolean deleteTag(@NotNull NlModel model) {
      XmlFile xmlFile = motionSceneFile();
      XmlTag tag = findKeyFrameTag();
      if (tag == null) {
        return false;
      }
      String command = "Delete key attributes for: " + target;
      Runnable operation = () -> {
        tag.delete();
        model.notifyModified(NlModel.ChangeType.EDIT);
        // Temporary for LayoutLib:
        LayoutPullParsers.saveFileIfNecessary(xmlFile);
      };
      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      return true;
    }

    /**
     * Set the value of a KeyFrame attribute.
     */
    public void setValue(@NotNull NlModel model, @NotNull String key, @NotNull String value) {
      XmlFile xmlFile = motionSceneFile();
      XmlTag tag = findKeyFrameTag();
      if (tag == null) {
        return;
      }
      String command = "Delete " + key + " attribute";
      String namespace = isAndroidAttribute(key) ? ANDROID_URI : AUTO_URI;
      Runnable operation = () -> {
        tag.setAttribute(key, namespace, value);
        model.notifyModified(NlModel.ChangeType.EDIT);
        // Temporary for LayoutLib:
        LayoutPullParsers.saveFileIfNecessary(xmlFile);
      };

      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, command, null, operation, xmlFile);
      parse(key, value);
    }

    private XmlFile motionSceneFile() {
      return (XmlFile)AndroidPsiUtils.getPsiFileSafely(myMotionSceneModel.myProject, myMotionSceneModel.myVirtualFile);
    }

    @Nullable
    private String findAttributeNamespace(@NotNull XmlTag tag, @NotNull String attributeName) {
      for (XmlAttribute attribute : tag.getAttributes()) {
        if (attributeName.equals(attribute.getLocalName())) {
          return attribute.getNamespace();
        }
      }
      return null;
    }

    /**
     * Find the {@link XmlTag} corresponding to this {@link KeyFrame}
     */
    @Nullable
    private XmlTag findKeyFrameTag() {
      XmlFile xmlFile = motionSceneFile();
      XmlTag root = xmlFile != null ? xmlFile.getRootTag() : null;
      if (root == null) {
        return null;
      }
      XmlTag[] keyFrames = root.findSubTags(MotionSceneKeyFrames);
      if (keyFrames.length == 0) {
        return null;
      }
      XmlTag[] keyAttributes = keyFrames[0].findSubTags(KeyTypeAttributes);
      for (XmlTag tag : keyAttributes) {
        if (match(tag)) {
          return tag;
        }
      }
      return null;
    }

    /**
     * @param xmlTag
     * @return
     */
    boolean match(XmlTag xmlTag) {
      String keyNodeName = xmlTag.getName();

      if (!keyNodeName.equals(mType)) return false;
      XmlAttribute[] attr = xmlTag.getAttributes();
      for (int k = 0; k < attr.length; k++) {
        XmlAttribute attribute = attr[k];
        if (attribute.getName().endsWith("framePosition")) {
          if (Integer.parseInt(attribute.getValue()) != framePosition) {
            return false;
          }
        }
        if (attribute.getName().endsWith("target")) {
          if (!attribute.getValue().endsWith(target)) {
            return false;
          }
        }
      }

      return true;
    }

    public String getName() {
      return mType;
    }

    public String getEasingCurve() {
      return (String)myAttributes.get(KeyPositionCartesian_transitionEasing);
    }
  }

  /* ============================KeyPosition==================================*/

  public static abstract class KeyPosition extends KeyFrame {
    String transitionEasing = null;

    public KeyPosition(MotionSceneModel motionSceneModel) { super(motionSceneModel); }

    @Override
    public float getFloat(String type) {
      return super.getFloat(type);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(KeyPositionCartesian_transitionEasing)) {
        transitionEasing = value;
      }
      super.parse(node, value);
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      if (transitionEasing != null) {
        attributes.put(Key_framePosition, (Integer)framePosition);
      }
      super.fill(attributes);
    }
  }

  /* ==========================KeyPositionPath====================================*/
  public static class KeyPositionPath extends KeyPosition {
    float path_percent = Float.NaN;
    float perpendicularPath_percent = Float.NaN;
    public static String[] ourPossibleAttr = {
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "drawPath",
      "sizePercent",
      "perpendicularPath_percent",
      "path_percent"
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"curve=(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"true", "false"},
      {"0.5"},
      {"0.0"},
      {"0.5"}
    };

    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    public KeyPositionPath(MotionSceneModel motionSceneModel) {
      super(motionSceneModel);
      myPossibleAttr = ourPossibleAttr;
      mType = KeyTypePositionPath;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      if (!Float.isNaN(path_percent)) {
        attributes.put(KeyPositionPath_path_percent, (Float)path_percent);
      }
      if (!Float.isNaN(perpendicularPath_percent)) {
        attributes.put(KeyPositionPath_perpendicularPath_percent, (Float)perpendicularPath_percent);
      }
      super.fill(attributes);
    }

    @Override
    public float getFloat(String type) {
      if ("perpendicularPath_percent".equals(type)) {
        return perpendicularPath_percent;
      }
      if ("path_percent".equals(type)) {
        return perpendicularPath_percent;
      }
      return super.getFloat(type);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(MotionSceneString.KeyPositionPath_path_percent)) {
        path_percent = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionPath_perpendicularPath_percent)) {
        perpendicularPath_percent = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionCartesian_framePosition) || node.endsWith("")) {
        super.parse(node, value);
      }
    }
  }

  /* ============================KeyPositionCartesian==================================*/
  public static class KeyPositionCartesian extends KeyPosition {
    float horizontalPosition_inDeltaX = Float.NaN;
    float verticalPosition_inDeltaY = Float.NaN;
    float horizontalPosition_inDeltaY = Float.NaN;
    float verticalPosition_inDeltaX = Float.NaN;
    float verticalPercent = Float.NaN;
    float horizontalPercent = Float.NaN;
    public static String[] ourPossibleAttr = {
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "drawPath",
      "sizePercent",
      "horizontalPosition_inDeltaX",
      "horizontalPosition_inDeltaY",
      "verticalPosition_inDeltaX",
      "verticalPosition_inDeltaY",
      "horizontalPercent",
      "verticalPercent",
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"curve=(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"true", "false"},
      {"0.5"},
      {"0.5"},
      {"0.5"},
      {"0.5"},
      {"0.5"},
      {"0.5"},
      {"0.5"}
    };

    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    public KeyPositionCartesian(MotionSceneModel motionSceneModel) {
      super(motionSceneModel);
      mType = KeyTypePositionCartesian;
      myPossibleAttr = ourPossibleAttr;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      if (!Float.isNaN(horizontalPosition_inDeltaX)) {
        attributes.put(KeyPositionCartesian_horizontalPosition_inDeltaX, (Float)horizontalPosition_inDeltaX);
      }
      if (!Float.isNaN(verticalPosition_inDeltaY)) {
        attributes.put(KeyPositionCartesian_verticalPosition_inDeltaY, (Float)verticalPosition_inDeltaY);
      }
      if (!Float.isNaN(horizontalPosition_inDeltaY)) {
        attributes.put(KeyPositionCartesian_horizontalPosition_inDeltaY, (Float)horizontalPosition_inDeltaY);
      }
      if (!Float.isNaN(verticalPosition_inDeltaX)) {
        attributes.put(KeyPositionCartesian_verticalPosition_inDeltaX, (Float)verticalPosition_inDeltaX);
      }
      if (!Float.isNaN(verticalPercent)) {
        attributes.put(KeyPositionCartesian_verticalPercent, (Float)verticalPercent);
      }
      if (!Float.isNaN(horizontalPercent)) {
        attributes.put(KeyPositionCartesian_horizontalPercent, (Float)horizontalPercent);
      }
      super.fill(attributes);
    }

    @Override
    public float getFloat(String type) {
      if (KeyPositionCartesian_horizontalPosition_inDeltaX.equals(type)) {
        return horizontalPosition_inDeltaX;
      }
      if (KeyPositionCartesian_verticalPosition_inDeltaY.equals(type)) {
        return verticalPosition_inDeltaY;
      }
      if (KeyPositionCartesian_horizontalPosition_inDeltaY.equals(type)) {
        return horizontalPosition_inDeltaY;
      }
      if (KeyPositionCartesian_verticalPosition_inDeltaX.equals(type)) {
        return verticalPosition_inDeltaX;
      }
      if (KeyPositionCartesian_horizontalPercent.equals(type)) {
        return horizontalPercent;
      }
      if (KeyPositionCartesian_verticalPercent.equals(type)) {
        return verticalPercent;
      }
      return super.getFloat(type);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(MotionSceneString.KeyPositionCartesian_horizontalPosition_inDeltaX)) {
        horizontalPosition_inDeltaX = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionCartesian_verticalPosition_inDeltaY)) {
        verticalPosition_inDeltaY = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionCartesian_horizontalPosition_inDeltaY)) {
        horizontalPosition_inDeltaY = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionCartesian_verticalPosition_inDeltaX)) {
        verticalPosition_inDeltaX = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionCartesian_horizontalPercent)) {
        horizontalPercent = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyPositionCartesian_verticalPercent)) {
        verticalPercent = Float.parseFloat(value);
      }
      else {
        super.parse(node, value);
      }
    }
  }

  /* ===========================KeyAttributes===================================*/
  public static class KeyAttributes extends KeyFrame {
    String curveFit = null;
    ArrayList<CustomAttributes> myCustomAttributes = new ArrayList<>();
    public static String[] ourPossibleAttr = {
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "sizePercent",
      "progress",
      "orientation",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "transitionPathRotate",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ",
      CustomLabel
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"curve=(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"true", "false"},
      {"0.5"},
      {"90"},
      {"0.5"},
      {"5dp"},
      {"45"},
      {"10"},
      {"10"},
      {"90"},
      {"1.5"},
      {"1.5"},
      {"10dp"},
      {"10dp"},
      {"10dp"},
    };

    public static String[] ourPossibleStandardAttr = {
      "orientation",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "transitionPathRotate",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ"
    };
    HashSet<String> myAndroidAttributes = null;

    public ArrayList<CustomAttributes> getCustomAttr() {
      return myCustomAttributes;
    }

    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    @Override
    public boolean isAndroidAttribute(String str) {
      if (myAndroidAttributes == null) {
        myAndroidAttributes = new HashSet<>(Arrays.asList(ourPossibleStandardAttr));
      }
      return myAndroidAttributes.contains(str);
    }

    public KeyAttributes(MotionSceneModel motionSceneModel) {
      super(motionSceneModel);
      mType = KeyTypeAttributes;
      myPossibleAttr = ourPossibleAttr;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      attributes.putAll(myAttributes);
      if (curveFit != null) {
        attributes.put(KeyAttributes_curveFit, curveFit);
      }
      super.fill(attributes);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(MotionSceneString.KeyAttributes_curveFit)) {
        curveFit = value;
      }
      else if (ourStandardSet.contains(node)) {
        myAttributes.put(node, value);
      }
      else if (node.endsWith(MotionSceneString.KeyAttributes_framePosition) || node.endsWith("")) {
        super.parse(node, value);
      }
      else {
        myAttributes.put(node, new Float(value));
      }
    }

    /**
     * @param nlModel
     * @param key
     * @param value
     */
    public void createCustomAttribute(NlModel nlModel, String key, CustomAttributes.Type type, String value) {
      XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(myMotionSceneModel.myProject, myMotionSceneModel.myVirtualFile);

      WriteCommandAction.runWriteCommandAction(myMotionSceneModel.myProject, new Runnable() {
        @Override
        public void run() {
          XmlTag[] tagKeyFrames = xmlFile.getRootTag().findSubTags(MotionSceneKeyFrames);
          for (int i = 0; i < tagKeyFrames.length; i++) {
            XmlTag tagKeyFrame = tagKeyFrames[i];
            XmlTag[] tagkey = tagKeyFrame.getSubTags();

            for (int j = 0; j < tagkey.length; j++) {
              XmlTag xmlTag = tagkey[j];
              if (match(xmlTag)) {
                String head = MotionNameSpace;
                if (isAndroidAttribute(key)) {
                  head = AndroidNameSpace;
                }
                xmlTag.setAttribute(head + key, value);
              }
            }
          }
        }
      });
      if (nlModel != null) {
        // TODO: we may want to do live edits instead, but LayoutLib needs
        // anyway to save the file to disk, so...
        LayoutPullParsers.saveFileIfNecessary(xmlFile);
        nlModel.notifyModified(NlModel.ChangeType.EDIT);
      }
      parse(key, value);
    }
  }

  /* ===========================KeyCycle===================================*/
  public static class KeyCycle extends KeyFrame {
    float waveOffset = Float.NaN;
    float wavePeriod = Float.NaN;
    ArrayList<CustomCycleAttributes> myCustomAttributes = new ArrayList<>();
    String waveShape;
    public static String[] ourPossibleAttr = {
      "target",
      "framePosition",
      "transitionEasing",
      "curveFit",
      "progress",
      "waveShape",
      "wavePeriod",
      "waveOffset",
      //TODO "waveVariesBy",
      "transitionPathRotate",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ"
    };
    public static String[][] ourDefaults = {
      {},
      {},
      {"curve=(0.5,0,0.5,1)"},
      {"spline", "linear"},
      {"0.5"},
      {"0.5"},
      {"sin", "square", "triangle", "sawtooth", "reverseSawtooth", "cos", "bounce"},
      {"1"},
      {"0"},
      {"90"},
      {"0.5"},
      {"20dp"},
      {"45"},
      {"10"},
      {"10"},
      {"1.5"},
      {"1.5"},
      {"20dp"},
      {"20dp"},
      {"20dp"},
    };

    public static String[] ourPossibleStandardAttr = {
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ"
    };

    @Override
    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    HashSet<String> myAndroidAttributes = null;

    @Override
    public String toString() {
      return getName() + Arrays.toString(myAttributes.keySet().toArray());
    }

    @Override
    public boolean isAndroidAttribute(String str) {
      if (myAndroidAttributes == null) {
        myAndroidAttributes = new HashSet<>(Arrays.asList(ourPossibleStandardAttr));
      }
      return myAndroidAttributes.contains(str);
    }

    public KeyCycle(MotionSceneModel motionSceneModel) {
      super(motionSceneModel);
      super.mType = KeyTypeCycle;
      myPossibleAttr = ourPossibleAttr;
    }

    @Override
    public void fill(HashMap<String, Object> attributes) {
      attributes.putAll(myAttributes);
      if (!Float.isNaN(waveOffset)) {
        attributes.put(KeyCycle_waveOffset, waveOffset);
      }
      if (!Float.isNaN(wavePeriod)) {
        attributes.put(KeyCycle_wavePeriod, wavePeriod);
      }
      if (waveShape != null) {
        attributes.put(KeyCycle_waveOffset, waveShape);
      }
      super.fill(attributes);
    }

    @Override
    void parse(String node, String value) {
      if (node.endsWith(MotionSceneString.KeyCycle_waveOffset)) {
        waveOffset = fparse(value);
      }
      else if (node.endsWith(MotionSceneString.KeyCycle_wavePeriod)) {
        wavePeriod = Float.parseFloat(value);
      }
      else if (node.endsWith(MotionSceneString.KeyCycle_waveShape)) {
        waveShape = value;
      }
      super.parse(node, value);
    }
  }

  /* ===========================CustomCycleAttributes===================================*/

  public static class CustomCycleAttributes implements AttributeParse {
    KeyCycle parentKeyCycle;
    HashMap<String, Object> myAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "attributeName",
      "customIntegerValue",
      "customFloatValue",
      "customDimension",
    };
    public static String[][] ourDefaults = {
      {},
      {"1"},
      {"1.0"},
      {"20dp"}
    };

    public String[] getDefault(String key) {
      for (int i = 0; i < ourPossibleAttr.length; i++) {
        if (key.equals(ourPossibleAttr[i])) {
          return (ourDefaults.length > i) ? ourDefaults[i] : ourDefaults[0];
        }
      }
      return ourDefaults[0];
    }

    public CustomCycleAttributes(KeyCycle frame) {
      parentKeyCycle = frame;
    }

    @Override
    public void parse(String name, String value) {
      myAttributes.put(name, value);
    }
  }
  /* ===========================CustomAttributes===================================*/

  public static class CustomAttributes implements AttributeParse {
    final KeyAttributes parentKeyAttributes;
    HashMap<String, Object> myAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "attributeName",
      "customColorValue",
      "customIntegerValue",
      "customFloatValue",
      "customDimension",
    };
    public static String[][] ourDefaults = {
      {},
      {"#FFF"},
      {"2"},
      {"1.0"},
      {"20dp"}
    };

    @Override
    public String toString() {
      return Arrays.toString(myAttributes.keySet().toArray());
    }

    public String[] getPossibleAttr() {
      return ourPossibleAttr;
    }

    public void deleteTag(NlModel model) {

    }

    enum Type {
      CUSTOM_COLOR,
      CUSTOM_INTEGER,
      CUSTOM_FLOAT,
      CUSTOM_STRING,
      CUSTOM_DIMENSION,
      CUSTOM_BOOLEAN
    }

    public CustomAttributes(KeyAttributes frame) {
      parentKeyAttributes = frame;
    }

    public HashMap<String, Object> getAttributes() {
      return myAttributes;
    }

    @Override
    public void parse(String name, String value) {
      myAttributes.put(name.substring(name.lastIndexOf(':') + 1), value);
    }
  }

  // =================================AttributeParse====================================== //

  interface AttributeParse {
    void parse(String name, String value);
  }

  // =================================ConstraintSet====================================== //

  static class ConstraintSet implements AttributeParse {
    String mId;

    void setId(String id) {
      mId = id.substring(id.indexOf('/') + 1);
    }

    ArrayList<ConstraintView> myConstraintViews = new ArrayList<>();

    @Override
    public void parse(String name, String value) {
      if ("android:id".equals(name)) {
        mId = value;
      }
    }
  }

  // =================================TransitionTag====================================== //
  public static class TransitionTag implements AttributeParse {
    private final String[] myPossibleAttr;
    MotionSceneModel myModel;
    String myConstraintSetEnd;
    String myConstraintSetStart;
    int duration;
    HashMap<String, Object> myAllAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "constraintSetStart",
      "constraintSetEnd",
      "keyFrame",
      "duration",
      "staggered"
    };

    public String[] getPossibleAttr() {
      return myPossibleAttr;
    }

    public HashMap<String, Object> getAttributes() {
      return myAllAttributes;
    }

    TransitionTag(MotionSceneModel model) {
      myModel = model;
      myPossibleAttr = ourPossibleAttr;
    }

    public ConstraintSet getConstraintSetEnd() {
      if (myConstraintSetEnd == null) {
        return null;
      }
      for (ConstraintSet set : myModel.myConstraintSets) {
        if (myConstraintSetEnd.equals(set.mId)) {
          return set;
        }
      }
      return null;
    }

    public ConstraintSet getConstraintSetStart() {
      if (myConstraintSetStart == null) {
        return null;
      }
      for (ConstraintSet set : myModel.myConstraintSets) {
        if (myConstraintSetStart.equals(set.mId)) {
          return set;
        }
      }
      return null;
    }

    @Override
    public void parse(String name, String value) {
      name = name.substring(name.lastIndexOf(':') + 1);
      myAllAttributes.put(name, value);
      if (name.endsWith(TransitionConstraintSetEnd)) {
        myConstraintSetEnd = value;
      }
      else if (name.endsWith(TransitionConstraintSetStart)) {
        myConstraintSetStart = value;
      }
      else if (name.endsWith(TransitionDuration)) {
        duration = Integer.parseInt(value);
      }
    }
  }

  // =================================OnSwipe====================================== //

  public static class OnSwipeTag implements AttributeParse {
    MotionSceneModel myModel;

    HashMap<String, Object> myAllAttributes = new HashMap<>();
    public static String[] ourPossibleAttr = {
      "maxVelocity",
      "maxAcceleration",
      "touchSide",
      "touchAnchorId",
      "touchAnchorSide"
    };
    public String[] myPossibleAttr = ourPossibleAttr;

    public String[] getPossibleAttr() {
      return myPossibleAttr;
    }

    public HashMap<String, Object> getAttributes() {
      return myAllAttributes;
    }

    OnSwipeTag(MotionSceneModel model) {
      myModel = model;
      myPossibleAttr = ourPossibleAttr;
    }

    @Override
    public void parse(String name, String value) {
      name = name.substring(name.lastIndexOf(':') + 1);
      myAllAttributes.put(name, value);
    }

    public void deletTag(NlModel model) {
    }
  }
  // =================================ConstraintView====================================== //

  static class ConstraintView implements AttributeParse {
    String mId;
    HashMap<String, Object> myAllAttributes = new HashMap<>();

    void setId(String id) {
      mId = id.substring(id.indexOf('/') + 1);
    }

    HashMap<String, String> myConstraintViews = new HashMap<>();

    @Override
    public void parse(String name, String value) {
      myAllAttributes.put(name, value);
    }
  }

  private static void parse(AttributeParse a, XmlAttribute[] attributes) {
    for (int i = 0; i < attributes.length; i++) {
      XmlAttribute attribute = attributes[i];
      parse(a, attribute.getName(), attribute.getValue());
    }
  }

  private static void parse(AttributeParse a, String name, String value) {
    a.parse(name, value);
  }

  public static MotionSceneModel parse(NlModel model,
                                       Project project,
                                       VirtualFile virtualFile,
                                       XmlFile file) {
    MotionSceneModel motionSceneModel = new MotionSceneModel();
    ArrayList<ConstraintSet> constraintSet = new ArrayList<>();

    motionSceneModel.myNlModel = model;
    motionSceneModel.myVirtualFile = virtualFile;
    motionSceneModel.myProject = project;
    // Process all the constraint sets

    XmlTag[] tagKeyFrames = file.getRootTag().findSubTags(MotionSceneConstraintSet);
    for (int i = 0; i < tagKeyFrames.length; i++) {
      XmlTag frame = tagKeyFrames[i];
      ConstraintSet set = new ConstraintSet();
      set.setId(frame.getAttributeValue("android:id"));
      parse(set, frame.getAttributes());
      constraintSet.add(set);
      XmlTag[] subTags = frame.getSubTags();
      for (int j = 0; j < subTags.length; j++) {
        XmlTag subtag = subTags[j];
        if (MotionSceneString.ConstraintSetConstrainView.equals(subtag.getName())) {
          ConstraintView view = new ConstraintView();
          view.setId(subtag.getAttributeValue("android:id"));
          motionSceneModel.addKeyFrame(view.mId);
          parse(view, subtag.getAttributes());
          set.myConstraintViews.add(view);
        }
      }
    }
    motionSceneModel.myConstraintSets = constraintSet;

    // process the Transition

    XmlTag[] transitionTags = file.getRootTag().findSubTags(MotionSceneTransition);
    if (transitionTags.length > 0) {
      motionSceneModel.myTransition = new ArrayList<>();
    }
    for (int i = 0; i < transitionTags.length; i++) {
      TransitionTag transition = new TransitionTag(motionSceneModel);
      XmlTag tag = transitionTags[i];
      parse(transition, tag.getAttributes());

      motionSceneModel.myTransition.add(transition);
    }
    // process the OnSwipe

    XmlTag[] onSwipeTags = file.getRootTag().findSubTags(MotionSceneOnSwipe);

    for (int i = 0; i < onSwipeTags.length; i++) {
      OnSwipeTag onSwipeTag = new OnSwipeTag(motionSceneModel);
      XmlTag tag = onSwipeTags[i];
      parse(onSwipeTag, tag.getAttributes());
      motionSceneModel.myOnSwipeTag = onSwipeTag;
    }

    // process all the key frames
    tagKeyFrames = file.getRootTag().findSubTags(MotionSceneKeyFrames);

    for (int i = 0; i < tagKeyFrames.length; i++) {
      XmlTag tagKeyFrame = tagKeyFrames[i];

      XmlTag[] tagkey = tagKeyFrame.getSubTags();

      for (int j = 0; j < tagkey.length; j++) {
        XmlTag xmlTag = tagkey[j];
        XmlTag[] customTags = xmlTag.getSubTags();
        String keyNodeName = xmlTag.getName();

        KeyFrame frame = null;
        if (KeyTypePositionPath.equals(keyNodeName)) {
          frame = new KeyPositionPath(motionSceneModel);
        }
        else if (KeyTypeAttributes.equals(keyNodeName)) {
          frame = new KeyAttributes(motionSceneModel);
          for (int k = 0; k < customTags.length; k++) {
            XmlTag tag = customTags[k];
            CustomAttributes custom = new CustomAttributes((KeyAttributes)frame);
            parse(custom, tag.getAttributes());
            ((KeyAttributes)frame).myCustomAttributes.add(custom);
          }
        }
        else if (KeyTypePositionCartesian.equals(keyNodeName)) {
          frame = new KeyPositionCartesian(motionSceneModel);
        }
        else if (KeyTypeCycle.equals(keyNodeName)) {
          frame = new KeyCycle(motionSceneModel);
          for (int k = 0; k < customTags.length; k++) {
            XmlTag tag = customTags[k];
            CustomCycleAttributes custom = new CustomCycleAttributes((KeyCycle)frame);
            parse(custom, tag.getAttributes());
            ((KeyCycle)frame).myCustomAttributes.add(custom);
          }
        }
        else {
          System.err.println("Unknown name :" + keyNodeName);
        }
        if (frame != null) {
          frame.parse(xmlTag.getAttributes());
          motionSceneModel.addKeyFrame(frame);
        }
      }
    }

    return motionSceneModel;
  }

  public static ArrayList<MotionSceneModel.KeyFrame> filterList(ArrayList<? extends MotionSceneModel.KeyFrame> keyList,
                                                                String name) {
    ArrayList<MotionSceneModel.KeyFrame> ret = new ArrayList<>();
    for (KeyFrame keyFrame : keyList) {
      if (keyFrame.myAttributes.containsKey(name)) {
        ret.add(keyFrame);
      }
    }
    return ret;
  }

  public static String[] getGraphAttributes(ArrayList<? extends MotionSceneModel.KeyFrame> keyList) {
    HashSet<String> set = new HashSet<>();
    for (KeyFrame frame : keyList) {
      if (frame instanceof KeyAttributes) {
        set.addAll(frame.myAttributes.keySet());
      }
      else if (frame instanceof KeyCycle) {
        set.addAll(frame.myAttributes.keySet());
      }
    }
    return set.toArray(new String[set.size()]);
  }

  void addKeyFrame(String id) {
    MotionSceneView motionView = mySceneViews.get(id);
    if (motionView == null) {
      motionView = new MotionSceneView();
      motionView.myModel = this;
      motionView.mid = id;
      mySceneViews.put(id, motionView);
    }
  }

  void addKeyFrame(KeyFrame frame) {
    String id = frame.target;
    MotionSceneView motionView = mySceneViews.get(id);
    if (motionView == null) {
      motionView = new MotionSceneView();
      motionView.myModel = this;
      motionView.mid = id;
      mySceneViews.put(id, motionView);
    }
    if (frame instanceof KeyAttributes) {
      motionView.myKeyAttributes.add((KeyAttributes)frame);
    }
    else if (frame instanceof KeyPosition) {
      motionView.myKeyPositions.add((KeyPosition)frame);
    }
    else if (frame instanceof KeyCycle) {
      motionView.myKeyCycles.add((KeyCycle)frame);
    }
  }
}
