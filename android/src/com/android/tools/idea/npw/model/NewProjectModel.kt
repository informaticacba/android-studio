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
package com.android.tools.idea.npw.model

import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.repository.io.FileOpUtils
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.platform.Language.JAVA
import com.android.tools.idea.npw.platform.Language.KOTLIN
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils
import com.android.tools.idea.npw.project.DomainToPackageExpression
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateMetadata.ATTR_CPP_FLAGS
import com.android.tools.idea.templates.TemplateMetadata.ATTR_CPP_SUPPORT
import com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_SUPPORT
import com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.WizardConstants
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.android.util.AndroidUtils
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Optional
import java.util.regex.Pattern

private val logger: Logger get() = logger<NewProjectModel>()

class NewProjectModel @JvmOverloads constructor(val projectSyncInvoker: ProjectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()) : WizardModel() {
  @JvmField val applicationName = StringValueProperty(message("android.wizard.module.config.new.application"))
  @JvmField val packageName = StringValueProperty()
  @JvmField val projectLocation = StringValueProperty()
  @JvmField val enableCppSupport = BoolValueProperty(PropertiesComponent.getInstance().isTrueValue(PROPERTIES_CPP_SUPPORT_KEY))
  @JvmField val cppFlags = StringValueProperty()
  @JvmField val project = OptionalValueProperty<Project>()
  @JvmField val templateValues = hashMapOf<String, Any>()
  @JvmField val language = OptionalValueProperty<Language>()
  /**
   * When the project is created, it contains the list of new Module that should also be created.
   */
  val multiTemplateRenderer = MultiTemplateRenderer(null, this.projectSyncInvoker)

  init {
    packageName.addListener {
      val androidPackage = packageName.get().substringBeforeLast('.')
      if (AndroidUtils.isValidAndroidPackageName(androidPackage)) {
        PropertiesComponent.getInstance().setValue(PROPERTIES_ANDROID_PACKAGE_KEY, androidPackage)
      }
    }

    applicationName.addConstraint(AbstractProperty.Constraint<String> { it.trim() } )

    language.set(calculateInitialLanguage(PropertiesComponent.getInstance()))
  }

  private fun saveWizardState() {
    // Set the property value
    val props = PropertiesComponent.getInstance()
    props.setValue(PROPERTIES_CPP_SUPPORT_KEY, enableCppSupport.get())
    props.setValue(PROPERTIES_NPW_LANGUAGE_KEY, language.value.toString())
    props.setValue(PROPERTIES_NPW_ASKED_LANGUAGE_KEY, true)
  }

  override fun handleFinished() {
    val projectLocation = projectLocation.get()
    val projectName = applicationName.get()

    val couldEnsureLocationExists = WriteCommandAction.runWriteCommandAction<Boolean>(null) {
      // We generally assume that the path has passed a fair amount of pre-validation checks
      // at the project configuration step before. Write permissions check can be tricky though in some cases,
      // e.g., consider an unmounted device in the middle of wizard execution or changed permissions.
      // Anyway, it seems better to check that we were really able to create the target location and are able to
      // write to it right here when the wizard is about to close, than running into some internal IDE errors
      // caused by these problems downstream
      // Note: this change was originally caused by http://b.android.com/219851, but then
      // during further discussions that a more important bug was in path validation in the old wizards,
      // where File.canWrite() always returned true as opposed to the correct Files.isWritable(), which is
      // already used in new wizard's PathValidator.
      // So the change below is therefore a more narrow case than initially supposed (however it still needs to be handled)
      try {
        if (VfsUtil.createDirectoryIfMissing(projectLocation) != null && FileOpUtils.create().canWrite(File(projectLocation))) {
          return@runWriteCommandAction true
        }
      }
      catch (e: Exception) {
        logger.error("Exception thrown when creating target project location: $projectLocation", e)
      }

      false
    }
    if (couldEnsureLocationExists) {
      project.value = ProjectManager.getInstance().createProject(projectName, projectLocation)!!
      multiTemplateRenderer.setProject(project.value)
    }
    else {
      val msg = "Could not ensure the target project location exists and is accessible:\n\n%1\$s\n\nPlease try to specify another path."
      Messages.showErrorDialog("$msg $projectLocation", "Error Creating Project")
    }

    multiTemplateRenderer.requestRender(ProjectTemplateRenderer())
  }

