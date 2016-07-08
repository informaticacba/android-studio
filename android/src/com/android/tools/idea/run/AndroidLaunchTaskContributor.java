package com.android.tools.idea.run;

import com.android.tools.idea.run.tasks.LaunchTask;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point to provide additional launch tasks to {@code AndroidLaunchTaskProvider}.
 */
public interface AndroidLaunchTaskContributor {

  ExtensionPointName<AndroidLaunchTaskContributor> EP_NAME =
    ExtensionPointName.create("com.android.run.androidLaunchTaskContributor");

  /**
   * Returns true if the task is appropriate for the given context. Allows a process to avoid additional overhead by running tasks
   * that are not pertinent.
   */
  boolean isApplicable(@NotNull Module module);

  /**
   * Returns the appropriate launch task. Note that this will not be called if {@code inContext} returns false.
   */
  @NotNull
  LaunchTask getTask();
}
