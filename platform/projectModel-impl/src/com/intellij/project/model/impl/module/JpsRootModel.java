/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.project.model.impl.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.RootModelBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.project.model.impl.module.content.JpsContentEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JpsRootModel extends RootModelBase implements ModuleRootModel {
  private Module myModule;
  private JpsModule myJpsModule;
  public VirtualFilePointer myExplodedDirectoryPointer;
  private List<ContentEntry> myContentEntries;
  private List<OrderEntry> myOrderEntries;

  public JpsRootModel(Module module, JpsModule jpsModule) {
    myModule = module;
    myJpsModule = jpsModule;
    myContentEntries = new ArrayList<ContentEntry>();
    for (String contentRoot : myJpsModule.getContentRootsList().getUrls()) {
      myContentEntries.add(new JpsContentEntry(jpsModule, this, contentRoot));
    }
    myOrderEntries = new ArrayList<OrderEntry>();
    for (JpsDependencyElement element : myJpsModule.getDependenciesList().getDependencies()) {
      myOrderEntries.add(JpsOrderEntryFactory.createOrderEntry(this, element));
    }
  }

  public JpsModule getJpsModule() {
    return myJpsModule;
  }

  @NotNull
  @Override
  public Module getModule() {
    return myModule;
  }

  @Override
  protected Collection<ContentEntry> getContent() {
    return myContentEntries;
  }

  @NotNull
  @Override
  public OrderEntry[] getOrderEntries() {
    return myOrderEntries.toArray(new OrderEntry[myOrderEntries.size()]);
  }

  @Override
  public VirtualFile getExplodedDirectory() {
    throw new UnsupportedOperationException("'getExplodedDirectory' not implemented in " + getClass().getName());
  }

  @Override
  public String getExplodedDirectoryUrl() {
    throw new UnsupportedOperationException("'getExplodedDirectoryUrl' not implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public VirtualFile[] getRootPaths(OrderRootType rootType) {
    throw new UnsupportedOperationException("'getRootPaths' not implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public String[] getRootUrls(OrderRootType rootType) {
    throw new UnsupportedOperationException("'getRootUrls' not implemented in " + getClass().getName());
  }

  @Override
  public <T> T getModuleExtension(Class<T> klass) {
    throw new UnsupportedOperationException("'getModuleExtension' not implemented in " + getClass().getName());
  }

  public Project getProject() {
    return myModule.getProject();
  }

  public boolean isExcludeExplodedDirectory() {
    return false;
  }
}
