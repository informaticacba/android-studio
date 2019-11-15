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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.CMakeModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.NdkBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.NdkBuildModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement.CMAKE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement.NDK_BUILD_BLOCK_NAME;

public class ExternalNativeBuildModelImpl extends GradleDslBlockModel implements ExternalNativeBuildModel {
  public ExternalNativeBuildModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  @Override
  public CMakeModel cmake() {
    CMakeDslElement cMakeDslElement = myDslElement.getPropertyElement(CMAKE_BLOCK_NAME, CMakeDslElement.class);
    if (cMakeDslElement == null) {
      cMakeDslElement = new CMakeDslElement(myDslElement);
      myDslElement.setNewElement(cMakeDslElement);
    }
    return new CMakeModelImpl(cMakeDslElement);
  }

  @NotNull
  @Override
  public ExternalNativeBuildModel removeCMake() {
    myDslElement.removeProperty(CMAKE_BLOCK_NAME);
    return this;
  }

  @NotNull
  @Override
  public NdkBuildModel ndkBuild() {
    NdkBuildDslElement ndkBuildDslElement = myDslElement.getPropertyElement(NDK_BUILD_BLOCK_NAME, NdkBuildDslElement.class);
    if (ndkBuildDslElement == null) {
      ndkBuildDslElement = new NdkBuildDslElement(myDslElement);
      myDslElement.setNewElement(ndkBuildDslElement);
    }
    return new NdkBuildModelImpl(ndkBuildDslElement);
  }

  @NotNull
  @Override
  public ExternalNativeBuildModel removeNdkBuild() {
    myDslElement.removeProperty(NDK_BUILD_BLOCK_NAME);
    return this;
  }
}
