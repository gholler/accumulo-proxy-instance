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
import org.apache.accumulo.core.client.admin.NamespaceOperations;
import org.apache.accumulo.core.iterators.IteratorUtil;
import org.apache.accumulo.proxy.thrift.AccumuloProxy;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Map;
import java.util.SortedSet;

public class ProxyNamespaceOperations implements NamespaceOperations {
  private final ProxyConnector connector;
  private final AccumuloProxy.Iface client;
  private final ByteBuffer token;

  public ProxyNamespaceOperations(ProxyConnector connector, ByteBuffer token) {
    this.connector = connector;
    this.client = connector.getClient();
    this.token = token;
  }

  @Override
  public String systemNamespace() {
    // TODO
    throw new UnsupportedOperationException("systemNamespace");
  }

  @Override
  public String defaultNamespace() {
    // TODO
    throw new UnsupportedOperationException("defaultNamespace");
  }

  @Override
  public SortedSet<String> list() throws AccumuloException, AccumuloSecurityException {
    // TODO
    throw new UnsupportedOperationException("list");
  }

  @Override
  public boolean exists(String namespace) {
    try {
      return client.namespaceExists(token, namespace);
    } catch (TException e) {
      throw ExceptionFactory.runtimeException(e);
    }
  }

  @Override
  public void create(String namespace) throws AccumuloException, AccumuloSecurityException, NamespaceExistsException {
    try {
      client.createNamespace(token, namespace);
    } catch (org.apache.accumulo.proxy.thrift.NamespaceExistsException e) {
      throw ExceptionFactory.namespaceExistsException(namespace, e);
    } catch (TException e) {
      throw ExceptionFactory.runtimeException(e);
    }
  }

  @Override
  public void delete(String namespace) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException, NamespaceNotEmptyException {
    try {
      client.deleteNamespace(token, namespace);
    } catch (org.apache.accumulo.proxy.thrift.NamespaceNotFoundException nnfe) {
      throw ExceptionFactory.namespaceNotFoundException(namespace, nnfe);
    } catch (TException e) {
      throw ExceptionFactory.runtimeException(e);
    }
  }

  @Override
  public void rename(String oldNamespaceName, String newNamespaceName) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException,
      NamespaceExistsException {
    // TODO
    throw new UnsupportedOperationException("rename");
  }

  @Override
  public void setProperty(String namespace, String property, String value) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("setProperty");
  }

  @Override
  public void removeProperty(String namespace, String property) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("removeProperty");
  }

  @Override
  public Iterable<Map.Entry<String,String>> getProperties(String namespace) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("getProperties");
  }

  @Override
  public Map<String,String> namespaceIdMap() throws AccumuloException, AccumuloSecurityException {
    // TODO
    throw new UnsupportedOperationException("namespaceIdMap");
  }

  @Override
  public void attachIterator(String namespace, IteratorSetting setting) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("attachIterator");
  }

  @Override
  public void attachIterator(String namespace, IteratorSetting setting, EnumSet<IteratorUtil.IteratorScope> scopes) throws AccumuloException,
      AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("attachIterator");
  }

  @Override
  public void removeIterator(String namespace, String name, EnumSet<IteratorUtil.IteratorScope> scopes) throws AccumuloException, AccumuloSecurityException,
      NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("removeIterator");
  }

  @Override
  public IteratorSetting getIteratorSetting(String namespace, String name, IteratorUtil.IteratorScope scope) throws AccumuloException,
      AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("getIteratorSetting");
  }

  @Override
  public Map<String,EnumSet<IteratorUtil.IteratorScope>> listIterators(String namespace) throws AccumuloException, AccumuloSecurityException,
      NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("listIterators");
  }

  @Override
  public void checkIteratorConflicts(String namespace, IteratorSetting setting, EnumSet<IteratorUtil.IteratorScope> scopes) throws AccumuloException,
      AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("checkIteratorConflicts");
  }

  @Override
  public int addConstraint(String namespace, String constraintClassName) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("addConstraint");
  }

  @Override
  public void removeConstraint(String namespace, int id) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("removeConstraint");
  }

  @Override
  public Map<String,Integer> listConstraints(String namespace) throws AccumuloException, AccumuloSecurityException, NamespaceNotFoundException {
    // TODO
    throw new UnsupportedOperationException("listConstraints");
  }

  @Override
  public boolean testClassLoad(String namespace, String className, String asTypeName) throws AccumuloException, AccumuloSecurityException,
      NamespaceNotFoundException {
    try {
      return client.testNamespaceClassLoad(token, namespace, className, asTypeName);
    } catch (org.apache.accumulo.proxy.thrift.NamespaceNotFoundException e) {
      throw ExceptionFactory.namespaceNotFoundException(namespace, e);
    } catch (TException e) {
      throw ExceptionFactory.runtimeException(e);
    }
  }
}
