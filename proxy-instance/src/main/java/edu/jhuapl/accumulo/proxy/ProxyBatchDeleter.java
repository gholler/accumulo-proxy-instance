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
import org.apache.accumulo.core.client.sample.SamplerConfiguration;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.proxy.thrift.AccumuloProxy;
import org.apache.hadoop.io.Text;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ProxyBatchDeleter implements BatchDeleter {

  /**
   * Parent connector.
   */
  ProxyConnector connector;

  /**
   * Local copy of connector's Thrift RPC object to Proxy Server.
   */
  private AccumuloProxy.Iface client;

  /**
   * Token used when communicating with the proxy server.
   */
  private ByteBuffer token;
  private final String tableName;
  private final BatchWriterConfig config;

  List<Range> ranges = new ArrayList<>();

  public ProxyBatchDeleter(ProxyConnector connector, ByteBuffer token, String tableName, BatchWriterConfig config) {
    this.connector = connector;
    this.client = connector.getClient();
    this.token = token;

    this.tableName = tableName;
    this.config = config;
  }

  @Override
    public void delete() throws MutationsRejectedException, TableNotFoundException {
        ranges.forEach(range -> {
            try {
                client.deleteRows(token, tableName, ThriftHelper.toThrift(range.getStartKey()).row, ThriftHelper.toThrift(range.getEndKey()).row);
            } catch (TException e) {
                throw ExceptionFactory.runtimeException(e);
            }
        });
    }

  @Override
  public void setRanges(Collection<Range> ranges) {
    this.ranges.clear();
    this.ranges.addAll(ranges);
  }

  @Override
  public void close() {
    // NOOP
  }

  @Override
  public void addScanIterator(IteratorSetting cfg) {
    // TODO
    throw new UnsupportedOperationException("addScanIterator");
  }

  @Override
  public void removeScanIterator(String iteratorName) {
    // TODO
    throw new UnsupportedOperationException("removeScanIterator");
  }

  @Override
  public void updateScanIteratorOption(String iteratorName, String key, String value) {
    // TODO
    throw new UnsupportedOperationException("updateScanIteratorOption");
  }

  @Override
  public void fetchColumnFamily(Text col) {
    // TODO
    throw new UnsupportedOperationException("fetchColumnFamily");
  }

  @Override
  public void fetchColumn(Text colFam, Text colQual) {
    // TODO
    throw new UnsupportedOperationException("fetchColumn");
  }

  @Override
  public void fetchColumn(IteratorSetting.Column column) {
    // TODO
    throw new UnsupportedOperationException("fetchColumn");
  }

  @Override
  public void clearColumns() {
    // TODO
    throw new UnsupportedOperationException("clearColumns");
  }

  @Override
  public void clearScanIterators() {
    // TODO
    throw new UnsupportedOperationException("clearScanIterators");
  }

  @Override
  public Iterator<Map.Entry<Key,Value>> iterator() {
    // TODO
    throw new UnsupportedOperationException("iterator");
  }

  @Override
  public void setTimeout(long timeOut, TimeUnit timeUnit) {
    // TODO
    throw new UnsupportedOperationException("setTimeout");
  }

  @Override
  public long getTimeout(TimeUnit timeUnit) {
    // TODO
    throw new UnsupportedOperationException("getTimeout");
  }

  @Override
  public Authorizations getAuthorizations() {
    // TODO
    throw new UnsupportedOperationException("getAuthorizations");
  }

  @Override
  public void setSamplerConfiguration(SamplerConfiguration samplerConfig) {
    // TODO
    throw new UnsupportedOperationException("setSamplerConfiguration");
  }

  @Override
  public SamplerConfiguration getSamplerConfiguration() {
    // TODO
    throw new UnsupportedOperationException("getSamplerConfiguration");
  }

  @Override
  public void clearSamplerConfiguration() {
    // TODO
    throw new UnsupportedOperationException("clearSamplerConfiguration");
  }

  @Override
  public void setBatchTimeout(long timeOut, TimeUnit timeUnit) {
    // TODO
    throw new UnsupportedOperationException("setBatchTimeout");
  }

  @Override
  public long getBatchTimeout(TimeUnit timeUnit) {
    // TODO
    throw new UnsupportedOperationException("getBatchTimeout");
  }

  @Override
  public void setClassLoaderContext(String classLoaderContext) {
    // TODO
    throw new UnsupportedOperationException("setClassLoaderContext");
  }

  @Override
  public void clearClassLoaderContext() {
    // TODO
    throw new UnsupportedOperationException("clearClassLoaderContext");
  }

  @Override
  public String getClassLoaderContext() {
    // TODO
    throw new UnsupportedOperationException("getClassLoaderContext");
  }
}
