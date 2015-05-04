/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.JavaModel;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacetConfiguration;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_DEPENDENCIES;
import static com.android.tools.idea.gradle.messages.Message.Type.ERROR;
import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static java.util.Collections.singletonList;

public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaJavaProject> {
  private static final Logger LOG = Logger.getInstance(AbstractDependenciesModuleCustomizer.class);
  private static final DependencyScope DEFAULT_DEPENDENCY_SCOPE = COMPILE;

  @Override
  protected void setUpDependencies(@NotNull ModifiableRootModel model,
                                   @NotNull IdeaJavaProject javaProject,
                                   @NotNull List<Message> errorsFound) {
    List<String> unresolved = Lists.newArrayList();

    for (JavaModuleDependency dependency : javaProject.getJavaModuleDependencies()) {
      updateDependency(model, dependency, errorsFound);
    }

    for (JarLibraryDependency dependency : javaProject.getJarLibraryDependencies()) {
      if (dependency.isResolved()) {
        updateDependency(model, dependency, errorsFound);
      }
      else {
        unresolved.add(dependency.getName());
      }
    }

    Module module = model.getModule();

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(model.getProject());
    messages.reportUnresolvedDependencies(unresolved, module);

    JavaGradleFacet facet = setAndGetJavaGradleFacet(module);
    File buildFolderPath = javaProject.getBuildFolderPath();
    if (!isGradleProjectModule(module)) {
      JavaModel javaModel = new JavaModel(unresolved, buildFolderPath);
      facet.setJavaModel(javaModel);
    }
    JavaGradleFacetConfiguration facetProperties = facet.getConfiguration();
    facetProperties.BUILD_FOLDER_PATH = buildFolderPath != null ? toSystemIndependentName(buildFolderPath.getPath()) : "";
    facetProperties.BUILDABLE = javaProject.isBuildable();
  }

  private static void updateDependency(@NotNull ModifiableRootModel model,
                                       @NotNull JavaModuleDependency dependency,
                                       @NotNull List<Message> errorsFound) {
    String moduleName = dependency.getModuleName();
    ModuleManager moduleManager = ModuleManager.getInstance(model.getProject());
    Module found = null;
    for (Module module : moduleManager.getModules()) {
      if (moduleName.equals(module.getName())) {
        found = module;
      }
    }
    if (found != null) {
      ModuleOrderEntry orderEntry = model.addModuleOrderEntry(found);
      orderEntry.setExported(true);
      return;
    }
    String msg = String.format("Unable fo find module '%1$s'.", moduleName);
    LOG.info(msg);
    errorsFound.add(new Message(FAILED_TO_SET_UP_DEPENDENCIES, ERROR, msg));
  }

  private static void updateDependency(@NotNull ModifiableRootModel model,
                                       @NotNull JarLibraryDependency dependency,
                                       @NotNull List<Message> errorsFound) {
    DependencyScope scope = parseScope(dependency.getScope());
    File binaryPath = dependency.getBinaryPath();
    if (binaryPath == null) {
      String msg = "Found a library dependency without a 'binary' path: " + dependency;
      LOG.info(msg);
      errorsFound.add(new Message(FAILED_TO_SET_UP_DEPENDENCIES, ERROR, msg));
      return;
    }
    String path = binaryPath.getPath();

    // Gradle API doesn't provide library name at the moment.
    String name = binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(path);
    setUpLibraryDependency(model, name, scope, singletonList(path), asPaths(dependency.getSourcePath()),
                           asPaths(dependency.getJavadocPath()));
  }

  @NotNull
  private static List<String> asPaths(@Nullable File file) {
    return file == null ? Collections.<String>emptyList() : singletonList(file.getPath());
  }

  @NotNull
  private static DependencyScope parseScope(@Nullable String scope) {
    if (scope == null) {
      return DEFAULT_DEPENDENCY_SCOPE;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (scope.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return DEFAULT_DEPENDENCY_SCOPE;
  }

  @NotNull
  private static JavaGradleFacet setAndGetJavaGradleFacet(Module module) {
    JavaGradleFacet facet = JavaGradleFacet.getInstance(module);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet = facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null);
      model.addFacet(facet);
    }
    finally {
      model.commit();
    }
    return facet;
  }

}
