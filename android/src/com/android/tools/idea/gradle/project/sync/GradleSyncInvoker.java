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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.build.invoker.GradleTasksExecutor;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.cleanup.PreSyncProjectCleanUp;
import com.android.tools.idea.gradle.project.sync.idea.IdeaGradleSync;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncChecks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.project.LibraryAttachments.removeLibrariesAndStoreAttachments;
import static com.android.tools.idea.gradle.project.importing.NewProjectImportGradleSyncListener.createTopLevelProjectAndOpen;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.clearStoredGradleJvmArgs;
import static com.android.tools.idea.gradle.util.Projects.executeProjectChanges;
import static com.android.tools.idea.gradle.util.Projects.requiresAndroidModel;
import static com.android.tools.idea.gradle.util.Projects.setSyncRequestedDuringBuild;
import static org.jetbrains.android.util.AndroidUtils.isAndroidStudio;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

public class GradleSyncInvoker {
  @NotNull private final FileDocumentManager myFileDocumentManager;
  @NotNull private final PreSyncProjectCleanUp myPreSyncProjectCleanUp;
  @NotNull private final PreSyncChecks myPreSyncChecks;

  @NotNull
  public static GradleSyncInvoker getInstance() {
    return ServiceManager.getService(GradleSyncInvoker.class);
  }

  public GradleSyncInvoker(@NotNull FileDocumentManager fileDocumentManager) {
    this(fileDocumentManager, new PreSyncProjectCleanUp(), new PreSyncChecks());
  }

  @VisibleForTesting
  GradleSyncInvoker(@NotNull FileDocumentManager fileDocumentManager,
                    @NotNull PreSyncProjectCleanUp preSyncProjectCleanUp,
                    @NotNull PreSyncChecks preSyncChecks) {
    myFileDocumentManager = fileDocumentManager;
    myPreSyncProjectCleanUp = preSyncProjectCleanUp;
    myPreSyncChecks = preSyncChecks;
  }

  public void requestProjectSyncAndSourceGeneration(@NotNull Project project, @Nullable GradleSyncListener listener) {
    requestProjectSync(project, Request.DEFAULT_REQUEST, listener);
  }

  public void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    if (isBuildInProgress(project)) {
      setSyncRequestedDuringBuild(project, true);
      return;
    }

    invokeAndWaitIfNeeded(() -> ensureToolWindowContentInitialized(project, GRADLE_SYSTEM_ID));
    Runnable syncTask = () -> {
      try {
        if (prepareProject(project, request, listener)) {
          sync(project, request, listener);
        }
      }
      catch (ConfigurationException e) {
        showErrorDialog(project, e.getMessage(), e.getTitle());
      }
    };

