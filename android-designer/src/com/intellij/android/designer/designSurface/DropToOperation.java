/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DropToOperation extends AbstractEditOperation {
  private JComponent myFeedback;

  public DropToOperation(RadViewComponent container, OperationContext context) {
    super(container, context);
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

    if (myFeedback == null) {
      myFeedback = new AlphaComponent(Color.green);
      layer.add(myFeedback);
      myFeedback.setBounds(myContainer.getBounds(layer));
      layer.repaint();
    }
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
    }
  }
}