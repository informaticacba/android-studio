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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import org.mockito.Mockito.mock
import javax.swing.JComponent

open class MockTableView : TableView {

  val listeners = mutableListOf<TableView.Listener>()

  override val component = mock(JComponent::class.java)

  override fun resetView() { }

  override fun startTableLoading() { }

  override fun showTableColumns(columns: List<SqliteColumn>) { }

  override fun stopTableLoading() { }

  override fun reportError(message: String, t: Throwable?) { }

  override fun setFetchPreviousRowsButtonState(enable: Boolean) { }

  override fun setFetchNextRowsButtonState(enable: Boolean) { }

  override fun setEditable(isEditable: Boolean) { }

  override fun addListener(listener: TableView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: TableView.Listener) {
    listeners.remove(listener)
  }

  override fun updateRows(rowDiffOperations: List<RowDiffOperation>) { }

  override fun setEmptyText(text: String) { }

  override fun showPageSizeValue(maxRowCount: Int) { }
}