/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

/**
 * Triggers the build of the given [modules] by calling the compile`Variant`Kotlin task
 */
private fun requestKotlinBuild(project: Project, modules: Set<Module>, requestedByUser: Boolean) {
  fun createBuildTasks(module: Module): String? {
    if (module.isDisposed) return null
    val gradlePath = GradleFacet.getInstance(module)?.configuration?.GRADLE_PROJECT_PATH ?: return null
    val currentVariant = AndroidModuleModel.get(module)?.selectedVariant?.name?.capitalize() ?: return null
    // We need to get the compileVariantKotlin task name. There is not direct way to get it from the model so, for now,
    // we just build it ourselves.
    // TODO(b/145199867): Replace this with the right API call to obtain compileVariantKotlin after the bug is fixed.
    return "${gradlePath}${SdkConstants.GRADLE_PATH_SEPARATOR}compile${currentVariant}Kotlin"
  }

  fun createBuildTasks(modules: Collection<Module>): Map<Module, List<String>> =
    modules
      .mapNotNull {
        Pair(it, listOf(createBuildTasks(it) ?: return@mapNotNull null))
      }
      .filter { it.second.isNotEmpty() }
      .toMap()

  if (project.isDisposed) return
  val moduleFinder = ProjectStructure.getInstance(project).moduleFinder

  createBuildTasks(modules).forEach {
    val rootProjectPath = moduleFinder.getRootProjectPath(it.key)
    val request = GradleBuildInvoker.Request.Builder(
      project = project,
      rootProjectPath = rootProjectPath.toFile(),
      gradleTasks = it.value
    )
      // If this was not requested by a user action, then do not automatically pop-up the build output panel on error.
      .setDoNotShowBuildOutputOnFailure(!requestedByUser)
    GradleBuildInvoker.getInstance(project).executeTasks(request.build())
  }
}

internal fun requestBuild(project: Project, file: VirtualFile, requestByUser: Boolean) {
  requestBuild(project, listOf(file), requestByUser)
}

internal fun requestBuild(project: Project, files: Collection<VirtualFile>, requestByUser: Boolean) {
  if (project.isDisposed) {
    return
  }

  // We build using the ProjectSystem interface if the project is not Gradle OR if it is gradle, and the Kotlin only build is disabled.
  val buildWithProjectSystem = !GradleProjectInfo.getInstance(project).isBuildWithGradle
                               || !StudioFlags.COMPOSE_PREVIEW_ONLY_KOTLIN_BUILD.get()
  if (buildWithProjectSystem) {
    ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileFilesAndDependencies(files.map { it.getSourceFile() })
    return
  }

  // When COMPOSE_PREVIEW_ONLY_KOTLIN_BUILD is enabled, we just trigger the module:compileDebugKotlin task. This avoids executing
  // a few extra tasks that are not required for the preview to refresh

  // TODO: Move gradle compose builds to AndroidProjectSystem
  // For Gradle projects, build modules associated with files instead
  files.mapNotNull { ModuleUtil.findModuleForFile(it, project) }
    .toSet()
    .ifNotEmpty {
      requestKotlinBuild(project, this, requestByUser)
    }
}

fun hasExistingClassFile(psiFile: PsiFile?) = if (psiFile is PsiClassOwner) {
  val androidModuleSystem by lazy {
    ReadAction.compute<AndroidModuleSystem?, Throwable> {
      psiFile.getModuleSystem()
    }
  }
  ReadAction.compute<List<String>, Throwable> { psiFile.classes.mapNotNull { it.qualifiedName } }
    .mapNotNull { androidModuleSystem?.moduleClassFileFinder?.findClassFile(it) }
    .firstOrNull() != null
}
else false

/**
 * Returns whether the [PsiFile] has been built. It does this by checking the build status of the module if available.
 * If not available, this method will look for the compiled classes and check if they exist.
 *
 * @param project the [Project] the [PsiFile] belongs to.
 * @param lazyFileProvider a lazy provider for the [PsiFile]. It will only be called if needed to obtain the status
 *  of the build.
 */
@Slow
fun hasBeenBuiltSuccessfully(project: Project, lazyFileProvider: () -> PsiFile): Boolean {
  val result = ProjectSystemService.getInstance(project).projectSystem.getBuildManager().getLastBuildResult()

  if (result.status != ProjectSystemBuildManager.BuildStatus.UNKNOWN) {
    return result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS &&
           result.mode != ProjectSystemBuildManager.BuildMode.CLEAN

  }

  // We do not have information from the last build, try to find if the class file exists
  return hasExistingClassFile(lazyFileProvider())
}

/**
 * Returns whether the [PsiFile] has been built. It does this by checking the build status of the module if available.
 * If not available, this method will look for the compiled classes and check if they exist.
 */
@Slow
fun hasBeenBuiltSuccessfully(psiFilePointer: SmartPsiElementPointer<PsiFile>): Boolean =
  hasBeenBuiltSuccessfully(psiFilePointer.project) { ReadAction.compute<PsiFile, Throwable> { psiFilePointer.element } }

@Suppress("UnstableApiUsage")
private fun VirtualFile.getSourceFile(): VirtualFile = if (!this.isInLocalFileSystem && this is BackedVirtualFile) {
  this.originFile
}
else this