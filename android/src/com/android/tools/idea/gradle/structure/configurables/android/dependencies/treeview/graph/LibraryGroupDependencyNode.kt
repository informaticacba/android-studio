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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency
import com.intellij.ui.treeStructure.SimpleNode

class LibraryGroupDependencyNode(parent: AbstractPsNode,
                                 val library: PsLibraryKey,
                                 val dependencies: List<PsLibraryAndroidDependency>
) : AbstractDependencyNode<PsLibraryAndroidDependency>(parent, dependencies) {
  internal var children: List<SimpleNode> = listOf()
  override fun getChildren(): Array<SimpleNode> = children.toTypedArray()
  override fun nameOf(model: PsLibraryAndroidDependency): String = model.spec.getDisplayText(true, false)
}