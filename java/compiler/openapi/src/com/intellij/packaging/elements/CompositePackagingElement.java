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
package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class CompositePackagingElement<S> extends PackagingElement<S> implements RenameablePackagingElement {
  private final List<PackagingElement<?>> myChildren = new ArrayList<PackagingElement<?>>();
  private List<PackagingElement<?>> myUnmodifiableChildren;

  protected CompositePackagingElement(PackagingElementType type) {
    super(type);
  }

  public <T extends PackagingElement<?>> T addOrFindChild(@NotNull T child) {
    for (PackagingElement<?> element : myChildren) {
      if (element.isEqualTo(child)) {
        if (element instanceof CompositePackagingElement) {
          final List<PackagingElement<?>> children = ((CompositePackagingElement<?>)child).getChildren();
          ((CompositePackagingElement<?>)element).addOrFindChildren(children);
        }
        //noinspection unchecked
        return (T) element;
      }
    }
    myChildren.add(child);
    myUnmodifiableChildren = null;
    return child;
  }

  public List<? extends PackagingElement<?>> addOrFindChildren(Collection<? extends PackagingElement<?>> children) {
    List<PackagingElement<?>> added = new ArrayList<PackagingElement<?>>();
    for (PackagingElement<?> child : children) {
      added.add(addOrFindChild(child));
    }
    myUnmodifiableChildren = null;
    return added;
  }

  @Nullable
  public PackagingElement<?> moveChild(int index, int direction) {
    int target = index + direction;
    if (0 <= index && index < myChildren.size() && 0 <= target && target < myChildren.size()) {
      final PackagingElement<?> element1 = myChildren.get(index);
      final PackagingElement<?> element2 = myChildren.get(target);
      myChildren.set(index, element2);
      myChildren.set(target, element1);
      myUnmodifiableChildren = null;
      return element1;
    }
    return null;
  }

  public void removeChild(@NotNull PackagingElement<?> child) {
    myChildren.remove(child);
    myUnmodifiableChildren = null;
  }

  public void removeChildren(@NotNull Collection<? extends PackagingElement<?>> children) {
    myChildren.removeAll(children);
    myUnmodifiableChildren = null;
  }

  @NotNull
  public List<PackagingElement<?>> getChildren() {
    if (myUnmodifiableChildren == null) {
      myUnmodifiableChildren = Collections.unmodifiableList(myChildren);
    }
    return myUnmodifiableChildren;
  }

  public boolean canBeRenamed() {
    return true;
  }

  protected List<? extends Generator> computeChildrenGenerators(PackagingElementResolvingContext resolvingContext,
                                                                final AntCopyInstructionCreator copyInstructionCreator,
                                                                final ArtifactAntGenerationContext generationContext, ArtifactType artifactType) {
    final List<Generator> generators = new ArrayList<Generator>();
    for (PackagingElement<?> child : myChildren) {
      generators.addAll(child.computeAntInstructions(resolvingContext, copyInstructionCreator, generationContext, artifactType));
    }
    return generators;
  }

  protected void computeChildrenInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                             @NotNull PackagingElementResolvingContext resolvingContext,
                                             @NotNull ArtifactIncrementalCompilerContext compilerContext, ArtifactType artifactType) {
    for (PackagingElement<?> child : myChildren) {
      child.computeIncrementalCompilerInstructions(creator, resolvingContext, compilerContext, artifactType);
    }
  }

  public void removeAllChildren() {
    myChildren.clear();
    myUnmodifiableChildren = null;
  }
}
