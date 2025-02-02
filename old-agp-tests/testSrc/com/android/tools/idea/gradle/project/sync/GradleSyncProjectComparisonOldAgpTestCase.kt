/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.GradleSyncProjectComparisonTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.intellij.testFramework.RunsInEdt
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Snapshot tests for 'Gradle Sync' that use old versions of AGP
 *
 * These tests compare the results of sync by converting the resulting project to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and comparing them to pre-recorded golden sync
 * results.
 *
 * The pre-recorded sync results can be found in testData/syncedProjectSnapshots/ *.txt files. Consult [snapshotSuffixes] for more
 * details on the way in which the file names are constructed.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_tests".
 */
@OldAgpTest(agpVersions = ["3.3.2"], gradleVersions = ["5.5"])
@RunWith(JUnit4::class)
@RunsInEdt
class GradleSyncProjectComparisonOldAgpTest: GradleSyncProjectComparisonTest() {

  @Test
  fun testSimpleApplicationWithAgp3_3_2() {
    val projectRootPath = prepareGradleProject(
      testProjectPath = TestProjectPaths.SIMPLE_APPLICATION,
      name = projectName,
      gradleVersion = "5.5",
      gradlePluginVersion = "3.3.2"
    )
    val text = openPreparedProject(projectName) { project -> project.saveAndDump() }
    assertIsEqualToSnapshot(text)
  }
}