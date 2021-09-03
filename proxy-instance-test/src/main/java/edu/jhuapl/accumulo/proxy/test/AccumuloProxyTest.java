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
package edu.jhuapl.accumulo.proxy.test;

import edu.jhuapl.accumulo.proxy.ProxyInstance;
import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class AccumuloProxyTest {

  public static void main(String[] args) throws Exception {

    String host = getProperty("accumulo.proxy.host");
    int port = Integer.parseInt(getProperty("accumulo.proxy.port"));
    String user = getProperty("accumulo.user");
    String password = getProperty("accumulo.password");
    String namespace = getProperty("accumulo.namespace");

    ProxyInstance proxyInstance = new ProxyInstance(host, port);
    Connector client = proxyInstance.getConnector(user, new PasswordToken(password));

    String namespacePrefix = namespace == null ? "" : namespace + ".";
    String tableName = namespacePrefix + "myTable" + getSerial();
    try {
      client.tableOperations().create(tableName);
      BatchWriterConfig writerConfig = new BatchWriterConfig();
      try (BatchWriter batchWriter = client.createBatchWriter(tableName, writerConfig)) {

        Mutation m = new Mutation("12345678".getBytes(StandardCharsets.UTF_8));
        m.put("d", "name", "John Doe");
        m.put("d", "ddn", "20/05/1969");
        batchWriter.addMutation(m);

        batchWriter.flush();
      }

      try (Scanner scanner = client.createScanner(tableName, new Authorizations())) {

        for (Map.Entry<Key,Value> entry : scanner) {
          System.out.printf("%s, %s%n", entry.getKey(), entry.getValue());
        }
      }
    } finally {
/*      try {
        client.tableOperations().delete(tableName);
      } catch (TableNotFoundException e) {
        // ok
      }*/
    }

    proxyInstance.close();

  }

  private static String getSerial() {
    return (UUID.randomUUID()).toString().replaceAll("-", "");
  }

  /**
   * Discover accumulo property as system property of environment variable
   * 
   * @param propName
   * @return
   */
  private static String getProperty(String propName) {
    String variableName = propName.toUpperCase().replaceAll("\\.", "_");
    return System.getProperty(propName, System.getenv(variableName));
  }

}
