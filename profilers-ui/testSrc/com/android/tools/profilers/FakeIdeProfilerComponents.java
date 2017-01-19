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
package com.android.tools.profilers;

import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.LoadingPanel;
import com.android.tools.profilers.common.TabsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FakeIdeProfilerComponents implements IdeProfilerComponents {
  @NotNull
  private Map<JComponent, ComponentNavigations> myComponents = new HashMap<>();

  @Nullable
  @Override
  public JComponent getFileViewer(@Nullable File file) {
    return null;
  }

  @Nullable
  @Override
  public LoadingPanel createLoadingPanel() {
    return new LoadingPanel() {
      @NotNull
      @Override
      public JComponent getComponent() {
        return new JPanel(new BorderLayout());
      }

      @Override
      public void setLoadingText(@NotNull String loadingText) {
      }

      @Override
      public void startLoading() {
      }

      @Override
      public void stopLoading() {
      }
    };
  }

  @NotNull
  @Override
  public TabsPanel createTabsPanel() {
    return new TabsPanel() {
      private JTabbedPane myTabbedPane = new JTabbedPane();

      @NotNull
      @Override
      public JComponent getComponent() {
        return myTabbedPane;
      }

      @Override
      public void addTab(@NotNull String label, @NotNull JComponent content) {
        myTabbedPane.add(label, content);
      }

      @Override
      public void removeTab(@NotNull JComponent tab) {
        myTabbedPane.remove(tab);
      }

      @Override
      public void removeAll() {
        myTabbedPane.removeAll();
      }
    };
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier,
                                           @Nullable Runnable preNavigate) {
    assertFalse(myComponents.containsKey(component));
    myComponents.put(component, new ComponentNavigations(codeLocationSupplier, preNavigate));
  }

  @Nullable
  public Supplier<CodeLocation> getCodeLocationSupplier(@NotNull JComponent component) {
    assertTrue(myComponents.containsKey(component));
    return myComponents.get(component).myCodeLocationSupplier;
  }

  @Nullable
  public Runnable getPreNavigate(@NotNull JComponent component) {
    assertTrue(myComponents.containsKey(component));
    return myComponents.get(component).myPreNavigate;
  }

  private static class ComponentNavigations {
    @Nullable
    private Supplier<CodeLocation> myCodeLocationSupplier;

    @Nullable
    private Runnable myPreNavigate;

    private ComponentNavigations(@Nullable Supplier<CodeLocation> supplier, @Nullable Runnable navigate) {
      myCodeLocationSupplier = supplier;
      myPreNavigate = navigate;
    }
  }
}
