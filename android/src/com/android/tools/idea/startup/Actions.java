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
package com.android.tools.idea.startup;

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;

public final class Actions {
  private Actions() {
  }

  public static void hideAction(@NotNull String actionId) {
    AnAction oldAction = ActionManager.getInstance().getAction(actionId);
    if (oldAction != null) {
      replaceAction(actionId, new EmptyAction());
    }
  }

  public static void replaceAction(@NotNull String actionId, @NotNull AnAction newAction) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      actionManager.replaceAction(actionId, newAction);
    }
    else {
      actionManager.registerAction(actionId, newAction);
    }
  }

  public static void moveAction(@NotNull String actionId, @NotNull String oldGroupId, @NotNull String groupId, @NotNull Constraints constraints, @NotNull ActionManager actionManager) {
    AnAction action = actionManager.getActionOrStub(actionId);
    AnAction group = actionManager.getAction(groupId);
    AnAction oldGroup = actionManager.getAction(oldGroupId);
    if (action != null && oldGroup instanceof DefaultActionGroup && group instanceof DefaultActionGroup) {
      ((DefaultActionGroup)oldGroup).remove(action, actionManager);
      ((DefaultActionGroup)group).add(action, constraints, actionManager);
    }
  }
}
