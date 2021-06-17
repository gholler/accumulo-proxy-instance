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
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

public class BatchScannerTest extends ConnectorBase {

  String table = "__TEST_TABLE__";

  int noAuthCount;

  @Before
  public void createTable() throws AccumuloException, AccumuloSecurityException, TableExistsException, TableNotFoundException {
    connector.tableOperations().create(table);
    BatchWriter bw = connector.createBatchWriter(table, new BatchWriterConfig());
    for (char ch = 'a'; ch <= 'z'; ch++) {
      Mutation m = new Mutation(ch + "_row");

      m.put("fam1", "qual1", "val1:1");
      m.put("fam1", "qual2", "val1:2");
      m.put("fam2", "qual1", "val2:1");
      m.put("fam2", "qual2", "val2:2");
      m.put("fam2", "qual3", "val2:3");
      noAuthCount = m.getUpdates().size();
      bw.addMutation(m);
    }
    bw.close();
  }

  @After
  public void dropTable() throws AccumuloException, AccumuloSecurityException, TableNotFoundException {
    connector.tableOperations().delete(table);
  }

  @Test
  public void simpleTest() throws TableNotFoundException {
    BatchScanner s = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    validate(noAuthCount * 26, s);
  }

  @Test
  public void testRanges() throws TableNotFoundException {
    // first row
    BatchScanner scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("a_row"), noAuthCount, scanner);

    // last row
    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("z_row"), noAuthCount, scanner);

    // "random" inner row
    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("q_row"), noAuthCount, scanner);

    // some actual ranges
    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("d_row", true, "h_row", true), 5 * noAuthCount, scanner);

    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("d_row", false, "h_row", true), 4 * noAuthCount, scanner);

    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("d_row", true, "h_row", false), 4 * noAuthCount, scanner);

    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("d_row", false, "h_row", false), 3 * noAuthCount, scanner);

    // no start
    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range(null, "j_row"), 10 * noAuthCount, scanner);

    // no end
    scanner = connector.createBatchScanner(table, Authorizations.EMPTY, 1);
    testRanges(new Range("j_row", null), 17 * noAuthCount, scanner);

  }

  private void testRanges(List<Range> ranges, int expected, BatchScanner scanner) {
    scanner.setRanges(ranges);
    validate(expected, scanner);
  }

  private void testRanges(Range range, int expected, BatchScanner scanner) {
    testRanges(Arrays.asList(range), expected, scanner);
  }

  private void validate(int expected, ScannerBase scanner) {
    int count = 0;
    for (Entry<Key,Value> entry : scanner) {
      validate(entry);
      count++;
    }
    Assert.assertEquals(expected, count);
    scanner.close();
  }

  /**
   * expects ("famX", "qualY") -> "valX:Y"
   * 
   * @param entry
   *          entry to validate
   */
  private void validate(Entry<Key,Value> entry) {
    String fam = entry.getKey().getColumnFamily().toString().substring(3);
    String qual = entry.getKey().getColumnQualifier().toString().substring(4);
    String val = new String(entry.getValue().get(), StandardCharsets.UTF_8).substring(3);
    String[] parts = val.split(":");
    Assert.assertEquals("Family and value did not line up.", fam, parts[0]);
    Assert.assertEquals("Qualifier and value did not line up.", qual, parts[1]);
  }
}
