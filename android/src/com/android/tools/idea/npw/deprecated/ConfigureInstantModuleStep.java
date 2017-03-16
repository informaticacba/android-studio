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
package com.android.tools.idea.npw.deprecated;

import com.android.tools.adtui.LabelWithEditLink;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.FormFactorUtils;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.*;

/**
 * This class configures Instant App specific data such as the name of the atom to be created and the path to assign to the default Activity
 */
@Deprecated
public final class ConfigureInstantModuleStep extends DynamicWizardStepWithDescription {
  @NotNull private final FormFactor myFormFactor;
  private JTextField mySplitNameField;
  private JPanel myPanel;
  private JTextField myFeatureNameField;
  private LabelWithEditLink myPackageName;

  public ConfigureInstantModuleStep(@Nullable Disposable parentDisposable, @NotNull FormFactor formFactor) {
    super(parentDisposable);
    setBodyComponent(myPanel);
    myFormFactor = formFactor;
  }

  @Override
  public void init() {
    super.init();
    // TODO: put descriptions in AndroidBundle once UI is finalised
    setControlDescription(myFeatureNameField, "Name of the feature to add to the project");
    setControlDescription(mySplitNameField, "Name of the feature module which will be added to the project");
    setControlDescription(myPackageName, "Package which will contain all feature splits in this instant app");

    ScopedStateStore.Key<String> moduleNameKey = FormFactorUtils.getModuleNameKey(myFormFactor);
    myState.put(moduleNameKey, null);

    register(FEATURE_NAME_KEY, myFeatureNameField);
    register(moduleNameKey, mySplitNameField);
    register(SPLIT_NAME_KEY, mySplitNameField);
    register(INSTANT_APP_PACKAGE_NAME_KEY, myPackageName);
    myState.put(FEATURE_NAME_KEY, "Feature");
    myState.put(moduleNameKey, "feature");

    registerValueDeriver(moduleNameKey, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
        return makeSetOf(FEATURE_NAME_KEY);
      }

      @Nullable
      @Override
      public String deriveValue(@NotNull ScopedStateStore state, @Nullable ScopedStateStore.Key changedKey, @Nullable String currentValue) {
        return WizardUtils.computeModuleName(state.getNotNull(FEATURE_NAME_KEY, "Feature"), getProject());
      }
    });

    myState.put(INSTANT_APP_PACKAGE_NAME_KEY, myState.get(PACKAGE_NAME_KEY) + ".instantapp");
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Customize Instant App Support";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Customize Instant App Support";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFeatureNameField;
  }

  @Override
  public boolean isStepVisible() {
    return myState.getNotNull(IS_INSTANT_APP_KEY, false) && getProject() == null;
  }
}
