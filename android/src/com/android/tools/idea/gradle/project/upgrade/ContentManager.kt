package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FAILURE_PREDICTED
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeHelper
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.util.EventListener
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeModelEvent
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

// "Model" here loosely in the sense of Model-View-Controller
class ToolWindowModel(
  val project: Project,
  var current: GradleVersion?,
  val knownVersionsRequester: () -> Set<GradleVersion> = { IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle") }
) {

  val latestKnownVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val selectedVersion = OptionalValueProperty<GradleVersion>(latestKnownVersion)
  var processor: AgpUpgradeRefactoringProcessor? = null

  //TODO introduce single state object describing controls and error instead.
  val showLoadingState = BoolValueProperty(true)
  val runTooltip = StringValueProperty()
  val message = OptionalValueProperty<Pair<Icon, String>>()
  val runEnabled = BoolValueProperty(true)

  val knownVersions = OptionalValueProperty<List<GradleVersion>>()

  val treeModel = DefaultTreeModel(CheckedTreeNode(null))

  val checkboxTreeStateUpdater = object : CheckboxTreeListener {
    override fun nodeStateChanged(node: CheckedTreeNode) {
      fun findNecessityNode(necessity: AgpUpgradeComponentNecessity): CheckedTreeNode? =
        (treeModel.root as CheckedTreeNode).children().asSequence().firstOrNull { (it as CheckedTreeNode).userObject == necessity } as? CheckedTreeNode

      fun enableNode(node: CheckedTreeNode) {
        node.isEnabled = true
        node.children().asSequence().forEach { (it as? CheckedTreeNode)?.isEnabled = true }
      }

      fun disableNode(node: CheckedTreeNode) {
        node.isEnabled = false
        node.children().asSequence().forEach { (it as? CheckedTreeNode)?.isEnabled = false }
      }

      fun allChildrenChecked(node: CheckedTreeNode) = node.children().asSequence().all { (it as? CheckedTreeNode)?.isChecked ?: true }
      fun anyChildrenChecked(node: CheckedTreeNode) = node.children().asSequence().any { (it as? CheckedTreeNode)?.isChecked ?: true }
      // We change the enabled states of nodes in the nodeStateChanged calls for the leaves (where the parents are the necessities) so
      // that we can largely ignore issues of checked state propagation; tempting though it is to do this for the state changes on the
      // necessity nodes themselves, it's not possible to get it right, because we can't tell whether we are deselecting a node because
      // of an explicit user action on that node or a propagated deselection of a child, and selecting a child node does not cause a
      // state change in the parent directly in any case.
      val parentNode = (node.parent as? CheckedTreeNode)?.also { if (it.userObject !is AgpUpgradeComponentNecessity) return } ?: return
      // The MANDATORY_CODEPENDENT node is special in two ways:
      // - its children's checkboxes are always disabled;
      // - it acts as a gateway for the other two necessities mentioned here: if it's enabled, then OPTIONAL_CODEPENDENT processors may
      //   be selected; if it's disabled, then MANDATORY_INDEPENDENT processors may be deselected.
      when (parentNode.userObject) {
        MANDATORY_INDEPENDENT -> findNecessityNode(MANDATORY_CODEPENDENT)?.let { it.isEnabled = allChildrenChecked(parentNode) }
        MANDATORY_CODEPENDENT -> {
          findNecessityNode(MANDATORY_INDEPENDENT)?.let { if (node.isChecked) disableNode(it) else enableNode(it) }
          findNecessityNode(OPTIONAL_CODEPENDENT)?.let { if (node.isChecked) enableNode(it) else disableNode(it) }
        }
        OPTIONAL_CODEPENDENT -> findNecessityNode(MANDATORY_CODEPENDENT)?.let { it.isEnabled = !anyChildrenChecked(parentNode) }
      }
    }
  }

  val connection = project.messageBus.connect()

  init {
    refresh()
    selectedVersion.addListener { refresh() }
    connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : ProjectSystemSyncManager.SyncResultListener {
      override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) = refresh(true)
    })

    // Initialize known versions (e.g. in case of offline work with no cache)
    knownVersions.value = suggestedVersionsList(setOf())

    // Request known versions.
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Looking for known versions", false) {
      override fun run(indicator: ProgressIndicator) {
        val gMavenVersions = knownVersionsRequester()
        val knownVersionsList = suggestedVersionsList(gMavenVersions)
        invokeLater(ModalityState.NON_MODAL) { knownVersions.value = knownVersionsList }
      }
    })
  }

  fun suggestedVersionsList(gMavenVersions: Set<GradleVersion>): List<GradleVersion> = gMavenVersions
    // Make sure the current (if known) and latest known versions are present, whether published or not
    .union(listOfNotNull(current, latestKnownVersion))
    // Keep only versions that are later than or equal to current
    .filter { current?.let { current -> it >= current } ?: false }
    // Keep only versions that are no later than the latest version we support
    .filter { it <= latestKnownVersion }
    // Do not keep versions that would force an upgrade from on sync
    .filter { !versionsShouldForcePluginUpgrade(it, latestKnownVersion) }
    .toList()
    .sortedDescending()

  fun refresh(refindPlugin: Boolean = false) {
    showLoadingState.set(true)
    // First clear some state
    runEnabled.set(false)
    val root = (treeModel.root as CheckedTreeNode)
    root.removeAllChildren()
    treeModel.nodeStructureChanged(root)

    if (refindPlugin) { current = AndroidPluginInfo.find(project)?.pluginVersion }
    val newVersion = selectedVersion.valueOrNull
    // TODO(xof/mlazeba): should we somehow preserve the existing uuid of the processor?
    val newProcessor = newVersion?.let {
      current?.let { current ->
        if (newVersion >= current) AgpUpgradeRefactoringProcessor(project, current, it) else null
      }
    }
    processor = newProcessor

    if (newProcessor == null) {
      // Preserve existing message and run button tooltips from newVersion validation.
      showLoadingState.set(false)
    }
    else {
      runTooltip.clear()
      message.clear()
      val application = ApplicationManager.getApplication()
      if (application.isUnitTestMode) {
        parseAndSetEnabled(newProcessor)
      } else {
        application.executeOnPooledThread { parseAndSetEnabled(newProcessor) }
      }
    }
  }

  private fun parseAndSetEnabled(newProcessor: AgpUpgradeRefactoringProcessor) {
    val application = ApplicationManager.getApplication()
    newProcessor.ensureParsedModels()
    val projectFilesClean = isCleanEnoughProject(project)
    val classpathUsageFound = !newProcessor.classpathRefactoringProcessor.isAlwaysNoOpForProject
    if (application.isUnitTestMode) {
      setEnabled(newProcessor, projectFilesClean, classpathUsageFound)
    } else {
      invokeLater(ModalityState.NON_MODAL) { setEnabled(newProcessor, projectFilesClean, classpathUsageFound) }
    }
  }

  private fun setEnabled(newProcessor: AgpUpgradeRefactoringProcessor, projectFilesClean: Boolean, classpathUsageFound: Boolean) {
    refreshTree(newProcessor)
    if (!classpathUsageFound && newProcessor.current != newProcessor.new) {
      newProcessor.trackProcessorUsage(FAILURE_PREDICTED)
      runEnabled.set(false)
      runTooltip.set("Cannot locate the version specification for the Android Gradle Plugin dependency, " +
                     "possibly because the project's build files use features not currently support by the " +
                     "Upgrade Assistant (for example: using constants defined in buildSrc)."
      )
      message.value = AllIcons.General.Error to "Cannot find AGP version in build files."
    }
    else if (!projectFilesClean) {
      runEnabled.set(true)
      runTooltip.set("There are uncommitted changes in project build files.  Before upgrading, " +
                     "you should commit or revert changes to the build files so that changes from the upgrade process " +
                     "can be handled separately.")
      message.value = AllIcons.General.Warning to "Uncommitted changes in build files."
    }
    else {
      runEnabled.set(true)
    }
    showLoadingState.set(false)
  }

  private fun refreshTree(processor: AgpUpgradeRefactoringProcessor) {
    val root = treeModel.root as CheckedTreeNode
    root.removeAllChildren()
    fun <T : DefaultMutableTreeNode> populateNecessity(
      necessity: AgpUpgradeComponentNecessity,
      constructor: (Any) -> (T)
    ): CheckedTreeNode {
      val node = CheckedTreeNode(necessity)
      processor.activeComponentsForNecessity(necessity).forEach { component -> node.add(constructor(toStepPresentation(component))) }
      node.let { if (it.childCount > 0) root.add(it) }
      return node
    }
    populateNecessity(MANDATORY_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }.isEnabled = false
    populateNecessity(MANDATORY_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }
    populateNecessity(OPTIONAL_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    populateNecessity(OPTIONAL_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    treeModel.nodeStructureChanged(root)
  }

  fun runUpgrade(showPreview: Boolean) = processor?.let { processor ->
    processor.components().forEach { it.isEnabled = false }
    CheckboxTreeHelper.getCheckedNodes(DefaultStepPresentation::class.java, null, treeModel)
      .forEach { it.processor.isEnabled = true }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      processor.run()
    }
    else {
      DumbService.getInstance(processor.project).smartInvokeLater {
        processor.setPreviewUsages(showPreview)
        processor.run()
      }
    }
  }

  interface ChangeListener : EventListener {
    fun modelChanged()
  }

  interface StepUiPresentation {
    val pageHeader: String
    val treeText: String
    val helpLinkUrl: String?
    val shortDescription: String?
  }

  interface StepUiWithComboSelectorPresentation {
    val label: String
    val elements: List<Any>
    var selectedValue: Any
  }

  // TODO(mlazeba/xof): temporary here, need to be defined in processor itself probably
  private fun toStepPresentation(processor: AgpUpgradeComponentRefactoringProcessor) = when (processor) {
    is Java8DefaultRefactoringProcessor -> object : DefaultStepPresentation(processor), StepUiWithComboSelectorPresentation {
      override val label: String = "Action on no explicit Java language level: "
      override val pageHeader: String
        get() = processor.commandName
      override val treeText: String
        get() = processor.noLanguageLevelAction.toString()
      override val elements: List<Java8DefaultRefactoringProcessor.NoLanguageLevelAction>
        get() = listOf(
          Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT,
          Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
        )
      override var selectedValue: Any
        get() = processor.noLanguageLevelAction
        set(value) {
          if (value is Java8DefaultRefactoringProcessor.NoLanguageLevelAction) processor.noLanguageLevelAction = value
        }
      init {
        selectedValue = Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
      }
    }
    else -> DefaultStepPresentation(processor)
  }

  open class DefaultStepPresentation(val processor: AgpUpgradeComponentRefactoringProcessor) : StepUiPresentation {
    override val pageHeader: String
      get() = treeText
    override val treeText: String
      get() = processor.commandName
    override val helpLinkUrl: String?
      get() = processor.getReadMoreUrl()
    override val shortDescription: String?
      get() = processor.getShortDescription()
  }
}

