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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service.path;

import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class HierarchyPath extends Path {
  @Override
  public StringBuilder stringPath(StringBuilder builder) {
    return myCapture.stringPath(builder).append(".Hierarchy");
  }

  //<<<Start:Java.ClassBody:1>>>
  CapturePath myCapture;

  // Constructs a default-initialized {@link HierarchyPath}.
  public HierarchyPath() {}


  public CapturePath getCapture() {
    return myCapture;
  }

  public HierarchyPath setCapture(CapturePath v) {
    myCapture = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-97, 105, 91, -121, 127, -117, 92, -93, -86, -99, -76, 110, -87, 57, 47, -43, 119, -110, -118, -83, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new HierarchyPath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      HierarchyPath o = (HierarchyPath)obj;
      e.object(o.myCapture);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      HierarchyPath o = (HierarchyPath)obj;
      o.myCapture = (CapturePath)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
