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

package com.google.zetasql.toolkit.catalog.typeparser;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.google.zetasql.StructType.StructField;
import com.google.zetasql.Type;
import com.google.zetasql.TypeFactory;
import com.google.zetasql.ZetaSQLType.TypeKind;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ZetaSQLTypeParserTest {

  @Test
  void parseSimpleTypes() {
    Map<String, TypeKind> inputsToExpectedKinds = new HashMap<>();
    inputsToExpectedKinds.put("STRING", TypeKind.TYPE_STRING);
    inputsToExpectedKinds.put("INT64", TypeKind.TYPE_INT64);
    inputsToExpectedKinds.put("DATE", TypeKind.TYPE_DATE);
    inputsToExpectedKinds.put("NUMERIC", TypeKind.TYPE_NUMERIC);
    inputsToExpectedKinds.put("INTERVAL", TypeKind.TYPE_INTERVAL);
    inputsToExpectedKinds.put("JSON", TypeKind.TYPE_JSON);

    Stream<Executable> assertions =
        inputsToExpectedKinds.entrySet().stream()
            .map(
                inputToExpectedKind ->
                    () ->
                        assertEquals(
                            TypeFactory.createSimpleType(inputToExpectedKind.getValue()),
                            ZetaSQLTypeParser.parse(inputToExpectedKind.getKey()),
                            "Failed to parse type: " + inputToExpectedKind.getKey()));

    assertAll(assertions);
  }

  @Test
  void typesAreCaseInsensitive() {
    Map<String, TypeKind> inputsToExpectedKinds = new HashMap<>();
    inputsToExpectedKinds.put("string", TypeKind.TYPE_STRING);
    inputsToExpectedKinds.put("inT64", TypeKind.TYPE_INT64);
    inputsToExpectedKinds.put("date", TypeKind.TYPE_DATE);
    inputsToExpectedKinds.put("NuMEric", TypeKind.TYPE_NUMERIC);
    inputsToExpectedKinds.put("intErval", TypeKind.TYPE_INTERVAL);
    inputsToExpectedKinds.put("JSOn", TypeKind.TYPE_JSON);

    Stream<Executable> assertions =
        inputsToExpectedKinds.entrySet().stream()
            .map(
                inputToExpectedKind ->
                    () ->
                        assertEquals(
                            TypeFactory.createSimpleType(inputToExpectedKind.getValue()),
                            ZetaSQLTypeParser.parse(inputToExpectedKind.getKey()),
                            "Failed to parse type: " + inputToExpectedKind.getKey()));

    assertAll(assertions);
  }

  @Test
  void parseSimpleTypesWithParameters() {
    assertAll(
        () ->
            assertEquals(
                TypeFactory.createSimpleType(TypeKind.TYPE_STRING),
                ZetaSQLTypeParser.parse("STRING(MAX)"),
                "Failed to parse string type with parameter: STRING(10)"),
        () ->
            assertEquals(
                TypeFactory.createSimpleType(TypeKind.TYPE_NUMERIC),
                ZetaSQLTypeParser.parse("NUMERIC(10, 2)"),
                "Failed to parse numeric type with parameters: NUMERIC(10, 2)"));
  }

  @Test
  void parseArray() {
    String typeStr = "ARRAY<STRING>";
    Type expectedType =
        TypeFactory.createArrayType(TypeFactory.createSimpleType(TypeKind.TYPE_STRING));

    assertEquals(
        expectedType, ZetaSQLTypeParser.parse(typeStr), "Failed to parse type ARRAY<STRING>");
  }

  @Test
  void parseStruct() {
    String typeStr = "STRUCT<f1 STRING, f2 INT64>";
    Type expectedType =
        TypeFactory.createStructType(
            ImmutableList.of(
                new StructField("f1", TypeFactory.createSimpleType(TypeKind.TYPE_STRING)),
                new StructField("f2", TypeFactory.createSimpleType(TypeKind.TYPE_INT64))));

    assertEquals(
        expectedType,
        ZetaSQLTypeParser.parse(typeStr),
        "Failed to parse type STRUCT<f1 STRING, f2 INT64>");
  }

  @Test
  void parseStructWithParameterType() {
    String typeStr = "STRUCT<f1 STRING, f2 NUMERIC(10, 2)>";
    Type expectedType =
        TypeFactory.createStructType(
            ImmutableList.of(
                new StructField("f1", TypeFactory.createSimpleType(TypeKind.TYPE_STRING)),
                new StructField("f2", TypeFactory.createSimpleType(TypeKind.TYPE_NUMERIC))));

    assertEquals(
        expectedType,
        ZetaSQLTypeParser.parse(typeStr),
        "Failed to parse struct type STRUCT<f1 STRING, f2 NUMERIC(10, 2)>");
  }

  @Test
  void parseArrayOfStructs() {
    String typeStr = "ARRAY<STRUCT<f1 STRING, f2 INT64>>";
    Type structType =
        TypeFactory.createStructType(
            ImmutableList.of(
                new StructField("f1", TypeFactory.createSimpleType(TypeKind.TYPE_STRING)),
                new StructField("f2", TypeFactory.createSimpleType(TypeKind.TYPE_INT64))));
    Type expectedType = TypeFactory.createArrayType(structType);

    assertEquals(
        expectedType,
        ZetaSQLTypeParser.parse(typeStr),
        "Failed to array of structs ARRAY<STRUCT<f1 STRING, f2 INT64>>");
  }

  @Test
  void parseStructWithMultipleNestingLevels() {
    String typeStr = "STRUCT<f1 ARRAY<STRUCT<f1_1 ARRAY<STRING>, f1_2 NUMERIC(10, 2)>>>";
    Type stringArrayType =
        TypeFactory.createArrayType(TypeFactory.createSimpleType(TypeKind.TYPE_STRING));
    Type innerStructType =
        TypeFactory.createStructType(
            ImmutableList.of(
                new StructField("f1_1", stringArrayType),
                new StructField("f1_2", TypeFactory.createSimpleType(TypeKind.TYPE_NUMERIC))));
    Type expectedType =
        TypeFactory.createStructType(
            ImmutableList.of(new StructField("f1", TypeFactory.createArrayType(innerStructType))));

    assertEquals(
        expectedType,
        ZetaSQLTypeParser.parse(typeStr),
        "Failed to struct with multiple nesting levels "
            + "STRUCT<f1 ARRAY<STRUCT<f1_1 ARRAY<STRING>, f1_2 NUMERIC(10, 2)>>>");
  }

  @Test
  void parseFailure() {
    String typeStr = "NOT_A_TYPE";

    assertThrows(
        ZetaSQLTypeParseError.class,
        () -> ZetaSQLTypeParser.parse(typeStr),
        "Expected ZetaSQLTypeParser.parse(\"NO_A_TYPE\") to throw RuntimeException");
  }
}