class ContentManager(val project: Project) {
  init {
    ToolWindowManager.getInstance(project).registerToolWindow(
      RegisterToolWindowTask.closable("Upgrade Assistant", icons.GradleIcons.ToolWindowGradle))
  }

  fun showContent() {
    val current = AndroidPluginInfo.find(project)?.pluginVersion
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    toolWindow.contentManager.removeAllContents(true)
    val model = ToolWindowModel(project, current)
    val view = View(model, toolWindow.contentManager)
    val content = ContentFactory.SERVICE.getInstance().createContent(view.content, model.current.contentDisplayName(), true)
    content.setDisposer(model.connection)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

  class View(val model: ToolWindowModel, contentManager: com.intellij.ui.content.ContentManager) {
    /*
    Experiment of usage of observable property bindings I have found in our code base.
    Taking inspiration from com/android/tools/idea/avdmanager/ConfigureDeviceOptionsStep.java:85 at the moment (Jan 2021).
     */
    private val myBindings = BindingsManager()
    private val myListeners = ListenerManager()

    val tree = CheckboxTree(UpgradeAssistantTreeCellRenderer(), null).apply {
      model = this@View.model.treeModel
      isRootVisible = false
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
      addCheckboxTreeListener(this@View.model.checkboxTreeStateUpdater)
      addTreeSelectionListener { e -> refreshDetailsPanel() }
      background = primaryContentBackground
      isOpaque = true
    }

    val upgradeLabel = JBLabel(model.current.upgradeLabelText()).also { it.border = JBUI.Borders.empty(0, 6) }

    fun editingValidation(value: String?): Pair<EditingErrorCategory, String> {
      val parsed = value?.let { GradleVersion.tryParseAndroidGradlePluginVersion(it) }
      val current = model.current
      return when {
        current == null -> Pair(EditingErrorCategory.ERROR, "Unknown current AGP version")
        parsed == null -> Pair(EditingErrorCategory.ERROR, "Invalid AGP version format.")
        parsed < current -> Pair(EditingErrorCategory.ERROR, "Selected version too low.")
        else -> EDITOR_NO_ERROR
      }
    }

    val versionTextField = CommonComboBox<GradleVersion, CommonComboBoxModel<GradleVersion>>(
      object : DefaultCommonComboBoxModel<GradleVersion>(
        model.selectedVersion.valueOrNull?.toString() ?: "",
        model.knownVersions.valueOrNull ?: emptyList()
      ) {
        init {
          selectedItem = model.selectedVersion.valueOrNull
          myListeners.listen(model.knownVersions) { knownVersions ->
            removeAllElements()
            selectedItem = model.selectedVersion.valueOrNull
            knownVersions.orElse(emptyList()).forEach { addElement(it) }
          }
          placeHolderValue = "Select new version"
        }

        // Given the ComponentValidator installation below, one might expect this not to be necessary, but although the
        // ComponentValidator provides the tooltip it appears not to provide the outline highlighting.
        override val editingSupport = object : EditingSupport {
          override val validation: EditingValidation = ::editingValidation
        }
      }
    ).apply {
      ComponentValidator(this@View.model.connection).withValidator { ->
        val text = editor.item.toString()
        val validation = editingValidation(text)
        when (validation.first) {
          EditingErrorCategory.ERROR -> ValidationInfo(validation.second, this)
          EditingErrorCategory.WARNING -> ValidationInfo(validation.second, this).asWarning()
          else -> null
        }
      }.installOn(this)
      (editor.editorComponent as? JTextComponent)?.document?.addDocumentListener(
        object: DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            ComponentValidator.getInstance(this@apply).ifPresent { v -> v.revalidate() }
            val status = editingValidation(editor.item.toString())
            if (status.first == EditingErrorCategory.ERROR) {
              previewButton.isEnabled = false
              previewButton.toolTipText = status.second
              okButton.isEnabled = false
              okButton.toolTipText = status.second
            }
            else {
              previewButton.isEnabled = this@View.model.runEnabled.get()
              previewButton.toolTipText = this@View.model.runTooltip.get()
              okButton.isEnabled = this@View.model.runEnabled.get()
              okButton.toolTipText = this@View.model.runTooltip.get()
            }
          }
        }
      )
      addActionListener {
        this@View.model.selectedVersion.setNullableValue(
          editingValidation(model.text).let { modelTextValidation ->
            if (modelTextValidation.first == EditingErrorCategory.ERROR) {
              this@View.model.message.value = AllIcons.General.Error to modelTextValidation.second
              this@View.model.runEnabled.set(false)
              null
            }
            else {
              when (val selected = selectedItem) {
                is GradleVersion -> selected
                is String ->
                  editingValidation(selected).let { stringValidation ->
                    if (stringValidation.first == EditingErrorCategory.ERROR) {
                      this@View.model.message.value = AllIcons.General.Error to stringValidation.second
                      this@View.model.runEnabled.set(false)
                      null
                    }
                    else GradleVersion.tryParseAndroidGradlePluginVersion(selected)
                  }
                else -> null
              }
            }
          }
        )
      }
    }