  private inner class ProjectTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    lateinit var projectTemplate: Template
    @WorkerThread
    override fun init() {
      projectTemplate = Template.createFromName(Template.CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME)
      // Cpp Apps attributes are needed to generate the Module and to generate the Render Template files (activity and layout)
      templateValues[ATTR_CPP_SUPPORT] = enableCppSupport.get()
      templateValues[ATTR_CPP_FLAGS] = cppFlags.get()
      templateValues[ATTR_TOP_OUT] = project.value.basePath ?: ""
      templateValues[ATTR_KOTLIN_SUPPORT] = language.value === KOTLIN

      TemplateValueInjector(templateValues).setProjectDefaults(project.value)
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      if (project.valueOrNull == null) {
        return false
      }

      performCreateProject(true)
      return true
    }

    @WorkerThread
    override fun render() {
      performCreateProject(false)

      try {
        val projectRoot = VfsUtilCore.virtualToIoFile(project.value.baseDir)
        AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot)
      }
      catch (e: IOException) {
        logger.warn("Failed to update Gradle wrapper permissions", e)
      }

      saveWizardState()
    }

    @UiThread
    override fun finish() = performGradleImport()

    private fun performCreateProject(dryRun: Boolean) {
      val context = RenderingContext.Builder.newContext(projectTemplate, project.value)
        .withCommandName("New Project")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withParams(templateValues)
        .build()

      projectTemplate.render(context, dryRun)
    }

    private fun performGradleImport() {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return
      }

      val rootLocation = File(projectLocation.get())
      val wrapperPropertiesFilePath = GradleWrapper.getDefaultPropertiesFilePath(rootLocation)
      try {
        val gradleDistFile = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(GRADLE_LATEST_VERSION)
        if (gradleDistFile == null) {
          GradleWrapper.get(wrapperPropertiesFilePath).updateDistributionUrl(GRADLE_LATEST_VERSION)
        }
        else {
          GradleWrapper.get(wrapperPropertiesFilePath).updateDistributionUrl(gradleDistFile)
        }
      }
      catch (e: IOException) {
        // Unlikely to happen. Continue with import, the worst-case scenario is that sync fails and the error message has a "quick fix".
        logger.warn("Failed to update Gradle wrapper file", e)
      }

      try {
        // Java language level; should be 7 for L and above
        var initialLanguageLevel: LanguageLevel? = null
        val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()
        if (sdkData != null) {
          val jdk = JavaSdk.getInstance()
          val sdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdk)
          if (sdk != null) {
            val version = jdk.getVersion(sdk)
            if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
              initialLanguageLevel = LanguageLevel.JDK_1_7
            }
          }
        }

        val request = GradleProjectImporter.Request(project.value)
        request.isNewProject = true
        request.javaLanguageLevel = initialLanguageLevel

        // "Import project" opens the project and thus automatically triggers sync.
        GradleProjectImporter.getInstance().importProjectNoSync(request)
      }
      catch (e: IOException) {
        Messages.showErrorDialog(e.message, message("android.wizard.project.create.error"))
        logger.error(e)
      }
    }
  }

  companion object {
    @VisibleForTesting const val PROPERTIES_ANDROID_PACKAGE_KEY = "SAVED_ANDROID_PACKAGE"
    @VisibleForTesting const val PROPERTIES_KOTLIN_SUPPORT_KEY = "SAVED_PROJECT_KOTLIN_SUPPORT"
    @VisibleForTesting const val PROPERTIES_NPW_LANGUAGE_KEY = "SAVED_ANDROID_NPW_LANGUAGE"
    @VisibleForTesting const val PROPERTIES_NPW_ASKED_LANGUAGE_KEY = "SAVED_ANDROID_NPW_ASKED_LANGUAGE"

    private const val PROPERTIES_CPP_SUPPORT_KEY = "SAVED_PROJECT_CPP_SUPPORT"
    private const val EXAMPLE_DOMAIN = "example.com"
    private val DISALLOWED_IN_DOMAIN = Pattern.compile("[^a-zA-Z0-9_]")
    private val MODULE_NAME_GROUP = Pattern.compile(".*:") // Anything before ":" belongs to the module parent name

    /**
     * Loads saved company domain, or generates a dummy one if no domain has been saved
     */
    @JvmStatic
    fun getInitialDomain(): String {
      val androidPackage = PropertiesComponent.getInstance().getValue(PROPERTIES_ANDROID_PACKAGE_KEY)
      if (androidPackage != null) {
        return DomainToPackageExpression(StringValueProperty(androidPackage), StringValueProperty("")).get()
      }
      return EXAMPLE_DOMAIN
    }

    /**
     * Tries to get a valid package suggestion for the specifies Project using the saved user domain.
     */
    @JvmStatic
    fun getSuggestedProjectPackage(): String {
        val companyDomain = StringValueProperty(getInitialDomain())
        return DomainToPackageExpression(companyDomain, StringValueProperty("")).get()
      }

    /**
     * Calculates the initial values for the language and updates the [PropertiesComponent]
     * @return If Language was previously saved, just return that saved value.
     * If User used the old UI check-box to select "Use Kotlin" or the User is using the Wizard for the first time => Kotlin
     * otherwise Java (ie user used the wizards before, and un-ticked the check-box)
     */
    @JvmStatic
    fun calculateInitialLanguage(props: PropertiesComponent): Optional<Language> {
      val initialLanguage: Language
      val languageValue = props.getValue(PROPERTIES_NPW_LANGUAGE_KEY)
      if (languageValue == null) {
        val selectedOldUseKotlin = props.getBoolean(PROPERTIES_KOTLIN_SUPPORT_KEY)
        val isFirstUsage = !props.isValueSet(PROPERTIES_ANDROID_PACKAGE_KEY)
        initialLanguage = if (selectedOldUseKotlin || isFirstUsage) KOTLIN else JAVA

        // Save now, otherwise the user may cancel the wizard, but the property for "isFirstUsage" will be set just because it was shown.
        props.setValue(PROPERTIES_NPW_LANGUAGE_KEY, initialLanguage.toString())
        props.unsetValue(PROPERTIES_KOTLIN_SUPPORT_KEY)
      }
      else {
        // We have this value saved already, nothing to do
        initialLanguage = Language.fromName(languageValue, KOTLIN)
      }

      val askedBefore = props.getBoolean(PROPERTIES_NPW_ASKED_LANGUAGE_KEY)
      // After version 3.5, we force the user to select the language if we didn't ask before or if the selection was not Kotlin.
      return if (initialLanguage === KOTLIN || askedBefore) Optional.of(initialLanguage) else Optional.empty()
    }

    @JvmStatic
    fun sanitizeApplicationName(s: String): String = DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("")

    /**
     * Converts the name of a Module, Application or User to a valid java package name segment.
     * Invalid characters are removed, and reserved Java language names are converted to valid values.
     */
    @JvmStatic
    fun nameToJavaPackage(name: String): String {
      val res = name.replace('-', '_').run {
        MODULE_NAME_GROUP.matcher(this).replaceAll("").run {
          DISALLOWED_IN_DOMAIN.matcher(this).replaceAll("").toLowerCase(Locale.US)
        }
      }
      if (res.isNotEmpty() && AndroidUtils.isReservedKeyword(res) != null) {
        return StringUtil.fixVariableNameDerivedFromPropertyName(res).toLowerCase(Locale.US)
      }
      return res
    }
  }
}
