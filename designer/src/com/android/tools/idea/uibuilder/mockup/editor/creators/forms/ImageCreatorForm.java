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
package com.android.tools.idea.uibuilder.mockup.editor.creators.forms;

import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

/**
 * From to display options for {@link com.android.tools.idea.uibuilder.mockup.editor.creators.ImageViewCreator}
 */
public class ImageCreatorForm {
  private JTextField myDrawableName;
  private JButton myDoNotSetSourceButton;
  private JComponent myComponent;
  private JButton mySetSourceButton;

  public ImageCreatorForm() {

  }

  public void addSetSourceListener(ActionListener listener) {
    mySetSourceButton.addActionListener(listener);
  }

  public JButton getDoNotSetSourceButton() {
    return myDoNotSetSourceButton;
  }

  @NotNull
  public String getDrawableName() {
    return myDrawableName.getText();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private void createUIComponents() {
    myComponent = new ToolRootPanel();
  }
}
