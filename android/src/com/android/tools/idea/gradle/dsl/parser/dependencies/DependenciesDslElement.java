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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DependenciesDslElement extends GradleDslBlockElement {
  @NonNls public static final String DEPENDENCIES_BLOCK_NAME = "dependencies";

  public DependenciesDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(DEPENDENCIES_BLOCK_NAME));
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement dependency) {
    // Treat all expressions and expression maps as dependencies
    if (dependency instanceof GradleDslExpression || dependency instanceof GradleDslExpressionMap) {
      GradleDslElementList elementList = getOrCreateParsedElement(dependency.getName());
      elementList.addParsedElement(dependency);
    }
    else if (dependency instanceof GradleDslExpressionList) {
      GradleDslElementList elementList = getOrCreateParsedElement(dependency.getName());
      for (GradleDslExpression expression : ((GradleDslExpressionList)dependency).getExpressions()) {
        GradleDslClosure dependencyClosureElement = dependency.getClosureElement();
        if (expression.getClosureElement() == null && dependencyClosureElement != null) {
          expression.setParsedClosureElement(dependencyClosureElement);
        }
        elementList.addParsedElement(expression);
      }
    }
  }

  @NotNull
  private GradleDslElementList getOrCreateParsedElement(@NotNull String configurationName) {
    GradleDslElementList elementList = getPropertyElement(configurationName, GradleDslElementList.class);
    if (elementList == null) {
      GradleNameElement name = GradleNameElement.create(configurationName);
      elementList = new GradleDslElementList(this, name);
      super.addParsedElement(elementList);
    }
    return elementList;
  }
}
