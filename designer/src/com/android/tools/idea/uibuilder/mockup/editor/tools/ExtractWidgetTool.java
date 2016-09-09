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
package com.android.tools.idea.uibuilder.mockup.editor.tools;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.mockup.editor.MockupViewPanel;
import com.android.tools.idea.uibuilder.mockup.editor.creators.WidgetCreator;
import com.android.tools.idea.uibuilder.mockup.editor.creators.WidgetCreatorFactory;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.JBColor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.android.SdkConstants.*;

/**
 * Tool Allowing the extraction of widget or layout from the current selection
 */
public class ExtractWidgetTool extends JPanel implements MockupEditor.Tool {

  /**
   * Here we define all the actions we want to display for the widget creation
   */
  private static ImmutableList<CreatorAction> ourWidgetCreationActions = new ImmutableList.Builder<CreatorAction>()
    .add(new CreatorAction(VIEW, "Create new widget from selection", AndroidIcons.Mockup.CreateWidget))
    .add(new CreatorAction(VIEW_INCLUDE, "Create new layout from selection", AndroidIcons.Mockup.CreateLayout))
    .add(new CreatorAction(IMAGE_VIEW, "Create new ImageView", AndroidIcons.Views.ImageView))
    .add(new CreatorAction(FLOATING_ACTION_BUTTON, "Create new FloatingActionButton", AndroidIcons.Views.FloatingActionButton))
    .add(new CreatorAction(TEXT_VIEW, "Create new TextView", AndroidIcons.Views.TextView))
    .build();

  public static final Logger LOGGER = Logger.getLogger(ExtractWidgetTool.class.getName());
  private final MockupViewPanel myMockupViewPanel;
  private final MockupEditor myMockupEditor;
  private final DesignSurface mySurface;
  private Rectangle mySelection;
  private MySelectionListener mySelectionListener;
  private float myAlpha = 0;

  /**
   * @param surface      Current designSurface holding the mockupEditor
   * @param mockupEditor
   */
  public ExtractWidgetTool(@NotNull DesignSurface surface, @NotNull MockupEditor mockupEditor) {
    super();
    mySurface = surface;
    myMockupViewPanel = mockupEditor.getMockupViewPanel();
    mySelectionListener = new MySelectionListener();
    myMockupEditor = mockupEditor;
    MockupEditor.MockupEditorListener mockupEditorListener = newMockup -> hideTooltipActions();
    mockupEditor.addListener(mockupEditorListener);
    setBorder(BorderFactory.createLineBorder(JBColor.background(), 1, true));
    add(createActionToolbar());
  }

  @Override
  public void paint(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    final Composite composite = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
    super.paint(g2d);
    g2d.setComposite(composite);
  }

  /**
   * Display the buttons of this tool inside the {@link MockupViewPanel} next to selection
   */
  private void displayTooltipActions() {
    myMockupViewPanel.removeAll();
    if (!mySelection.isEmpty()) {
      Timer timer = new Timer(20, e -> {
        float alpha = myAlpha;
        alpha += 0.1;
        if (alpha > 1) {
          alpha = 1;
          ((Timer)e.getSource()).setRepeats(false);
        }
        myAlpha = alpha;
        repaint();
      });
      timer.setRepeats(true);
      timer.setRepeats(true);
      timer.setCoalesce(true);
      timer.start();
      myMockupViewPanel.add(this);
      myMockupViewPanel.doLayout();
    }
  }

  /**
   * hide the buttons of this tool inside the {@link MockupViewPanel} next to selection
   */
  private void hideTooltipActions() {
    myMockupViewPanel.remove(this);
    myAlpha = 0;
  }

  /**
   * Create the Action toolbar containing the buttons created using ourWidgetCreationActions list
   *
   * @return The toolbar JComponent to add to the layout
   */
  private JComponent createActionToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup(createActionsButtons());
    final ActionToolbar actionToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.UNKNOWN, group, false);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    actionToolbar.setTargetComponent(this);
    return actionToolbar.getComponent();
  }

  /**
   * Activate only the selection layer in the mockup editor
   *
   * @param mockupEditor The {@link MockupEditor} on which the {@link MockupEditor.Tool} behave
   */
  @Override
  public void enable(@NotNull MockupEditor mockupEditor) {
    MockupViewPanel mockupViewPanel = mockupEditor.getMockupViewPanel();
    mockupViewPanel.addSelectionListener(mySelectionListener);
    mockupViewPanel.resetState();
  }

  /**
   * Disable the selection on the mockupEditor and hide any displayed actions
   *
   * @param mockupEditor The {@link MockupEditor} on which the {@link MockupEditor.Tool} behave
   */
  @Override
  public void disable(@NotNull MockupEditor mockupEditor) {
    MockupViewPanel mockupViewPanel = mockupEditor.getMockupViewPanel();
    mockupViewPanel.removeSelectionListener(mySelectionListener);
    hideTooltipActions();
  }

  /**
   * Create AnActions from ourWidgetCreationActions that use WidgetCreator to create the new widgets
   *
   * @return a List of {@link AnAction}
   */
  private List<AnAction> createActionsButtons() {
    List<AnAction> actions = new ArrayList<>(ourWidgetCreationActions.size());
    for (CreatorAction creatorAction : ourWidgetCreationActions) {
      actions.add(new AnAction(creatorAction.myTitle, creatorAction.myTitle, creatorAction.myIcon) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          Mockup mockup = myMockupEditor.getMockup();
          ScreenView currentScreenView = mySurface.getCurrentScreenView();
          if (mockup == null) {
            myMockupEditor.showError("Cannot create a widget from an empty mockup");
            LOGGER.warning("MockupEditor has no associated mockup");
            return;
          }
          if (currentScreenView == null) {
            myMockupEditor.showError("The designer is not ready to create a new widget");
            LOGGER.warning("The DesignSurface does not have a current screen view");
            return;
          }
          WidgetCreator creator = WidgetCreatorFactory.create(
            creatorAction.myAndroidClassName, mockup, currentScreenView.getModel(), currentScreenView, mySelection);

          if (creator.hasOptionsComponent()) {
            setEnabled(false);
            final JComponent optionsComponent = creator.getOptionsComponent(
              type -> { // Done
                setEnabled(true);
                myMockupViewPanel.removeAll();
                if (WidgetCreator.DoneCallback.FINISH == type) {
                  creator.addToModel();
                }
              }
            );
            if (optionsComponent != null) {
              optionsComponent.setEnabled(true);
              myMockupViewPanel.add(optionsComponent);
              myMockupViewPanel.doLayout();
            }
          }
          else {
            creator.addToModel();
          }
        }
      });
    }
    return actions;
  }

  /**
   * Selection listener that hide the actions when the selection begin and show them when it end
   * and is no empty
   */
  private class MySelectionListener implements MockupViewPanel.SelectionListener {

    @Override
    public void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y) {
      hideTooltipActions();
    }

    @Override
    public void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection) {
      mySelection = selection;
      final Mockup mockup = myMockupEditor.getMockup();
      if (mockup != null &&
          mockup.getComponent().isOrHasSuperclass(CLASS_VIEWGROUP)) {
        displayTooltipActions();
      }
    }
  }

  private static class CreatorAction {
    String myAndroidClassName;
    String myTitle;
    Icon myIcon;

    public CreatorAction(String androidClassName, String title, Icon icon) {
      myAndroidClassName = androidClassName;
      myTitle = title;
      myIcon = icon;
    }
  }
}
