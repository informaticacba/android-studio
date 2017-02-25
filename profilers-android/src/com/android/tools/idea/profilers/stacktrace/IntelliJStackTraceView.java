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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.StackTraceModel;
import com.android.tools.profilers.common.StackTraceView;
import com.android.tools.profilers.common.ThreadId;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.BiFunction;

import static com.intellij.ui.SimpleTextAttributes.*;

public class IntelliJStackTraceView extends AspectObserver implements StackTraceView {
  @NotNull
  private final Project myProject;

  @NotNull
  private final StackTraceModel myModel;

  @NotNull
  private final BiFunction<Project, CodeLocation, StackNavigation> myGenerator;

  @NotNull
  private final JBScrollPane myScrollPane;

  @NotNull
  private final DefaultListModel<StackElement> myListModel;

  @NotNull
  private final JBList myListView;

  public IntelliJStackTraceView(@NotNull Project project, @NotNull StackTraceModel model) {
    this(project, model, IntelliJStackNavigation::new);
  }

  @VisibleForTesting
  IntelliJStackTraceView(@NotNull Project project,
                         @NotNull StackTraceModel model,
                         @NotNull BiFunction<Project, CodeLocation, StackNavigation> stackNavigationGenerator) {
    myProject = project;
    myModel = model;
    myGenerator = stackNavigationGenerator;
    myListModel = new DefaultListModel<>();
    myListView = new JBList(myListModel);
    myListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myListView.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myListView.setCellRenderer(new StackElementRenderer());
    myScrollPane = new JBScrollPane(myListView);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    myListView.addListSelectionListener(e -> {
      int index = myListView.getSelectedIndex();
      if (index < 0 || index >= myListView.getItemsCount() || myListView.getItemsCount() == 0) {
        myModel.clearSelection();
        return;
      }

      StackElement element = myListModel.getElementAt(index);
      element.navigate();
      myModel.setSelectedIndex(index);
    });

    myListView.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          myListView.clearSelection();
          e.consume();
        }
      }
    });


    myModel.addDependency(this).
      onChange(StackTraceModel.Aspect.STACK_FRAMES, () -> {
        List<CodeLocation> stackFrames = myModel.getCodeLocations();
        myListModel.removeAllElements();
        myListView.clearSelection();
        stackFrames.forEach(stackFrame -> myListModel.addElement(myGenerator.apply(myProject, stackFrame)));

        ThreadId threadId = myModel.getThreadId();
        if (!threadId.equals(ThreadId.INVALID_THREAD_ID)) {
          myListModel.addElement(new ThreadElement(threadId));
        }
      })
      .onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> {
        int index = myModel.getSelectedIndex();
        if (myModel.getSelectedType() == StackTraceModel.Type.INVALID) {
          if (myListView.getSelectedIndex() != -1) {
            myListView.clearSelection();
          }
        }
        else if (index >= 0 && index < myListView.getItemsCount()) {
          if (myListView.getSelectedIndex() != index) {
            myListView.setSelectedIndex(index);
          }
        }
        else {
          throw new IndexOutOfBoundsException(
            "View has " + myListView.getItemsCount() + " elements while aspect is changing to index " + index);
        }
      });
  }

  @NotNull
  @Override
  public StackTraceModel getModel() {
    return myModel;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myScrollPane;
  }

  @VisibleForTesting
  @NotNull
  JBList getListView() {
    return myListView;
  }

  private static class StackElementRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         Object value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      // Fix GTK background
      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }

      if (value == null) {
        return;
      }

      if (value instanceof StackNavigation) {
        renderStackNavigation((StackNavigation)value, selected);
      }
      else if (value instanceof ThreadElement) {
        renderThreadElement((ThreadElement)value, selected);
      }
      else {
        append(value.toString(), ERROR_ATTRIBUTES);
      }
    }

    private void renderStackNavigation(@NotNull StackNavigation navigation, boolean selected) {
      setIcon(PlatformIcons.METHOD_ICON);
      SimpleTextAttributes textAttribute = selected || navigation.isInUserCode() ? REGULAR_ATTRIBUTES : GRAY_ATTRIBUTES;
      CodeLocation location = navigation.getCodeLocation();
      append(navigation.getMethodName(), textAttribute, navigation.getMethodName());
      String lineNumberText = ":" + Integer.toString(location.getLineNumber() + 1) + ", ";
      append(lineNumberText, textAttribute, lineNumberText);
      append(navigation.getSimpleClassName(), textAttribute, navigation.getSimpleClassName());
      String packageName = " (" + navigation.getPackageName() + ")";
      append(packageName, selected ? REGULAR_ITALIC_ATTRIBUTES : GRAYED_ITALIC_ATTRIBUTES, packageName);
    }

    private void renderThreadElement(@NotNull ThreadElement threadElement, boolean selected) {
      setIcon(AllIcons.Debugger.ThreadSuspended);
      String text = threadElement.getThreadId().toString();
      append(text, selected ? REGULAR_ATTRIBUTES : GRAY_ATTRIBUTES, text);
    }
  }
}
