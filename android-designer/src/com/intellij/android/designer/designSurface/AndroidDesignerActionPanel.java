/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.designSurface;

import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.ZoomType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import icons.AndroidDesignerIcons;

import javax.swing.*;
import java.awt.*;

public class AndroidDesignerActionPanel extends DesignerActionPanel {
  public AndroidDesignerActionPanel(DesignerEditorPanel designer, JComponent shortcuts) {
    super(designer, shortcuts);
  }

  @Override
  protected JComponent createToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // Create a layout where there are three toolbars:
    // +----------------------------------------------------------------------------+
    // | Normal toolbar, minus dynamic actions                                      |
    // +---------------------------------------------+------------------------------+
    // | Dynamic layout actions                      | Zoom actions and file status |
    // +---------------------------------------------+------------------------------+

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar(TOOLBAR, getActionGroup(), true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    panel.add(actionToolbar.getComponent(), BorderLayout.NORTH);

    ActionToolbar layoutToolBar = actionManager.createActionToolbar(TOOLBAR, getDynamicActionGroup(), true);
    ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, getRhsActions(), true);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(layoutToolBar.getComponent(), BorderLayout.WEST);
    bottom.add(zoomToolBar.getComponent(), BorderLayout.EAST);
    panel.add(bottom, BorderLayout.SOUTH);

    return panel;
  }

  @Override
  protected DefaultActionGroup createActionGroup() {
    // Like super implementation, but only adding the static group; the dynamic
    // actions are added to a separate toolbar
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(getActionGroup());
    return group;
  }

  @Override
  public void update() {
    // Overriding super; we don't want to dynamically hide or show the toolbar; it's there all the time
  }

  private ActionGroup getRhsActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction(null, "Zoom to Fit (0)", AndroidDesignerIcons.ZoomFit) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.FIT); }
    });
    group.add(new AnAction(null, "Reset Zoom to 100% (1)", AndroidDesignerIcons.Zoom100) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.ACTUAL); }
    });
    group.addSeparator();
    group.add(new AnAction(null, "Zoom In (+)", AndroidDesignerIcons.ZoomIn) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.IN); }
    });
    group.add(new AnAction(null, "Zoom Out (-)", AndroidDesignerIcons.ZoomOut) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.OUT); }
    });

    return group;
  }
}
