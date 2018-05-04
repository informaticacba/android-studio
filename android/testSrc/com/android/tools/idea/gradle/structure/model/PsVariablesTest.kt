/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.util.concurrent.ListenableFuture
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

class PsVariablesTest : AndroidGradleTestCase() {

  fun testGetModuleVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProject(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables.getModuleVariables()
    assertThat(variables.size, equalTo(9))
    assertThat(
      variables.map { it.getName() },
      hasItems(
        "myVariable",
        "variable1",
        "anotherVariable",
        "mapVariable",
        "moreVariable",
        "varInt",
        "varBool",
        "varRefString",
        "varProGuardFiles"
      )
    )
  }

  fun testGetAvailableVariablesForType() {
    val stringWithDotsProperty: ModelPropertyContext<Nothing?, Any, String> = object : ModelPropertyContext<Nothing?, Any, String> {
      override fun parse(context: Nothing?, value: String): ParsedValue<String> = when {
        value.contains(".") -> ParsedValue.Set.Parsed(value, DslText.Literal)
        else -> ParsedValue.Set.Invalid(value, "invalid")
      }

      override fun format(context: Nothing?, value: String): String = throw UnsupportedOperationException()

      override fun getKnownValues(context: Nothing?, model: Any): ListenableFuture<KnownValues<String>> =
        throw UnsupportedOperationException()
    }
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProject(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    run {
      val variables = psAppModule.variables.getAvailableVariablesFor(null, stringWithDotsProperty).toSet()
      assertThat(
        variables,
        equalTo(
          setOf(
            ("myVariable" to "26.1.0").asParsed(),
            ("variable1" to "1.3").asParsed(),
            ("anotherVariable" to "3.0.1").asParsed(),
            ("varRefString" to "1.3").asParsed()
          )
        )
      )
    }
  }
}

private fun <T : Any> Pair<String, T>.asParsed() = ParsedValue.Set.Parsed(dslText = DslText.Reference(first), value = second)
