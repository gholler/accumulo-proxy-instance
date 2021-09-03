/**
 * Copyright 2014-2015 The Johns Hopkins University / Applied Physics Laboratory
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
package edu.jhuapl.accumulo.proxy;

import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MultiBatchWriterTest extends ConnectorBase {
  public static final char END_ROW = 'z';
  public static final char START_ROW = 'a';
  String table1 = "__TEST_TABLE1__";
  String table2 = "__TEST_TABLE2__";

  int noAuthCount;

  @Before
  public void createTable() throws AccumuloException, AccumuloSecurityException, TableExistsException, TableNotFoundException {
    connector.tableOperations().create(table1);
    connector.tableOperations().create(table2);
  }

  @After
  public void tearDown() throws Exception {
    connector.tableOperations().delete(table1);
    connector.tableOperations().delete(table2);
  }

  @Test
  public void testMultiWrite() throws Exception {
    addInsertions();

    validateInsertions();

  }

  @Test
  public void testComplexMutationSequence() throws Exception {
    addInsertions();

    validateInsertions();

    addDeletions();

    addInsertions();

    validateInsertions();
  }

  @Test
  public void testComplexMutationSequence2() throws Exception {
    addInsertions();

    validateInsertions();

    // update the value
    addUpdates();

    // check update is ok
    validateUpdates();
  }

  @Test
  public void testComplexMutationSequence3() throws Exception {
    addInsertions();

    validateInsertions();

    // update the value through delete-then-insert mutations
    addDeleteAndInsert2();

    // check update is ok
    validateUpdates();
  }

  private void addUpdates() throws Exception {
    MultiTableBatchWriter bw = connector.createMultiTableBatchWriter(new BatchWriterConfig());
    for (char ch = START_ROW; ch <= END_ROW; ch++) {
      Mutation m = new Mutation(ch + "_row");
      m.put("fam1", "qual1", "val1:11");
      m.put("fam1", "qual2", "val1:22");
      m.put("fam2", "qual1", "val2:11");
      m.put("fam2", "qual2", "val2:22");
      m.put("fam2", "qual3", "val2:33");
      bw.getBatchWriter(table1).addMutation(m);
      bw.getBatchWriter(table2).addMutation(m);
    }

    bw.close();
  }

  private void addDeleteAndInsert() throws Exception {
    // update as a delete then insert sequence with same mutations
    MultiTableBatchWriter bw = connector.createMultiTableBatchWriter(new BatchWriterConfig());
    for (char ch = START_ROW; ch <= END_ROW; ch++) {
      putDeleteInsert(bw, ch, 1, 1);
      putDeleteInsert(bw, ch, 1, 2);
      putDeleteInsert(bw, ch, 2, 1);
      putDeleteInsert(bw, ch, 2, 2);
      putDeleteInsert(bw, ch, 2, 3);

    }

    bw.close();
  }

  private void addDeleteAndInsert2() throws Exception {
    // update as a delete then insert sequence with same mutations
    MultiTableBatchWriter bw = connector.createMultiTableBatchWriter(new BatchWriterConfig());
    for (char ch = START_ROW; ch <= END_ROW; ch++) {
      putDeleteInsert2(bw, ch, 1, 1);
      putDeleteInsert2(bw, ch, 1, 2);
      putDeleteInsert2(bw, ch, 2, 1);
      putDeleteInsert2(bw, ch, 2, 2);
      putDeleteInsert2(bw, ch, 2, 3);

    }

    bw.close();

  }

  private void putDeleteInsert(MultiTableBatchWriter bw, char ch, int cf, int qual) throws AccumuloException, TableNotFoundException, AccumuloSecurityException {

    Mutation m = new Mutation(ch + "_row");

    String colFam = "fam" + cf;
    String qualifier = "qual" + qual;
    m.putDelete(colFam, qualifier);
    String value = "val" + cf + ":" + qual + "" + qual;
    m.put(colFam, qualifier, value);

    bw.getBatchWriter(table1).addMutation(m);
    bw.getBatchWriter(table2).addMutation(m);
  }

  private void putDeleteInsert2(MultiTableBatchWriter bw, char ch, int cf, int qual) throws AccumuloException, TableNotFoundException,
      AccumuloSecurityException {

    Mutation m1 = new Mutation(ch + "_row");
    String colFam = "fam" + cf;
    String qualifier = "qual" + qual;
    m1.putDelete(colFam, qualifier);
    bw.getBatchWriter(table1).addMutation(m1);
    bw.getBatchWriter(table2).addMutation(m1);

    String value = "val" + cf + ":" + qual + "" + qual;
    Mutation m2 = new Mutation(ch + "_row");
    m2.put(colFam, qualifier, value);
    bw.getBatchWriter(table1).addMutation(m2);
    bw.getBatchWriter(table2).addMutation(m2);
  }

  private void validateUpdates() throws Exception {
        BatchScanner s1 = connector.createBatchScanner(table2, Authorizations.EMPTY, 1);
        validate(noAuthCount, s1, row -> {
                String fam = row.getKey().getColumnFamily().toString().substring(3);
                String qual = row.getKey().getColumnQualifier().toString().substring(4);
                String val = new String(row.getValue().get(), StandardCharsets.UTF_8).substring(3);
                String[] parts = val.split(":");
                Assert.assertEquals("Family and value did not line up.", fam, parts[0]);
                Assert.assertEquals("Qualifier and value did not line up.", qual + qual, parts[1]);
        });
    }

  private void validateInsertions() throws Exception {
        BatchScanner s1 = connector.createBatchScanner(table2, Authorizations.EMPTY, 1);
        validate(noAuthCount, s1,  row -> {
            String fam = row.getKey().getColumnFamily().toString().substring(3);
            String qual = row.getKey().getColumnQualifier().toString().substring(4);
            String val = new String(row.getValue().get(), StandardCharsets.UTF_8).substring(3);
            String[] parts = val.split(":");
            Assert.assertEquals("Family and value did not line up.", fam, parts[0]);
            Assert.assertEquals("Qualifier and value did not line up.", qual, parts[1]);
        });
    }

  private void addDeletions() throws Exception {
    MultiTableBatchWriter bw2 = connector.createMultiTableBatchWriter(new BatchWriterConfig());
    for (char ch = START_ROW; ch <= END_ROW; ch++) {
      Mutation m = new Mutation(ch + "_row");
      m.putDelete("fam1", "qual1");
      m.putDelete("fam1", "qual2");
      m.putDelete("fam2", "qual1");
      m.putDelete("fam2", "qual2");
      m.putDelete("fam2", "qual3");
      bw2.getBatchWriter(table1).addMutation(m);
      bw2.getBatchWriter(table2).addMutation(m);
    }

    bw2.close();
  }

  private void addInsertions() throws AccumuloException, AccumuloSecurityException, TableNotFoundException {
    MultiTableBatchWriter bw1 = connector.createMultiTableBatchWriter(new BatchWriterConfig());
    noAuthCount = 0;
    for (char ch = START_ROW; ch <= END_ROW; ch++) {
      Mutation m = new Mutation(ch + "_row");

      m.put("fam1", "qual1", "val1:1");
      m.put("fam1", "qual2", "val1:2");
      m.put("fam2", "qual1", "val2:1");
      m.put("fam2", "qual2", "val2:2");
      m.put("fam2", "qual3", "val2:3");
      noAuthCount += m.getUpdates().size();
      bw1.getBatchWriter(table1).addMutation(m);
      bw1.getBatchWriter(table2).addMutation(m);
    }
    bw1.close();
  }

  private void validate(int expected, ScannerBase scanner) {
        validate(expected, scanner, row -> {});
    }

  private interface RowValidator {
    void validate(Map.Entry<Key,Value> row);
  }

  private void validate(int expected, ScannerBase scanner, RowValidator rowValidator) {
    int count = 0;
    for (Map.Entry<Key,Value> entry : scanner) {
      rowValidator.validate(entry);
      count++;
    }
    Assert.assertEquals(expected, count);
    scanner.close();
  }
}
