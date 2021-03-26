// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.exportSignedPackage;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "GenerateSignedApkSettings",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class GenerateSignedApkSettings implements PersistentStateComponent<GenerateSignedApkSettings> {
  public boolean EXPORT_PRIVATE_KEY = true;
  public String KEY_STORE_PATH = "";
  public String KEY_ALIAS = "";
  public boolean REMEMBER_PASSWORDS = false;
  public String BUILD_TARGET_KEY = ExportSignedPackageWizard.BUNDLE;

  @Override
  public GenerateSignedApkSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GenerateSignedApkSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static GenerateSignedApkSettings getInstance(final Project project) {
    return project.getService(GenerateSignedApkSettings.class);
  }
}
