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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.activity.AndroidActivityLauncher;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public abstract class ActivityLaunchTask implements LaunchTask {
  @NotNull private final String myApplicationId;
  @NotNull private final StartActivityFlagsProvider myStartActivityFlagsProvider;

  public ActivityLaunchTask(@NotNull String applicationId,
                            @NotNull StartActivityFlagsProvider startActivityFlagsProvider) {
    myApplicationId = applicationId;
    myStartActivityFlagsProvider = startActivityFlagsProvider;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Launching activity";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    String activityName = getQualifiedActivityName(device, printer);
    if (activityName == null) {
      return false;
    }

    final String activityPath = AndroidActivityLauncher.getLauncherActivityPath(myApplicationId, activityName);
    String command = AndroidActivityLauncher.getStartActivityCommand(activityPath, myStartActivityFlagsProvider.getFlags(device));

    // The timeout is quite large to accomodate ARM emulators.
    return ShellCommandLauncher.execute(command, device, launchStatus, printer, 15, TimeUnit.SECONDS);
  }

  @Nullable
  protected abstract String getQualifiedActivityName(@NotNull IDevice device, @NotNull ConsolePrinter printer);
}
