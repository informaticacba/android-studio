/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.preference;

import static com.android.SdkConstants.ATTR_TITLE;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_DEFAULT_VALUE;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_DEPENDENCY;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_KEY;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_RINGTONE_TYPE;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_SHOW_DEFAULT;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_SHOW_SILENT;
import static com.android.SdkConstants.PreferenceAttributes.ATTR_SUMMARY;
import static com.android.SdkConstants.PreferenceTags.RINGTONE_PREFERENCE;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RingtonePreferenceHandler extends PreferenceHandler {
  @Language("XML")
  @NotNull
  @Override
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_DEFAULT_VALUE, "")
          .androidAttribute(ATTR_TITLE, "Ringtone preference")
          .endTag(tagName)
          .toString();
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return NO_PREVIEW;
      default:
        throw new AssertionError(xmlType);
    }
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_RINGTONE_TYPE,
      ATTR_KEY,
      ATTR_TITLE,
      ATTR_SUMMARY,
      ATTR_DEPENDENCY,
      ATTR_SHOW_DEFAULT,
      ATTR_SHOW_SILENT);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    if (!super.onCreate(editor, parent, newChild, type)) {
      return false;
    }

    NlWriteCommandActionUtil.run(newChild, "Set RingtonePreference", () -> {
      newChild.setAndroidAttribute(ATTR_KEY, generateKey(newChild, RINGTONE_PREFERENCE, "ringtone_preference_"));
    });
    return true;
  }
}
