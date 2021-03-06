/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 *  @author dsl
 */
public class ModuleSourceOrderEntryImpl extends OrderEntryBaseImpl implements ModuleSourceOrderEntry, WritableOrderEntry, ClonableOrderEntry {
  @NonNls static final String ENTRY_TYPE = "sourceFolder";
  @NonNls private static final String ATTRIBUTE_FOR_TESTS = "forTests";

  ModuleSourceOrderEntryImpl(RootModelImpl rootModel) {
    super(rootModel);
  }

  ModuleSourceOrderEntryImpl(Element element, RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    if (!element.getName().equals(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      throw new InvalidDataException();
    }
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR, ENTRY_TYPE);
    element.setAttribute(ATTRIBUTE_FOR_TESTS, Boolean.FALSE.toString()); // compatibility with prev builds
    rootElement.addContent(element);
  }

  public boolean isValid() {
    return !isDisposed();
  }

  @NotNull
  public Module getOwnerModule() {
    return getRootModel().getModule();
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleSourceOrderEntry(this, initialValue);
  }

  public String getPresentableName() {
    return ProjectBundle.message("project.root.module.source");
  }


  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    if (OrderRootType.SOURCES.equals(type)) {
      return getRootModel().getSourceRoots();
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    final ArrayList<String> result = new ArrayList<String>();
    if (OrderRootType.SOURCES.equals(type)) {
      final ContentEntry[] content = getRootModel().getContentEntries();
      for (ContentEntry contentEntry : content) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          final String url = sourceFolder.getUrl();
          result.add(url);
        }
      }
      return ArrayUtil.toStringArray(result);
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleSourceOrderEntryImpl(rootModel);
  }

  public boolean isSynthetic() {
    return true;
  }
}
