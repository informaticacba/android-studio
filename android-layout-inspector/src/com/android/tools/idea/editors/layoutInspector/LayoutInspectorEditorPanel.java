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
package com.android.tools.idea.editors.layoutInspector;

import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.layoutInspector.ui.LayoutInspectorPanel;
import com.android.tools.idea.editors.layoutInspector.ui.LayoutTreeDefinition;
import com.android.tools.idea.editors.layoutInspector.ui.PropertiesDefinition;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutInspectorEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class LayoutInspectorEditorPanel extends WorkBench<LayoutInspectorContext> {
  public LayoutInspectorEditorPanel(@NotNull LayoutInspectorEditor editor,
                                    @NotNull Project project,
                                    @NotNull LayoutInspectorContext context) {
    super(project, "Layout Inspector", editor, (Disposable)editor);

    List<ToolWindowDefinition<LayoutInspectorContext>> tools = new ArrayList<>(2);
    tools.add(new LayoutTreeDefinition(Side.LEFT, Split.TOP, AutoHide.DOCKED));
    tools.add(new PropertiesDefinition(Side.RIGHT, Split.TOP, AutoHide.DOCKED));

    init(new LayoutInspectorPanel(context), context, tools, false);

    UsageTracker.log(UsageTrackerUtils.withProjectId(
       AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.LAYOUT_INSPECTOR_EVENT)
        .setLayoutInspectorEvent(LayoutInspectorEvent.newBuilder()
          .setType(LayoutInspectorEvent.LayoutInspectorEventType.OPEN)),
       project));
  }
}