    val refreshButton = JButton("Refresh").apply {
      addActionListener { this@View.model.refresh(true) }
    }
    val okButton = JButton("Run selected steps").apply {
      addActionListener { this@View.model.runUpgrade(false) }
      myListeners.listen(this@View.model.runTooltip) { toolTipText = this@View.model.runTooltip.get() }
    }
    val previewButton = JButton("Run with preview").apply {
      addActionListener { this@View.model.runUpgrade(true) }
      myListeners.listen(this@View.model.runTooltip) { toolTipText = this@View.model.runTooltip.get() }
    }
    val messageLabel = JBLabel().apply {
      myListeners.listen(this@View.model.message) {
        val info = this@View.model.message.valueOrNull
        icon = info?.first
        text = info?.second
      }
    }

    val detailsPanel = JBPanel<JBPanel<*>>().apply {
      layout = VerticalLayout(0, SwingConstants.LEFT)
      border = JBUI.Borders.empty(10)
    }
    val content = JBLoadingPanel(BorderLayout(), contentManager).apply {
      val controlsPanel = makeTopComponent()
      add(controlsPanel, BorderLayout.NORTH)
      add(tree, BorderLayout.WEST)
      add(detailsPanel, BorderLayout.CENTER)

      fun updateState(loading: Boolean) {
        refreshButton.isEnabled = !loading
        if (loading) {
          startLoading()
          detailsPanel.removeAll()
          okButton.isEnabled = false
          previewButton.isEnabled = false
        }
        else {
          stopLoading()
          okButton.isEnabled = model.runEnabled.get()
          previewButton.isEnabled = model.runEnabled.get()
          upgradeLabel.text = model.current.upgradeLabelText()
          contentManager.getContent(this)?.displayName = model.current.contentDisplayName()
        }
      }

      myListeners.listen(model.showLoadingState, ::updateState)
      updateState(model.showLoadingState.get())
    }

