/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

import java.io.File

interface IdeLintOptions {
    val baselineFile: File?
    val lintConfig: File?
    val severityOverrides: Map<String, Int>?
    val isCheckTestSources: Boolean
    val isCheckDependencies: Boolean
    val disable: Set<String>
    val enable: Set<String>
    val check: Set<String>?
    val isAbortOnError: Boolean
    val isAbsolutePaths: Boolean
    val isNoLines: Boolean
    val isQuiet: Boolean
    val isCheckAllWarnings: Boolean
    val isIgnoreWarnings: Boolean
    val isWarningsAsErrors: Boolean
    val isIgnoreTestSources: Boolean
    val isIgnoreTestFixturesSources: Boolean
    val isCheckGeneratedSources: Boolean
    val isCheckReleaseBuilds: Boolean
    val isExplainIssues: Boolean
    val isShowAll: Boolean
    val textReport: Boolean
    val textOutput: File?
    val htmlReport: Boolean
    val htmlOutput: File?
    val xmlReport: Boolean
    val xmlOutput: File?
    val sarifReport: Boolean
    val sarifOutput: File?

  companion object {
    /** A severity for Lint. Corresponds to com.android.tools.lint.detector.api.Severity#FATAL */
    const val SEVERITY_FATAL         = 1
    /** A severity for Lint. Corresponds to com.android.tools.lint.detector.api.Severity#ERROR */
    const val SEVERITY_ERROR         = 2
    /** A severity for Lint. Corresponds to com.android.tools.lint.detector.api.Severity#WARNING */
    const val SEVERITY_WARNING       = 3
    /** A severity for Lint. Corresponds to com.android.tools.lint.detector.api.Severity#INFORMATIONAL */
    const val SEVERITY_INFORMATIONAL = 4
    /** A severity for Lint. Corresponds to com.android.tools.lint.detector.api.Severity#IGNORE */
    const val SEVERITY_IGNORE        = 5
    /**
     * A severity for lint. This severity means that the severity should be whatever the default
     * is for this issue (this is used when the DSL just says "enable", and Gradle doesn't know
     * what the default severity is.)
     */
    const val SEVERITY_DEFAULT_ENABLED = 6
  }
}
