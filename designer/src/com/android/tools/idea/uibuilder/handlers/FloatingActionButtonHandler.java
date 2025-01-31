/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.SdkConstants.APP_BAR_LAYOUT;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.ATTR_BACKGROUND_TINT;
import static com.android.SdkConstants.ATTR_BACKGROUND_TINT_MODE;
import static com.android.SdkConstants.ATTR_BORDER_WIDTH;
import static com.android.SdkConstants.ATTR_COMPAT_PADDING;
import static com.android.SdkConstants.ATTR_ELEVATION;
import static com.android.SdkConstants.ATTR_FAB_CUSTOM_SIZE;
import static com.android.SdkConstants.ATTR_FAB_SIZE;
import static com.android.SdkConstants.ATTR_HIDE_MOTION_SPEC;
import static com.android.SdkConstants.ATTR_HOVERED_FOCUSED_TRANSLATION_Z;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_MAX_IMAGE_SIZE;
import static com.android.SdkConstants.ATTR_PRESSED_TRANSLATION_Z;
import static com.android.SdkConstants.ATTR_RIPPLE_COLOR;
import static com.android.SdkConstants.ATTR_SHOW_MOTION_SPEC;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_TINT;
import static com.android.SdkConstants.COORDINATOR_LAYOUT;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for the {@code <android.support.design.widget.FloatingActionButton>} widget.
 */
public class FloatingActionButtonHandler extends ImageViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
      ATTR_STYLE,
      ATTR_BACKGROUND_TINT,
      ATTR_BACKGROUND_TINT_MODE,
      ATTR_RIPPLE_COLOR,
      ATTR_TINT,
      ATTR_FAB_SIZE,
      ATTR_FAB_CUSTOM_SIZE,
      ATTR_ELEVATION,
      ATTR_HOVERED_FOCUSED_TRANSLATION_Z,
      ATTR_PRESSED_TRANSLATION_Z,
      ATTR_BORDER_WIDTH,
      ATTR_COMPAT_PADDING,
      ATTR_MAX_IMAGE_SIZE,
      ATTR_SHOW_MOTION_SPEC,
      ATTR_HIDE_MOTION_SPEC);
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    XmlBuilder builder = new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_SRC, getSampleImageSrc())
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .androidAttribute("clickable", true);

    if (xmlType.equals(XmlType.PREVIEW_ON_PALETTE)) {
      builder.attribute(APP_PREFIX, "elevation", "0dp");
    }

    return builder
      .endTag(tagName)
      .toString();
  }

  @Override
  @NotNull
  public String getSampleImageSrc() {
    // Builtin graphics available since v1:
    return "@android:drawable/ic_input_add"; //$NON-NLS-1$
  }

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    return 0.8;
  }

  @Override
  public boolean acceptsParent(@NotNull NlComponent layout, @NotNull NlComponent newChild) {
    NlComponent appBar = getAppBar(layout);
    if (appBar == null) {
      return super.acceptsParent(layout, newChild);
    }
    return layout == appBar.getParent();
  }

  @Nullable
  private static NlComponent getAppBar(@NotNull NlComponent component) {
    NlComponent parent = component.getParent();
    while (parent != null) {
      component = parent;
      parent = component.getParent();
    }
    if (!COORDINATOR_LAYOUT.isEquals(component.getTagName())) {
      return null;
    }
    for (NlComponent child : component.getChildren()) {
      if (APP_BAR_LAYOUT.isEquals(child.getTagName())) {
        return child;
      }
    }
    return null;
  }
}
