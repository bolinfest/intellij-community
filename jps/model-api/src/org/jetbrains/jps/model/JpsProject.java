package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.module.JpsTypedModule;

import java.util.List;

/**
 * @author nik
 */
public interface JpsProject extends JpsCompositeElement, JpsReferenceableElement<JpsProject> {

  @NotNull
  <P extends JpsElement, ModuleType extends JpsModuleType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModule addModule(@NotNull String name, @NotNull ModuleType moduleType);

  void addModule(@NotNull JpsModule module);

  @NotNull
  List<JpsModule> getModules();

  @NotNull
  <P extends JpsElement>
  Iterable<JpsTypedModule<P>> getModules(JpsModuleType<P> type);

  @NotNull
  <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType libraryType);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsSdkReferencesTable getSdkReferencesTable();
}