    init {
      model.treeModel.addTreeModelListener(object : TreeModelAdapter() {
        override fun treeStructureChanged(event: TreeModelEvent?) {
          TreeUtil.expandAll(tree)
        }
      })
      TreeUtil.expandAll(tree)
    }

    private fun makeTopComponent() = JBPanel<JBPanel<*>>().apply {
      layout = HorizontalLayout(5)
      add(upgradeLabel)
      add(versionTextField)
      // TODO(xof): make these buttons come in a platform-dependent order
      add(refreshButton)
      // TODO(xof): make this look like a default button
      add(okButton)
      add(previewButton)
      add(messageLabel)
    }

    private fun refreshDetailsPanel() {
      detailsPanel.removeAll()
      val selectedStep = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
      val label = HtmlLabel().apply { name = "content" }
      setUpAsHtmlLabel(label)
      when (selectedStep) {
        is AgpUpgradeComponentNecessity -> {
          label.text = "<div><b>${selectedStep.treeText()}</b></div><p>${selectedStep.description().replace("\n", "<br>")}</p>"
          detailsPanel.add(label)
        }
        is ToolWindowModel.StepUiPresentation -> {
          val text = StringBuilder("<div><b>${selectedStep.pageHeader}</b></div>")
          val paragraph = selectedStep.helpLinkUrl != null || selectedStep.shortDescription != null
          if (paragraph) text.append("<p>")
          selectedStep.shortDescription?.let { description ->
            text.append(description.replace("\n", "<br>"))
            selectedStep.helpLinkUrl?.let { text.append("  ") }
          }
          selectedStep.helpLinkUrl?.let { url ->
            // TODO(xof): what if we end near the end of the line, and this sticks out in an ugly fashion?
            text.append("<a href='$url'>Read more</a><icon src='ide/external_link_arrow.svg'>.")
          }
          label.text = text.toString()
          detailsPanel.add(label)
          if (selectedStep is ToolWindowModel.StepUiWithComboSelectorPresentation) {
            ComboBox(selectedStep.elements.toTypedArray()).apply {
              name = "selection"
              item = selectedStep.selectedValue
              addActionListener {
                selectedStep.selectedValue = this.item
                tree.repaint()
                refreshDetailsPanel()
              }
              val comboPanel = JBPanel<JBPanel<*>>()
              comboPanel.layout = HorizontalLayout(0)
              comboPanel.add(JBLabel(selectedStep.label).also { it.border = JBUI.Borders.empty(0, 4); it.name = "label" })
              comboPanel.add(this)
              detailsPanel.add(comboPanel)
            }
          }
        }
      }
      detailsPanel.revalidate()
      detailsPanel.repaint()
    }
  }

  private class UpgradeAssistantTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true) {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      if (value is DefaultMutableTreeNode) {
        when (val o = value.userObject) {
          is AgpUpgradeComponentNecessity -> {
            textRenderer.append(o.treeText())
            myCheckbox.let { toolTipText = o.checkboxToolTipText(it.isEnabled, it.isSelected) }
          }
          is ToolWindowModel.StepUiPresentation -> {
            (value.parent as? DefaultMutableTreeNode)?.let { parent ->
              if (parent.userObject == MANDATORY_CODEPENDENT) {
                toolTipText = null
                myCheckbox.isVisible = false
                textRenderer.append("")
                val totalXoffset = myCheckbox.width + myCheckbox.margin.left + myCheckbox.margin.right
                val firstXoffset = 2 * myCheckbox.width / 5 // approximate padding needed to put the bullet centrally in the space
                textRenderer.appendTextPadding(firstXoffset)
                textRenderer.append("\u2022", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
                // Although this looks wrong (one might expect e.g. `totalXoffset - firstXoffset`), it does seem to be the case that
                // SimpleColoredComponent interprets padding from the start of the extent, rather than from the previous end.  Of course this
                // might be a bug, and if the behaviour of SimpleColoredComponent is changed this will break alignment of the Upgrade steps.
                textRenderer.appendTextPadding(totalXoffset)
              }
              else {
                myCheckbox.let {
                  toolTipText = (parent.userObject as? AgpUpgradeComponentNecessity)?.let { n ->
                    n.checkboxToolTipText(it.isEnabled, it.isSelected)
                  }
                }
              }
            }
            textRenderer.append(o.treeText, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
            if (o is ToolWindowModel.StepUiWithComboSelectorPresentation) {
              textRenderer.icon = AllIcons.Actions.Edit
              textRenderer.isIconOnTheRight = true
              textRenderer.iconTextGap = 10
            }
          }
        }
      }
      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }
}

private fun AgpUpgradeRefactoringProcessor.components() = this.componentRefactoringProcessors + this.classpathRefactoringProcessor

private fun AgpUpgradeRefactoringProcessor.activeComponentsForNecessity(necessity: AgpUpgradeComponentNecessity) =
  this.components().filter { it.isEnabled }.filter { it.necessity() == necessity }.filter { !it.isAlwaysNoOpForProject }

fun AgpUpgradeComponentNecessity.treeText() = when (this) {
  MANDATORY_INDEPENDENT -> "Upgrade prerequisites"
  MANDATORY_CODEPENDENT -> "Upgrade"
  OPTIONAL_CODEPENDENT -> "Post-upgrade steps"
  OPTIONAL_INDEPENDENT -> "Optional steps"
  else -> "Irrelevant steps" // TODO(xof): log this -- should never happen
}

fun AgpUpgradeComponentNecessity.checkboxToolTipText(enabled: Boolean, selected: Boolean) =
  if (enabled) null
  else when (this to selected) {
    MANDATORY_INDEPENDENT to true -> "Cannot be deselected while ${MANDATORY_CODEPENDENT.treeText()} is selected"
    MANDATORY_CODEPENDENT to false -> "Cannot be selected while ${MANDATORY_INDEPENDENT.treeText()} is unselected"
    MANDATORY_CODEPENDENT to true -> "Cannot be deselected while ${OPTIONAL_CODEPENDENT.treeText()} is selected"
    OPTIONAL_CODEPENDENT to false -> "Cannot be selected while ${MANDATORY_CODEPENDENT.treeText()} is unselected"
    else -> null // TODO(xof): log this -- should never happen
  }

fun AgpUpgradeComponentNecessity.description() = when (this) {
  MANDATORY_INDEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "You can choose to do them in separate steps, in advance of the Android\n" +
    "Gradle Plugin upgrade itself."
  MANDATORY_CODEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "They must all happen together, at the same time as the Android Gradle Plugin\n" +
    "upgrade itself."
  OPTIONAL_CODEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future, but\n" +
    "only if the Android Gradle Plugin is upgraded to its new version."
  OPTIONAL_INDEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future,\n" +
    "with or without upgrading the Android Gradle Plugin to its new version."
  else -> "These steps are irrelevant to this upgrade (and should not be displayed)" // TODO(xof): log this
}

fun GradleVersion?.upgradeLabelText() = when (this) {
  null -> "Upgrading Android Gradle Plugin from unknown version to"
  else -> "Upgrading Android Gradle Plugin from version $this to"
}

fun GradleVersion?.contentDisplayName() = when(this) {
  null -> "Upgrading project from unknown AGP"
  else -> "Upgrading project from AGP $this"
}
