/*
 * Copyright 2023 Google LLC All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zetasql.toolkit.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.google.zetasql.*;
import com.google.zetasql.ZetaSQLType.TypeKind;
import com.google.zetasql.resolvedast.ResolvedCreateStatementEnums.CreateMode;
import com.google.zetasql.toolkit.catalog.exceptions.CatalogResourceAlreadyExists;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogOperationsTest {

  SimpleCatalog testCatalog;

  private SimpleCatalog createSampleCatalog(String name) {
    SimpleCatalog catalog = new SimpleCatalog(name);
    SimpleTable sampleTable =
        new SimpleTable(
            "sample",
            ImmutableList.of(
                new SimpleColumn(
                    "sample", "column", TypeFactory.createSimpleType(TypeKind.TYPE_STRING))));
    catalog.addSimpleTable(sampleTable);

    return catalog;
  }

  @BeforeEach
  void setUp() {
    this.testCatalog = this.createSampleCatalog("catalog");
    this.testCatalog.addSimpleCatalog(this.createSampleCatalog("nested"));
  }

  private Table assertTableExists(SimpleCatalog catalog, List<String> tablePath, String message) {
    return assertDoesNotThrow(() -> catalog.findTable(tablePath), message);
  }

  private void assertTableDoesNotExist(
      SimpleCatalog catalog, List<String> tablePath, String message) {
    assertThrows(NotFoundException.class, () -> catalog.findTable(tablePath), message);
  }

  @Test
  void testCreateTableInCatalog() {
    String tableName = "newTable";
    String fullTableName = "qualified.newTable";
    SimpleTable newTable =
        new SimpleTable(
            tableName,
            ImmutableList.of(
                new SimpleColumn(
                    tableName, "column", TypeFactory.createSimpleType(TypeKind.TYPE_STRING))));
    newTable.setFullName(fullTableName);

    CatalogOperations.createTableInCatalog(
        this.testCatalog, newTable.getFullName(), newTable, CreateMode.CREATE_DEFAULT);

    assertTableExists(this.testCatalog, ImmutableList.of("qualified.newTable"), "Expected created table to exist");
  }

  @Test
  void testDeleteTableFromCatalog() {

    CatalogOperations.deleteTableFromCatalog(this.testCatalog, "sample");

    assertTableDoesNotExist(
        this.testCatalog, ImmutableList.of("sample"),
        "Expected table to have been deleted");

  }

  @Test
  void testTableAlreadyExists() {
    String tableName = "sample";
    SimpleTable newTable =
        new SimpleTable(
            tableName,
            ImmutableList.of(
                new SimpleColumn(
                    tableName, "column", TypeFactory.createSimpleType(TypeKind.TYPE_INT64))));

    assertThrows(
        CatalogResourceAlreadyExists.class,
        () ->
            CatalogOperations.createTableInCatalog(
                this.testCatalog, newTable.getFullName(), newTable, CreateMode.CREATE_DEFAULT));
  }

  @Test
  void testReplaceTable() {
    String tableName = "sample";
    SimpleTable newTable =
        new SimpleTable(
            tableName,
            ImmutableList.of(
                new SimpleColumn(
                    tableName, "column", TypeFactory.createSimpleType(TypeKind.TYPE_INT64))));


    CatalogOperations.createTableInCatalog(
        this.testCatalog, newTable.getFullName(), newTable, CreateMode.CREATE_OR_REPLACE);

    Table foundTable = assertTableExists(
        this.testCatalog, ImmutableList.of("sample"), "Expected replaced table to exist");

    assertEquals(
        foundTable.getColumn(0).getType(),
        TypeFactory.createSimpleType(TypeKind.TYPE_INT64),
        "Expected table to have been replaced");
  }

  @Test
  void testCreateTableIfNotExists_ExistingTable() throws NotFoundException {
    String tableName = "sample";
    SimpleTable newTable =
        new SimpleTable(
            tableName,
            ImmutableList.of(
                new SimpleColumn(
                    tableName, "column", TypeFactory.createSimpleType(TypeKind.TYPE_INT64))));

    List<String> sampleTablePath = ImmutableList.of("sample");

    Table originalTable = this.testCatalog.findTable(sampleTablePath);

    CatalogOperations.createTableInCatalog(
        this.testCatalog, newTable.getFullName(), newTable, CreateMode.CREATE_IF_NOT_EXISTS);

    Table foundTable =
        assertTableExists(
            this.testCatalog,
            sampleTablePath,
            "Expected table to still exist after CREATE IF NOT EXISTS");

    assertSame(
        originalTable,
        foundTable,
        "Expected existing table to not have changed after CREATE IF NOT EXISTS");
  }

  @Test
  void testCreateTableIfNotExists_NewTable() {
    String tableName = "newTable";
    SimpleTable newTable =
        new SimpleTable(
            tableName,
            ImmutableList.of(
                new SimpleColumn(
                    tableName, "column", TypeFactory.createSimpleType(TypeKind.TYPE_INT64))));

    CatalogOperations.createTableInCatalog(
        this.testCatalog, newTable.getFullName(), newTable, CreateMode.CREATE_IF_NOT_EXISTS);

    assertTableExists(
        this.testCatalog,
        ImmutableList.of("newTable"),
        "Expected table to have been created");
  }

  private Function assertFunctionExists(SimpleCatalog catalog, String fullName, String message) {
    // TODO: switch to using SimpleCatalog.findFunction once available
    Function function = catalog.getFunctionByFullName(fullName);
    assertNotNull(function, message);
    return function;
  }

  private void assertFunctionDoesNotExist(SimpleCatalog catalog, String fullName, String message) {
    Function function = catalog.getFunctionByFullName(fullName);
    assertNull(function, message);
  }

  @Test
  void testCreateFunctionInCatalog() {
    FunctionInfo newFunction =
        FunctionInfo.newBuilder()
            .setNamePath(ImmutableList.of("qualified.newFunction"))
            .setGroup("UDF")
            .setMode(ZetaSQLFunctions.FunctionEnums.Mode.SCALAR)
            .setSignatures(
                ImmutableList.of(
                    new FunctionSignature(
                        new FunctionArgumentType(
                            TypeFactory.createSimpleType(TypeKind.TYPE_STRING)),
                        ImmutableList.of(),
                        -1)))
            .build();

    CatalogOperations.createFunctionInCatalog(
        this.testCatalog, "qualified.newFunction", newFunction, CreateMode.CREATE_DEFAULT);

    assertFunctionExists(
        this.testCatalog, "UDF:qualified.newFunction", "Expected created function to exist");

  }

  @Test
  void testDeleteFunctionFromCatalog() {
    FunctionInfo newFunction =
        FunctionInfo.newBuilder()
            .setNamePath(ImmutableList.of("qualified.newFunction"))
            .setGroup("UDF")
            .setMode(ZetaSQLFunctions.FunctionEnums.Mode.SCALAR)
            .setSignatures(
                ImmutableList.of(
                    new FunctionSignature(
                        new FunctionArgumentType(
                            TypeFactory.createSimpleType(TypeKind.TYPE_STRING)),
                        ImmutableList.of(),
                        -1)))
            .build();

    CatalogOperations.createFunctionInCatalog(
        this.testCatalog, "qualified.newFunction", newFunction, CreateMode.CREATE_DEFAULT);
    CatalogOperations.deleteFunctionFromCatalog(this.testCatalog, "qualified.newFunction");

    assertFunctionDoesNotExist(
        this.testCatalog,
        "UDF:qualified.newFunction",
        "Expected function to have been deleted");

  }

  private TableValuedFunction assertTVFExists(SimpleCatalog catalog, String name, String message) {
    // TODO: switch to using SimpleCatalog.findTableValuedFunction once available
    TableValuedFunction tvf = catalog.getTVFByName(name);
    assertNotNull(tvf, message);
    return tvf;
  }

  private void assertTVFDoesNotExist(SimpleCatalog catalog, String name, String message) {
    TableValuedFunction tvf = catalog.getTVFByName(name);
    assertNull(tvf, message);
  }

  @Test
  void testCreateTVFInCatalog() {
    TVFInfo newTVF =
        TVFInfo.newBuilder()
            .setNamePath(ImmutableList.of("qualified.newTVF"))
            .setSignature(
                new FunctionSignature(
                    new FunctionArgumentType(
                        ZetaSQLFunctions.SignatureArgumentKind.ARG_TYPE_RELATION),
                    ImmutableList.of(),
                    -1))
            .setOutputSchema(
                TVFRelation.createValueTableBased(
                    TypeFactory.createSimpleType(TypeKind.TYPE_STRING)))
            .build();

    CatalogOperations.createTVFInCatalog(
        this.testCatalog, "qualified.newTVF", newTVF, CreateMode.CREATE_DEFAULT);

    assertTVFExists(testCatalog, "qualified.newTVF", "Expected created function to exist");
  }

  @Test
  void testDeleteTVFFromCatalog() {
    TVFInfo tvf = TVFInfo.newBuilder()
        .setNamePath(ImmutableList.of("qualified.newTVF"))
        .setSignature(
            new FunctionSignature(
                new FunctionArgumentType(
                    ZetaSQLFunctions.SignatureArgumentKind.ARG_TYPE_RELATION),
                ImmutableList.of(),
                -1))
        .setOutputSchema(
            TVFRelation.createValueTableBased(
                TypeFactory.createSimpleType(TypeKind.TYPE_STRING)))
        .build();

    CatalogOperations.createTVFInCatalog(
        this.testCatalog, "qualified.newTVF", tvf, CreateMode.CREATE_DEFAULT);
    CatalogOperations.deleteTVFFromCatalog(this.testCatalog, "qualified.newTVF");

    assertTVFDoesNotExist(
        this.testCatalog, "qualified.newTVF", "Expected function to have been deleted");
  }

  private Procedure assertProcedureExists(
      SimpleCatalog catalog, List<String> procedurePath, String message) {
    return assertDoesNotThrow(() -> catalog.findProcedure(procedurePath), message);
  }

  private void assertProcedureDoesNotExist(
      SimpleCatalog catalog, List<String> procedurePath, String message) {
    assertThrows(NotFoundException.class, () -> catalog.findProcedure(procedurePath), message);
  }

  @Test
  void testCreateProcedureInCatalog() {
    ProcedureInfo newProcedure =
        new ProcedureInfo(
            ImmutableList.of("qualified.newProcedure"),
            new FunctionSignature(
                new FunctionArgumentType(ZetaSQLFunctions.SignatureArgumentKind.ARG_TYPE_VOID),
                ImmutableList.of(),
                -1));

    List<String> newProcedurePath1 = ImmutableList.of("qualified.newProcedure");
    List<String> newProcedurePath2 = ImmutableList.of("qualified", "newProcedure");

    CatalogOperations.createProcedureInCatalog(
        this.testCatalog, "qualified.newProcedure", newProcedure, CreateMode.CREATE_DEFAULT);

    assertAll(
        () ->
            assertProcedureExists(
                this.testCatalog, newProcedurePath1, "Expected created procedure to exist"),
        () ->
            assertProcedureExists(
                this.testCatalog, newProcedurePath2, "Expected created procedure to exist"));
  }

  @Test
  void testDeleteProcedureFromCatalog() {
    ProcedureInfo newProcedure =
        new ProcedureInfo(
            ImmutableList.of("qualified.newProcedure"),
            new FunctionSignature(
                new FunctionArgumentType(ZetaSQLFunctions.SignatureArgumentKind.ARG_TYPE_VOID),
                ImmutableList.of(),
                -1));

    CatalogOperations.createProcedureInCatalog(
        this.testCatalog, "qualified.newProcedure", newProcedure, CreateMode.CREATE_DEFAULT);

    List<String> procedurePath1 = ImmutableList.of("newProcedure");
    List<String> procedurePath2 = ImmutableList.of("qualified", "newProcedure");

    CatalogOperations.deleteProcedureFromCatalog(this.testCatalog, "qualified.newProcedure");

    assertAll(
        () ->
            assertProcedureDoesNotExist(
                this.testCatalog, procedurePath1, "Expected procedure to have been deleted"),
        () ->
            assertProcedureDoesNotExist(
                this.testCatalog,
                procedurePath2,
                "Expected procedure to have been deleted"));
  }

  @Test
  void testCopyCatalog() {
    SimpleCatalog copiedCatalog = CatalogOperations.copyCatalog(this.testCatalog);

    List<String> sampleTablePath = ImmutableList.of("sample");
    List<String> nestedTablePath = ImmutableList.of("nested", "sample");

    Table copiedTable =
        assertTableExists(
            copiedCatalog, sampleTablePath, "Existing table was not found in copied catalog");
    assertTableExists(
        copiedCatalog, nestedTablePath, "Existing table was not found in copied catalog");

    assertAll(
        () ->
            assertEquals(
                "sample",
                copiedTable.getName(),
                "Table name in copied catalog didn't match original"),
        () ->
            assertEquals(
                "column",
                copiedTable.getColumn(0).getName(),
                "Column name in copied catalog didn't match original"));
  }
}
