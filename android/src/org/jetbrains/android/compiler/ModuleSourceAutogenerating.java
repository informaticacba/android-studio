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
package org.jetbrains.android.compiler;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleSourceAutogenerating {
  private final Set<AndroidAutogeneratorMode> myDirtyModes = EnumSet.noneOf(AndroidAutogeneratorMode.class);
  private Sdk myPrevSdk;

  @GuardedBy("myAutogeneratedFiles")
  private final Map<AndroidAutogeneratorMode, Set<String>> myAutogeneratedFiles = new HashMap<>();

  @Nullable
  public static ModuleSourceAutogenerating getInstance(@NotNull AndroidFacet facet) {
    return facet.getModule().getService(ModuleSourceAutogenerating.class);
  }

  private ModuleSourceAutogenerating(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new IllegalArgumentException(module.getName() + " is not an Android module");
    }

    if (!requiresAutoSourceGeneration(facet)) {
      throw new IllegalArgumentException(
        module.getName() + " is built by an external build system and should not require the IDE to generate sources");
    }

    scheduleSourceRegenerating(AndroidAutogeneratorMode.AIDL);
    scheduleSourceRegenerating(AndroidAutogeneratorMode.RENDERSCRIPT);
    scheduleSourceRegenerating(AndroidAutogeneratorMode.BUILDCONFIG);

    myPrevSdk = ModuleRootManager.getInstance(module).getSdk();
    module.getMessageBus().connect(facet).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        Sdk newSdk = ModuleRootManager.getInstance(module).getSdk();
        if (newSdk != null && newSdk.getSdkType() instanceof AndroidSdkType && !newSdk.equals(myPrevSdk)) {
          myPrevSdk = newSdk;
          resetRegeneratingState();
        }
      }
    });
  }

  public static boolean requiresAutoSourceGeneration(@NotNull AndroidFacet facet) {
    return !AndroidModel.isRequired(facet) && ApkFacet.getInstance(facet.getModule()) == null;
  }

  public boolean isGeneratedFileRemoved(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      Set<String> filePaths = myAutogeneratedFiles.get(mode);

      if (filePaths != null) {
        for (String path : filePaths) {
          VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);

          if (file == null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void clearAutogeneratedFiles(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);
      if (set != null) {
        set.clear();
      }
    }
  }

  public void markFileAutogenerated(@NotNull AndroidAutogeneratorMode mode, @NotNull VirtualFile file) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);

      if (set == null) {
        set = new HashSet<>();
        myAutogeneratedFiles.put(mode, set);
      }
      set.add(file.getPath());
    }
  }

  @NotNull
  public Set<String> getAutogeneratedFiles(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);
      return set != null ? new HashSet<>(set) : Collections.emptySet();
    }
  }

  public void scheduleSourceRegenerating(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myDirtyModes) {
      myDirtyModes.add(mode);
    }
  }

  public boolean cleanRegeneratingState(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myDirtyModes) {
      return myDirtyModes.remove(mode);
    }
  }

  public void resetRegeneratingState() {
    synchronized (myDirtyModes) {
      Collections.addAll(myDirtyModes, AndroidAutogeneratorMode.values());
    }
  }
}
