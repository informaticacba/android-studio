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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.perflib.heap.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class HeapDumpReferenceObject implements ReferenceObject {

  @NotNull private final InstanceObject myReferrer;

  @NotNull private final List<String> myReferencingFieldNames;

  public HeapDumpReferenceObject(@NotNull InstanceObject referrer, @NotNull List<String> referencingFieldNames) {
    myReferrer = referrer;
    myReferencingFieldNames = referencingFieldNames;
  }

  @NotNull
  @Override
  public String getDisplayLabel() {
    return myReferrer.getDisplayLabel();
  }

  @Nullable
  @Override
  public String getClassName() {
    return myReferrer.getClassName();
  }

  @NotNull
  @Override
  public List<String> getReferenceFieldNames() {
    return myReferencingFieldNames;
  }

  @Override
  public int getShallowSize() {
    return myReferrer.getShallowSize();
  }

  @Override
  public long getRetainedSize() {
    return myReferrer.getRetainedSize();
  }

  @Override
  public int getDepth() {
    return myReferrer.getDepth();
  }

  @Override
  public boolean getIsArray() {
    return myReferrer.getIsArray();
  }

  @Override
  public boolean getIsRoot() {
    return myReferrer.getIsRoot();
  }

  @NotNull
  @Override
  public List<ReferenceObject> getReferences() {
    return myReferrer.getReferences();
  }
}
