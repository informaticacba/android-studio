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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.VectorDrawablesOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.VectorDrawablesOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS_BLOCK_NAME;

public final class ProductFlavorModelImpl extends FlavorTypeModelImpl implements ProductFlavorModel {
  @NonNls private static final String APPLICATION_ID = "applicationId";
  @NonNls private static final String DIMENSION = "dimension";
  @NonNls private static final String MAX_SDK_VERSION = "maxSdkVersion";
  @NonNls private static final String MIN_SDK_VERSION = "minSdkVersion";
  @NonNls private static final String RENDER_SCRIPT_TARGET_API = "renderscriptTargetApi";
  @NonNls private static final String RENDER_SCRIPT_SUPPORT_MODE_ENABLED = "renderscriptSupportModeEnabled";
  @NonNls private static final String RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED = "renderscriptSupportModeBlasEnabled";
  @NonNls private static final String RENDER_SCRIPT_NDK_MODE_ENABLED = "renderscriptNdkModeEnabled";
  @NonNls private static final String RES_CONFIGS = "resConfigs";
  @NonNls private static final String TARGET_SDK_VERSION = "targetSdkVersion";
  @NonNls private static final String TEST_APPLICATION_ID = "testApplicationId";
  @NonNls private static final String TEST_FUNCTIONAL_TEST = "testFunctionalTest";
  @NonNls private static final String TEST_HANDLE_PROFILING = "testHandleProfiling";
  @NonNls private static final String TEST_INSTRUMENTATION_RUNNER = "testInstrumentationRunner";
  @NonNls private static final String TEST_INSTRUMENTATION_RUNNER_ARGUMENTS = "testInstrumentationRunnerArguments";
  @NonNls private static final String VERSION_CODE = "versionCode";
  @NonNls private static final String VERSION_NAME = "versionName";
  @NonNls private static final String WEAR_APP_UNBUNDLED = "wearAppUnbundled";

  public ProductFlavorModelImpl(@NotNull ProductFlavorDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel applicationId() {
    return getModelForProperty(APPLICATION_ID);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel dimension() {
    return getModelForProperty(DIMENSION);
  }

  @Override
  @NotNull
  public ExternalNativeBuildOptionsModel externalNativeBuild() {
    ExternalNativeBuildOptionsDslElement externalNativeBuildOptionsDslElement =
      myDslElement.getPropertyElement(EXTERNAL_NATIVE_BUILD_BLOCK_NAME,
                                      ExternalNativeBuildOptionsDslElement.class);
    if (externalNativeBuildOptionsDslElement == null) {
      externalNativeBuildOptionsDslElement = new ExternalNativeBuildOptionsDslElement(myDslElement);
      myDslElement.setNewElement(externalNativeBuildOptionsDslElement);
    }
    return new ExternalNativeBuildOptionsModelImpl(externalNativeBuildOptionsDslElement);
  }

  @Override
  public void removeExternalNativeBuild() {
    myDslElement.removeProperty(EXTERNAL_NATIVE_BUILD_BLOCK_NAME);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel maxSdkVersion() {
    return getModelForProperty(MAX_SDK_VERSION);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel minSdkVersion() {
    return getModelForProperty(MIN_SDK_VERSION);
  }

  @Override
  @NotNull
  public NdkOptionsModel ndk() {
    NdkOptionsDslElement ndkOptionsDslElement = myDslElement.getPropertyElement(NDK_BLOCK_NAME, NdkOptionsDslElement.class);
    if (ndkOptionsDslElement == null) {
      ndkOptionsDslElement = new NdkOptionsDslElement(myDslElement);
      myDslElement.setNewElement(ndkOptionsDslElement);
    }
    return new NdkOptionsModelImpl(ndkOptionsDslElement);
  }

  @Override
  public void removeNdk() {
    myDslElement.removeProperty(NDK_BLOCK_NAME);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel resConfigs() {
    return getModelForProperty(RES_CONFIGS);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptTargetApi() {
    return getModelForProperty(RENDER_SCRIPT_TARGET_API);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptSupportModeEnabled() {
    return getModelForProperty(RENDER_SCRIPT_SUPPORT_MODE_ENABLED);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptSupportModelBlasEnabled() {
    return getModelForProperty(RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptNdkModeEnabled() {
    return getModelForProperty(RENDER_SCRIPT_NDK_MODE_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel targetSdkVersion() {
    return getModelForProperty(TARGET_SDK_VERSION);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testApplicationId() {
    return getModelForProperty(TEST_APPLICATION_ID);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testFunctionalTest() {
    return getModelForProperty(TEST_FUNCTIONAL_TEST);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testHandleProfiling() {
    return getModelForProperty(TEST_HANDLE_PROFILING);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testInstrumentationRunner() {
    return getModelForProperty(TEST_INSTRUMENTATION_RUNNER);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testInstrumentationRunnerArguments() {
    return getModelForProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionCode() {
    return getModelForProperty(VERSION_CODE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionName() {
    return getModelForProperty(VERSION_NAME);
  }

  @NotNull
  @Override
  public VectorDrawablesOptionsModel vectorDrawables() {
    VectorDrawablesOptionsDslElement vectorDrawableElement = myDslElement.getPropertyElement(VECTOR_DRAWABLES_OPTIONS_BLOCK_NAME, VectorDrawablesOptionsDslElement.class);
    if (vectorDrawableElement == null) {
      vectorDrawableElement = new VectorDrawablesOptionsDslElement(myDslElement);
      myDslElement.setNewElement(vectorDrawableElement);
    }
    return new VectorDrawablesOptionsModelImpl(vectorDrawableElement);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel wearAppUnbundled() {
    return getModelForProperty(WEAR_APP_UNBUNDLED);
  }
}