    if (request.isRunInBackground()) {
      invokeLaterIfProjectAlive(project, syncTask);
    }
    else {
      invokeAndWaitIfNeeded(syncTask);
    }
  }

  private static boolean isBuildInProgress(@NotNull Project project) {
    IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) {
      return false;
    }
    for (Pair<TaskInfo, ProgressIndicator> backgroundProcess : statusBar.getBackgroundProcesses()) {
      TaskInfo task = backgroundProcess.getFirst();
      if (task instanceof GradleTasksExecutor) {
        ProgressIndicator second = backgroundProcess.getSecond();
        if (second.isRunning()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean prepareProject(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener)
    throws ConfigurationException {
    if (requiresAndroidModel(project) || hasTopLevelGradleBuildFile(project)) {
      if (!request.isNewProject()) {
        myFileDocumentManager.saveAllDocuments();
      }
      return true; // continue with sync.
    }
    invokeLaterIfProjectAlive(project, () -> {
      String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
      AndroidGradleNotification.getInstance(project).showBalloon("Project Sync", msg, ERROR, new OpenMigrationToGradleUrlHyperlink());

      if (listener != null) {
        listener.syncFailed(project, msg);
      }
    });
    return false; // stop sync.
  }

  private static boolean hasTopLevelGradleBuildFile(@NotNull Project project) {
    String projectFolderPath = project.getBasePath();
    if (projectFolderPath != null) {
      File buildFile = new File(projectFolderPath, FN_BUILD_GRADLE);
      return buildFile.isFile();
    }
    return false;
  }

  private void sync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      // TODO move this method out of GradleUtil.
      clearStoredGradleJvmArgs(project);
    }

    PreSyncCheckResult canSync = myPreSyncChecks.canSync(project);
    if (!canSync.isSuccess()) {
      // User should have already warned that something is not right and sync cannot continue.
      String cause = nullToEmpty(canSync.getFailureCause());
      handlePreSyncCheckFailure(project, cause, listener);
      return;
    }

    // We only update UI on sync when re-importing projects. By "updating UI" we mean updating the "Build Variants" tool window and editor
    // notifications.  It is not safe to do this for new projects because the new project has not been opened yet.
    boolean started = GradleSyncState.getInstance(project).syncStarted(!request.isNewProject());
    if (!started) {
      return;
    }

    boolean useNewGradleSync = GradleExperimentalSettings.getInstance().USE_NEW_GRADLE_SYNC;
    if (!useNewGradleSync) {
      resetProject(project);
    }
    myPreSyncProjectCleanUp.cleanUp(project);

    GradleSync gradleSync;
    if (useNewGradleSync) {
      gradleSync = new NewGradleSync();
    }
    else {
      gradleSync = new IdeaGradleSync();
    }
    gradleSync.sync(project, request, listener);
  }

  private static void handlePreSyncCheckFailure(@NotNull Project project,
                                                @NotNull String failureCause,
                                                @Nullable GradleSyncListener syncListener) {
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (syncState.syncStarted(true)) {
      createTopLevelProjectAndOpen(project);
      syncState.syncFailed(failureCause);
      if (syncListener != null) {
        syncListener.syncFailed(project, failureCause);
      }
    }
  }

  // See issue: https://code.google.com/p/android/issues/detail?id=64508
  private static void resetProject(@NotNull Project project) {
    executeProjectChanges(project, () -> {
      removeLibrariesAndStoreAttachments(project);

      // Remove all AndroidProjects from module. Otherwise, if re-import/sync fails, editors will not show the proper notification of the
      // failure.
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          facet.setAndroidModel(null);
        }
      }
    });
  }

  public static class Request {
    private static final Request DEFAULT_REQUEST = new Request();

    private boolean myRunInBackground = true;
    private boolean myGenerateSourcesOnSuccess = true;
    private boolean myCleanProject;
    private boolean myUseCachedGradleModels;
    private boolean myNewProject;

    public boolean isRunInBackground() {
      return myRunInBackground;
    }

    @NotNull
    public Request setRunInBackground(boolean runInBackground) {
      myRunInBackground = runInBackground;
      return this;
    }

    public boolean isGenerateSourcesOnSuccess() {
      return myGenerateSourcesOnSuccess;
    }

    @NotNull
    public Request setGenerateSourcesOnSuccess(boolean generateSourcesOnSuccess) {
      myGenerateSourcesOnSuccess = generateSourcesOnSuccess;
      return this;
    }

    public boolean isCleanProject() {
      return myCleanProject;
    }

    @NotNull
    public Request setCleanProject(boolean cleanProject) {
      myCleanProject = cleanProject;
      return this;
    }

    public boolean isUseCachedGradleModels() {
      return myUseCachedGradleModels;
    }

    @NotNull
    public Request setUseCachedGradleModels(boolean useCachedGradleModels) {
      myUseCachedGradleModels = useCachedGradleModels;
      return this;
    }

    public boolean isNewProject() {
      return myNewProject;
    }

    @NotNull
    public Request setNewProject(boolean newProject) {
      myNewProject = newProject;
      return this;
    }

    @NotNull
    public ProgressExecutionMode getProgressExecutionMode() {
      return isRunInBackground() ? IN_BACKGROUND_ASYNC : MODAL_SYNC;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return myRunInBackground == request.myRunInBackground &&
             myCleanProject == request.myCleanProject &&
             myGenerateSourcesOnSuccess == request.myGenerateSourcesOnSuccess &&
             myUseCachedGradleModels == request.myUseCachedGradleModels &&
             myNewProject == request.myNewProject;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myRunInBackground, myCleanProject, myGenerateSourcesOnSuccess, myUseCachedGradleModels, myNewProject);
    }

    @Override
    public String toString() {
      return "RequestSettings{" +
             "myRunInBackground=" + myRunInBackground +
             ", myCleanProject=" + myCleanProject +
             ", myGenerateSourcesOnSuccess=" + myGenerateSourcesOnSuccess +
             ", myUseCachedGradleModels=" + myUseCachedGradleModels +
             ", myNewProject=" + myNewProject +
             '}';
    }
  }
}
