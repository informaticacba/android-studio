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
package com.android.tools.idea.gradle.dsl.parser;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.util.GradleUtil.getPathSegments;
import static com.intellij.openapi.util.text.StringUtil.isQuotedString;

/**
 * A Gradle project dependency. There are two notations supported for declaring a project dependency:
 * <pre> configurationName project("gradlePath") </pre> and
 * <pre> configurationName project(path: "gradlePath", configuration: "configuration") </pre>
 */
public class ProjectDependencyElement implements DependencyElement {
  @NotNull String myConfigurationName;
  @NotNull GrLiteralContainer myGradlePathElement;
  /**
   * Not null if the dependency uses map notation and configuration argument is specified. e.g.
   * compile project(path: "gradlePath", configuration: "configuration")
   */
  @Nullable GrLiteralContainer myTargetConfigurationElement;

  /**
   * Creates project dependency from the literal form:
   * <pre>
   *   compile project("gradlePath")
   * </pre>
   */
  @Nullable
  public static ProjectDependencyElement withCompactNotation(@NotNull String configurationName,
                                                             @NotNull GrLiteralContainer projectNamePsiElement) {
    if (projectNamePsiElement.getValue() == null) {
      return null;
    }
    return new ProjectDependencyElement(configurationName, projectNamePsiElement, null);
  }

  /**
   * Creates project dependency from map notation:
   * <pre>
   *   compile project(path: "gradlePath", configuration: "xxx")
   *   compile project(path: "gradlePath")
   * </pre>
   */
  @Nullable
  public static ProjectDependencyElement withMapNotation(@NotNull String configurationName, @NotNull GrArgumentList argumentList) {
    GrLiteral projectNamePsiElement = findNamedArgumentLiteralValue(argumentList, "path");
    if (projectNamePsiElement == null || projectNamePsiElement.getValue() == null) {
      return null;
    }
    return new ProjectDependencyElement(configurationName, projectNamePsiElement,
                                        findNamedArgumentLiteralValue(argumentList, "configuration"));
  }

  @Nullable
  private static GrLiteral findNamedArgumentLiteralValue(@NotNull GrArgumentList argumentList, @NotNull String label) {
    GrNamedArgument namedArgument = argumentList.findNamedArgument(label);
    if (namedArgument == null) {
      return null;
    }
    if (namedArgument.getExpression() instanceof GrLiteral) {
      return (GrLiteral)namedArgument.getExpression();
    }
    return null;
  }

  private ProjectDependencyElement(@NotNull String configurationName,
                                   @NotNull GrLiteralContainer projectNamePsiElement,
                                   @Nullable GrLiteralContainer targetConfigurationElement) {
    myConfigurationName = configurationName;
    myGradlePathElement = projectNamePsiElement;
    myTargetConfigurationElement = targetConfigurationElement;
  }

  @NotNull
  public String getConfigurationName() {
    return myConfigurationName;
  }

  @NotNull
  public String getPath() {
    Object literalValue = myGradlePathElement.getValue();
    assert literalValue != null;
    return literalValue.toString();
  }

  @NotNull
  public String getName() {
    List<String> pathSegments = getPathSegments(getPath());
    int segmentCount = pathSegments.size();
    return segmentCount > 0 ? pathSegments.get(segmentCount - 1) : "";
  }

  @Nullable
  public String getTargetConfiguration() {
    if (myTargetConfigurationElement == null) {
      return null;
    }
    Object value = myTargetConfigurationElement.getValue();
    return value != null ? value.toString() : null;
  }

  public void setName(@NotNull String newName) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    String newPath;

    // Keep empty spaces, needed when putting the path back together
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).splitToList(getPath());
    List<String> modifiableSegments = Lists.newArrayList(segments);
    int segmentCount = modifiableSegments.size();
    if (segmentCount == 0) {
      newPath = GRADLE_PATH_SEPARATOR + newName.trim();
    }
    else {
      modifiableSegments.set(segmentCount - 1, newName);
      newPath = Joiner.on(GRADLE_PATH_SEPARATOR).join(modifiableSegments);
    }
    String currentText = myGradlePathElement.getText();
    char quote = '\'';
    if (isQuotedString(currentText)) {
      // Use same quote as the original text.
      quote = currentText.charAt(0);
    }
    newPath = quote + newPath + quote;
    myGradlePathElement = myGradlePathElement.updateText(newPath);
  }
}
