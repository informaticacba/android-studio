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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.project.GradleBuildListener;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.messages.MessageBusConnection;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.gradle.GradleSyncState.GRADLE_SYNC_TOPIC;
import static com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor.GRADLE_BUILD_TOPIC;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.tests.gui.framework.GuiTests.LONG_TIMEOUT;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;

public class IdeFrameFixture extends ComponentFixture<IdeFrameImpl> {
  private EditorFixture myEditor;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot, @NotNull final File projectPath, @Nullable final String projectName) {
    final GenericTypeMatcher<IdeFrameImpl> matcher = new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(IdeFrameImpl frame) {
        Project project = frame.getProject();
        if (project != null && projectPath.getPath().equals(project.getBasePath())) {
          return projectName == null || projectName.equals(project.getName());
        }
        return false;
      }
    };

    pause(new Condition("IdeFrame " + quote(projectPath.getPath()) + " to show up") {
      @Override
      public boolean test() {
        Collection<IdeFrameImpl> frames = robot.finder().findAll(matcher);
        return !frames.isEmpty();
      }
    }, LONG_TIMEOUT);

    IdeFrameImpl ideFrame = robot.finder().find(matcher);
    return new IdeFrameFixture(robot, ideFrame);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(robot, target);
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectToBeOpened() {
    Project project = getProject();
    Disposable disposable = new NoOpDisposable();

    final ProjectSyncListener listener = new ProjectSyncListener();
    try {
      MessageBusConnection connection = project.getMessageBus().connect(disposable);
      connection.subscribe(GRADLE_SYNC_TOPIC, listener);

      pause(new Condition("Syncing project " + quote(project.getName()) + " to finish") {
        @Override
        public boolean test() {
          return listener.mySyncFinished;
        }
      }, LONG_TIMEOUT);

      if (listener.mySyncError != null) {
        throw listener.mySyncError;
      }
    }
    finally {
      Disposer.dispose(disposable);
    }

    if (!listener.mySyncWasSkipped) {
      waitForSourceGenerationToFinish();
    }

    return waitForBackgroundTasksToFinish();
  }

  private void waitForSourceGenerationToFinish() {
    Project project = getProject();
    Disposable disposable = new NoOpDisposable();

    try {
      MessageBusConnection connection = project.getMessageBus().connect(disposable);
      final ProjectBuildListener listener = new ProjectBuildListener(SOURCE_GEN);
      connection.subscribe(GRADLE_BUILD_TOPIC, listener);

      pause(new Condition("Source generation for project " + quote(project.getName()) + " to finish'") {
        @Override
        public boolean test() {
          return listener.myBuildFinished;
        }
      }, LONG_TIMEOUT);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  @NotNull
  public IdeFrameFixture waitForBackgroundTasksToFinish() {
    pause(new Condition("Background tasks to finish") {
      @Override
      public boolean test() {
        ProgressManager progressManager = ProgressManager.getInstance();
        return !progressManager.hasModalProgressIndicator() &&
               !progressManager.hasProgressIndicator() &&
               !progressManager.hasUnsafeProgressIndicator();
      }
    }, LONG_TIMEOUT);
    return this;
  }

  @NotNull
  public IdeFrameFixture requireModuleCount(int expected) {
    Module[] modules = getModuleManager().getModules();
    assertThat(modules).as("Module count in project " + quote(getProject().getName())).hasSize(expected);
    return this;
  }

  @NotNull
  public IdeaAndroidProject getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.isGradleProject()) {
      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      if (androidProject != null) {
        return androidProject;
      }
    }
    throw new AssertionError("Unable to find IdeaAndroidProject for module " + quote(name));
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    for (Module module : getModuleManager().getModules()) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    throw new AssertionError("Unable to find module with name " + quote(name));
  }

  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public Project getProject() {
    Project project = target.getProject();
    assertNotNull(project);
    return project;
  }

  // TODO EditorFixture can be a better home for this method
  @NotNull
  public FileFixture openFile(@NotNull File path) {
    final VirtualFile virtualFile = findFileByIoFile(path, true);
    assertNotNull("No VirtualFile found for path " + quote(path.getPath()), virtualFile);

    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
        editorManager.openFile(virtualFile, true);
      }
    });

    FileFixture file = new FileFixture(getProject(), path);
    file.requireOpenAndSelected();

    return file;
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot, this);
    }

    return myEditor;
  }

  private static class ProjectSyncListener extends GradleSyncListener.Adapter {
    AssertionError mySyncError;
    boolean mySyncFinished;
    boolean mySyncWasSkipped;

    @Override
    public void syncSucceeded(@NotNull Project project) {
      mySyncFinished = true;
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      mySyncError = new AssertionError("Project sync for \"" + project.getName() + "\" failed: " + errorMessage);
      mySyncFinished = true;
    }

    @Override
    public void syncSkipped(@NotNull Project project) {
      mySyncFinished = true;
      mySyncWasSkipped = true;
    }
  }

  private static class ProjectBuildListener implements GradleBuildListener {
    @NotNull private final BuildMode myExpectedBuildMode;

    boolean myBuildFinished;

    ProjectBuildListener(@NotNull BuildMode expectedBuildMode) {
      myExpectedBuildMode = expectedBuildMode;
    }

    @Override
    public void buildFinished(@NotNull Project project, @Nullable BuildMode mode) {
      if (myExpectedBuildMode == mode) {
        myBuildFinished = true;
      }
    }
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }
}
