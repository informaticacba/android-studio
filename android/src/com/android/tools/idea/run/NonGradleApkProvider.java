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
package com.android.tools.idea.run;

import static com.android.tools.idea.run.NonGradleApplicationIdProvider.computePackageName;

import com.android.ddmlib.IDevice;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A provider of APK information for run configurations in non-Gradle projects.
 */
public class NonGradleApkProvider implements ApkProvider {
  @NotNull
  private final AndroidFacet myFacet;
  @NotNull
  private final ApplicationIdProvider myApplicationIdProvider;
  @Nullable
  private final String myArtifactName;

  public NonGradleApkProvider(@NotNull AndroidFacet facet,
                              @NotNull ApplicationIdProvider applicationIdProvider,
                              @Nullable String artifactName) {
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myArtifactName = artifactName;
  }

  @Override
  @NotNull
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    String packageName = myApplicationIdProvider.getPackageName();
    // Gather up all the dependency APKs to install, and check that none conflict.
    HashMap<AndroidFacet, String> depFacet2PackageName = new HashMap<>();
    fillRuntimeAndTestDependencies(myFacet.getModule(), depFacet2PackageName);
    checkPackageNames(depFacet2PackageName, myFacet, packageName);

    List<ApkInfo> apkList = new ArrayList<>();
    addApk(apkList, packageName, myFacet);

    for (AndroidFacet depFacet : depFacet2PackageName.keySet()) {
      addApk(apkList, depFacet2PackageName.get(depFacet), depFacet);
    }
    return apkList;
  }

  private void addApk(@NotNull List<ApkInfo> apkList, @NotNull String packageName, @NotNull AndroidFacet facet)
    throws ApkProvisionException {
    final Module module = facet.getModule();
    String localPath;

    if (myArtifactName != null && !myArtifactName.isEmpty()) {
      localPath = NonGradleAndroidArtifactResolver.getInstance().getModuleApkPathByArtifactName(module, myArtifactName);
    } else {
      localPath = AndroidCompileUtil.getUnsignedApkPath(facet);
    }
    if (localPath == null) {
      throw new ApkProvisionException("ERROR: APK path is not specified for module \"" + module.getName() + '"');
    }

    apkList.add(new ApkInfo(new File(localPath), packageName));
  }

  private static void fillRuntimeAndTestDependencies(@NotNull Module module, @NotNull Map<AndroidFacet, String> module2PackageName)
    throws ApkProvisionException {
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
          if (depFacet != null &&
              !module2PackageName.containsKey(depFacet) &&
              depFacet.getConfiguration().isAppProject()) {
            String packageName = computePackageName(depFacet);
            module2PackageName.put(depFacet, packageName);
            fillRuntimeAndTestDependencies(depModule, module2PackageName);
          }
        }
      }
    }
  }

  private static void checkPackageNames(@NotNull Map<AndroidFacet, String> additionalFacet2PackageName,
                                           @NotNull AndroidFacet facet,
                                           @NotNull String mainPackageName) throws ApkProvisionException {
    final Map<String, List<String>> packageName2ModuleNames = new HashMap<>();
    packageName2ModuleNames.put(
      mainPackageName, new ArrayList<>(Collections.singletonList(facet.getModule().getName())));

    for (Map.Entry<AndroidFacet, String> entry : additionalFacet2PackageName.entrySet()) {
      final String moduleName = entry.getKey().getModule().getName();
      final String packageName = entry.getValue();
      List<String> list = packageName2ModuleNames.get(packageName);

      if (list == null) {
        list = new ArrayList<>();
        packageName2ModuleNames.put(packageName, list);
      }
      list.add(moduleName);
    }

    final StringBuilder messageBuilder = new StringBuilder("Applications have the same package name ");
    boolean fail = false;
    for (Map.Entry<String, List<String>> entry : packageName2ModuleNames.entrySet()) {
      final String packageName = entry.getKey();
      final List<String> moduleNames = entry.getValue();

      if (moduleNames.size() > 1) {
        fail = true;
        messageBuilder.append(packageName).append(":\n    ");

        for (Iterator<String> it = moduleNames.iterator(); it.hasNext(); ) {
          String moduleName = it.next();
          messageBuilder.append(moduleName);
          if (it.hasNext()) {
            messageBuilder.append(", ");
          }
        }
        messageBuilder.append("\n");
      }
    }
    if (fail) {
      throw new ApkProvisionException(messageBuilder.toString());
    }
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    return ImmutableList.of();
  }
}
