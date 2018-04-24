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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A mechanism for layout out screens in the navigation editor.
 */
public interface NavSceneLayoutAlgorithm {
  /**
   * @param component The component to position
   * @return True if the component was positioned, false if we should try other layout algorithms
   */
  boolean layout(@NotNull SceneComponent component);

  /**
   * @return true if this algorithm can persist components' positions
   */
  default boolean canSave() {
    return false;
  }

  /**
   * Persist the component's position.
   */
  default void save(@NotNull SceneComponent component) {
    throw new UnsupportedOperationException();
  }

  default void restorePositionData(@NotNull SceneComponent component, @NotNull Object position) {
    throw new UnsupportedOperationException();
  }
  default Object getPositionData(@NotNull SceneComponent component) {
    throw new UnsupportedOperationException();
  }
}
