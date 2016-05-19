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
package com.android.tools.idea.editors.gfxtrace.service;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;

import java.io.IOException;

public final class Hierarchy implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private String myName;
  private ContextID myContext;
  private AtomGroup myRoot;

  // Constructs a default-initialized {@link Hierarchy}.
  public Hierarchy() {}


  public String getName() {
    return myName;
  }

  public Hierarchy setName(String v) {
    myName = v;
    return this;
  }

  public ContextID getContext() {
    return myContext;
  }

  public Hierarchy setContext(ContextID v) {
    myContext = v;
    return this;
  }

  public AtomGroup getRoot() {
    return myRoot;
  }

  public Hierarchy setRoot(AtomGroup v) {
    myRoot = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service", "Hierarchy", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Name", new Primitive("string", Method.String)),
      new Field("Context", new Array("path.ContextID", new Primitive("byte", Method.Uint8), 20)),
      new Field("Root", new Struct(AtomGroup.Klass.INSTANCE.entity())),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new Hierarchy(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Hierarchy o = (Hierarchy)obj;
      e.string(o.myName);
      o.myContext.write(e);

      e.value(o.myRoot);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Hierarchy o = (Hierarchy)obj;
      o.myName = d.string();
      o.myContext = new ContextID(d);

      o.myRoot = new AtomGroup();
      d.value(o.myRoot);
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
