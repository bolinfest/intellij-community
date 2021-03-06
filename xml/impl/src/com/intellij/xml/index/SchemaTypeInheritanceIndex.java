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
package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.EncoderDecoder;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/4/12
 * Time: 6:29 PM
 *
 * map: tag name->file url
 */
public class SchemaTypeInheritanceIndex extends XmlIndex<Set<SchemaTypeInfo>> {
  private static final ID<String, Set<SchemaTypeInfo>> NAME = ID.create("SchemaTypeInheritance");

  public static List<Set<SchemaTypeInfo>> getDirectChildrenOfType(final Project project, final String ns, final String name) {
    final List<Set<SchemaTypeInfo>>
      list = FileBasedIndex.getInstance().getValues(NAME, NsPlusTag.INSTANCE.encode(Pair.create(ns, name)), createFilter(project));
    return list;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @NotNull
  @Override
  public ID<String, Set<SchemaTypeInfo>> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Set<SchemaTypeInfo>, FileContent> getIndexer() {
    return new DataIndexer<String, Set<SchemaTypeInfo>, FileContent>() {
      @NotNull
      @Override
      public Map<String, Set<SchemaTypeInfo>> map(FileContent inputData) {
        final Map<String, Set<SchemaTypeInfo>> map = new HashMap<String, Set<SchemaTypeInfo>>();
        final MultiMap<SchemaTypeInfo,SchemaTypeInfo> multiMap =
          XsdComplexTypeInfoBuilder.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        for (SchemaTypeInfo key : multiMap.keySet()) {
          map.put(NsPlusTag.INSTANCE.encode(Pair.create(key.getNamespaceUri(), key.getTagName())), new HashSet<SchemaTypeInfo>(multiMap.get(key)));
        }
        return map;
      }
    };
  }

  @Override
  public DataExternalizer<Set<SchemaTypeInfo>> getValueExternalizer() {
    return new DataExternalizer<Set<SchemaTypeInfo>>() {
      @Override
      public void save(DataOutput out, Set<SchemaTypeInfo> value) throws IOException {
        out.writeInt(value.size());
        for (SchemaTypeInfo key : value) {
          out.writeUTF(key.getNamespaceUri());
          out.writeUTF(key.getTagName());
          out.writeBoolean(key.isIsTypeName());
        }
      }

      @Override
      public Set<SchemaTypeInfo> read(DataInput in) throws IOException {
        final Set<SchemaTypeInfo> set = new HashSet<SchemaTypeInfo>();
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
          final String nsUri = in.readUTF();
          final String tagName = in.readUTF();
          final boolean isType = in.readBoolean();
          set.add(new SchemaTypeInfo(tagName, isType, nsUri));
        }
        return set;
      }
    };
  }

  private static class NsPlusTag implements EncoderDecoder<Pair<String, String>, String> {
    private final static NsPlusTag INSTANCE = new NsPlusTag();
    private final static char ourSeparator = ':';

    @Override
    public String encode(Pair<String, String> pair) {
      return pair.getFirst() + ourSeparator + pair.getSecond();
    }

    @Override
    public Pair<String, String> decode(String s) {
      final int i = s.indexOf(ourSeparator);
      return i <= 0 ? Pair.create("", s) : Pair.create(s.substring(0, i), s.substring(i + 1));
    }
  }
}
