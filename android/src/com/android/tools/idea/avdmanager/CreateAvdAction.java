/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.icons.AllIcons;
import java.awt.event.ActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Invoke the wizard to create a new AVD
 */
public class CreateAvdAction extends AvdUiAction {

  public CreateAvdAction(@NotNull AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Create Virtual Device...", "Create a new Android Virtual Device", AllIcons.General.Add);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdOptionsModel avdOptionsModel = new AvdOptionsModel(null);
    ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(myAvdInfoProvider.getAvdProviderComponent(), getProject(), avdOptionsModel);
    if (dialog.showAndGet()) {
      // Select the AVD that we just created
      refreshAvdsAndSelect(avdOptionsModel.getCreatedAvd());
    }
  }
}