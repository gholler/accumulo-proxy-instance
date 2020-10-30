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

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.security.tokens.AuthenticationToken;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.proxy.thrift.AccumuloProxy;
import org.apache.accumulo.proxy.thrift.ActiveCompaction;
import org.apache.accumulo.proxy.thrift.ActiveScan;
import org.apache.accumulo.proxy.thrift.BatchScanOptions;
import org.apache.accumulo.proxy.thrift.ColumnUpdate;
import org.apache.accumulo.proxy.thrift.CompactionStrategyConfig;
import org.apache.accumulo.proxy.thrift.ConditionalStatus;
import org.apache.accumulo.proxy.thrift.ConditionalUpdates;
import org.apache.accumulo.proxy.thrift.ConditionalWriterOptions;
import org.apache.accumulo.proxy.thrift.DiskUsage;
import org.apache.accumulo.proxy.thrift.IteratorScope;
import org.apache.accumulo.proxy.thrift.IteratorSetting;
import org.apache.accumulo.proxy.thrift.Key;
import org.apache.accumulo.proxy.thrift.KeyValueAndPeek;
import org.apache.accumulo.proxy.thrift.MutationsRejectedException;
import org.apache.accumulo.proxy.thrift.NamespaceExistsException;
import org.apache.accumulo.proxy.thrift.NamespaceNotEmptyException;
import org.apache.accumulo.proxy.thrift.NamespaceNotFoundException;
import org.apache.accumulo.proxy.thrift.NamespacePermission;
import org.apache.accumulo.proxy.thrift.NoMoreEntriesException;
import org.apache.accumulo.proxy.thrift.PartialKey;
import org.apache.accumulo.proxy.thrift.Range;
import org.apache.accumulo.proxy.thrift.ScanOptions;
import org.apache.accumulo.proxy.thrift.ScanResult;
import org.apache.accumulo.proxy.thrift.SystemPermission;
import org.apache.accumulo.proxy.thrift.TableExistsException;
import org.apache.accumulo.proxy.thrift.TableNotFoundException;
import org.apache.accumulo.proxy.thrift.TablePermission;
import org.apache.accumulo.proxy.thrift.TimeType;
import org.apache.accumulo.proxy.thrift.UnknownScanner;
import org.apache.accumulo.proxy.thrift.UnknownWriter;
import org.apache.accumulo.proxy.thrift.WriterOptions;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.LoggerFactory;

/**
 * A proxy instance that uses the Accumulo Proxy Thrift interface to fulfill the Accumulo client APIs. Note this instance implements Closeable and, while not
 * part of the public Instance API, should be closed when no longer needed.
 */
public class ProxyInstance implements Instance, Closeable {

  /**
   * Default fetch size to be used for BatchScanners. Currently equal to 1,000.
   */
  private static final int DEFAULT_FETCH_SIZE = 1_000;

  private AccumuloProxy.Iface client;

  private TTransport transport;
  private int fetchSize;

  /**
   * Assumes a TSocket transport wrapped by a TFramedTransport.
   * 
   * @param host
   *          the host name or IP address where the Accumulo Thrift Proxy server is running
   * @param port
   *          the port where the Accumulo Thrift Proxy server is listening
   * @throws TTransportException
   *           thrown if the Thrift TTransport cannot be established.
   */
  public ProxyInstance(String host, int port) throws TTransportException {
    this(host, port, DEFAULT_FETCH_SIZE);
  }

  /**
   * Assumes a TSocket transport wrapped by a TFramedTransport.
   * 
   * @param host
   *          the host name or IP address where the Accumulo Thrift Proxy server is running
   * @param port
   *          the port where the Accumulo Thrift Proxy server is listening
   * @param fetchSize
   *          the fetch size for BatchScanners
   * @throws TTransportException
   *           thrown if the Thrift TTransport cannot be established.
   */
  public ProxyInstance(String host, int port, int fetchSize) throws TTransportException {
    this(new TFramedTransport(new TSocket(host, port)), fetchSize);
  }

  public ProxyInstance(TTransport transport) throws TTransportException {
    this(transport, DEFAULT_FETCH_SIZE);
  }

  /**
   * 
   * @param transport
   *          Thrift transport to communicate with Proxy Server
   * @param fetchSize
   *          the fetch size for BatchScanners. Must be 0 < fetchSize <= 2000. If fetchSize is outside of this range, a warning will be logged and the
   *          {@link #DEFAULT_FETCH_SIZE} will be used.
   * @throws TTransportException
   *           thrown if the Thrift TTransport cannot be established.
   */
  public ProxyInstance(TTransport transport, int fetchSize) throws TTransportException {
    if (!transport.isOpen()) {
      transport.open();
    }
    TProtocol protocol = new TCompactProtocol(transport);
    client = new SynchronizedProxy(new AccumuloProxy.Client(protocol));
    this.transport = transport;

    if (fetchSize <= 0 || fetchSize > 2000) {
      LoggerFactory.getLogger(ProxyInstance.class).warn(
          "Fetch size out of range (0 < fetchSize <= 2000): " + fetchSize + "; using default: " + DEFAULT_FETCH_SIZE);
      this.fetchSize = DEFAULT_FETCH_SIZE;
    } else {
      this.fetchSize = fetchSize;
    }
  }

  public int getBatchScannerFetchSize() {
    return fetchSize;
  }

  public String getRootTabletLocation() {
    throw ExceptionFactory.unsupported();
  }

  public List<String> getMasterLocations() {
    throw ExceptionFactory.unsupported();
  }

  public String getInstanceID() {
    throw ExceptionFactory.unsupported();
  }

  public String getInstanceName() {
    throw ExceptionFactory.unsupported();
  }

  public String getZooKeepers() {
    throw ExceptionFactory.unsupported();
  }

  public int getZooKeepersSessionTimeOut() {
    throw ExceptionFactory.unsupported();
  }

  @Deprecated
  public Connector getConnector(String user, byte[] pass) throws AccumuloException, AccumuloSecurityException {
    return getConnector(user, new PasswordToken(pass));
  }

  @Deprecated
  public Connector getConnector(String user, ByteBuffer pass) throws AccumuloException, AccumuloSecurityException {
    return getConnector(user, new PasswordToken(pass));
  }

  @Deprecated
  public Connector getConnector(String user, CharSequence pass) throws AccumuloException, AccumuloSecurityException {
    return getConnector(user, new PasswordToken(pass));
  }

  @Deprecated
  public AccumuloConfiguration getConfiguration() {
    throw ExceptionFactory.unsupported();
  }

  @Deprecated
  public void setConfiguration(AccumuloConfiguration conf) {
    throw ExceptionFactory.unsupported();
  }

  public Connector getConnector(String principal, AuthenticationToken token) throws AccumuloException, AccumuloSecurityException {
    try {
      return new ProxyConnector(this, principal, token);
    } catch (TException e) {
      throw ExceptionFactory.accumuloException(e);
    }
  }

  AccumuloProxy.Iface getClient() {
    return client;
  }

  public void close() {
    // TODO- Neither Instance API nor Connector have a "close" method. But
    // we need to close the transport when done. How to handle it? Currently
    // clients must cast their Instance as a ProxyInstance and call close,
    // *hope* the finalize method is called, or just be a bad citizen and
    // not clean up resources on the proxy server.
    if (transport != null) {
      try {
        transport.close();
      } finally {
        transport = null;
        client = null;
      }
    }
  }

  @Override
  protected void finalize() {
    close();
  }

  /**
   * A wrapper class that synchronizes every method in the Iface interface against this object, and then passes the call through to the wrapped Iface delegate.
   * Due to the asynchronous nature of Thrift internals, if multiple threads use the same Iface object, the server may receive requests out-of-order and fail.
   * This class helps mitigate that risk by ensuring only one thread is ever communicating with the proxy at one time. This incurs a performance hit, but if you
   * are using the proxy instance, you probably aren't too concerned about throughput anyway...
   */
  private static class SynchronizedProxy implements AccumuloProxy.Iface {

    AccumuloProxy.Iface delegate;

    SynchronizedProxy(AccumuloProxy.Iface delegate) {
      this.delegate = delegate;
    }

	/**
	 * @param principal
	 * @param loginProperties
	 * @return
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#login(java.lang.String, java.util.Map)
	 */
	public synchronized ByteBuffer login(String principal, Map<String, String> loginProperties)
			throws org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.login(principal, loginProperties);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param constraintClassName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#addConstraint(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized int addConstraint(ByteBuffer login, String tableName, String constraintClassName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.addConstraint(login, tableName, constraintClassName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param splits
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#addSplits(java.nio.ByteBuffer, java.lang.String, java.util.Set)
	 */
	public synchronized void addSplits(ByteBuffer login, String tableName, Set<ByteBuffer> splits)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.addSplits(login, tableName, splits);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param setting
	 * @param scopes
	 * @throws AccumuloSecurityException
	 * @throws AccumuloException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#attachIterator(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.IteratorSetting, java.util.Set)
	 */
	public synchronized void attachIterator(ByteBuffer login, String tableName, IteratorSetting setting, Set<IteratorScope> scopes)
			throws org.apache.accumulo.proxy.thrift.AccumuloSecurityException,
			org.apache.accumulo.proxy.thrift.AccumuloException, TableNotFoundException, TException {
		delegate.attachIterator(login, tableName, setting, scopes);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param setting
	 * @param scopes
	 * @throws AccumuloSecurityException
	 * @throws AccumuloException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#checkIteratorConflicts(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.IteratorSetting, java.util.Set)
	 */
	public synchronized void checkIteratorConflicts(ByteBuffer login, String tableName, IteratorSetting setting,
			Set<IteratorScope> scopes) throws org.apache.accumulo.proxy.thrift.AccumuloSecurityException,
			org.apache.accumulo.proxy.thrift.AccumuloException, TableNotFoundException, TException {
		delegate.checkIteratorConflicts(login, tableName, setting, scopes);
	}

	/**
	 * @param login
	 * @param tableName
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#clearLocatorCache(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void clearLocatorCache(ByteBuffer login, String tableName) throws TableNotFoundException, TException {
		delegate.clearLocatorCache(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param newTableName
	 * @param flush
	 * @param propertiesToSet
	 * @param propertiesToExclude
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TableExistsException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#cloneTable(java.nio.ByteBuffer, java.lang.String, java.lang.String, boolean, java.util.Map, java.util.Set)
	 */
	public synchronized void cloneTable(ByteBuffer login, String tableName, String newTableName, boolean flush,
			Map<String, String> propertiesToSet, Set<String> propertiesToExclude)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TableExistsException,
			TException {
		delegate.cloneTable(login, tableName, newTableName, flush, propertiesToSet, propertiesToExclude);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param startRow
	 * @param endRow
	 * @param iterators
	 * @param flush
	 * @param wait
	 * @param compactionStrategy
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws AccumuloException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#compactTable(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer, java.nio.ByteBuffer, java.util.List, boolean, boolean, org.apache.accumulo.proxy.thrift.CompactionStrategyConfig)
	 */
	public synchronized void compactTable(ByteBuffer login, String tableName, ByteBuffer startRow, ByteBuffer endRow,
			List<IteratorSetting> iterators, boolean flush, boolean wait, CompactionStrategyConfig compactionStrategy)
			throws org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException,
			org.apache.accumulo.proxy.thrift.AccumuloException, TException {
		delegate.compactTable(login, tableName, startRow, endRow, iterators, flush, wait, compactionStrategy);
	}

	/**
	 * @param login
	 * @param tableName
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws AccumuloException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#cancelCompaction(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void cancelCompaction(ByteBuffer login, String tableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException,
			org.apache.accumulo.proxy.thrift.AccumuloException, TException {
		delegate.cancelCompaction(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param versioningIter
	 * @param type
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableExistsException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createTable(java.nio.ByteBuffer, java.lang.String, boolean, org.apache.accumulo.proxy.thrift.TimeType)
	 */
	public synchronized void createTable(ByteBuffer login, String tableName, boolean versioningIter, TimeType type)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableExistsException, TException {
		delegate.createTable(login, tableName, versioningIter, type);
	}

	/**
	 * @param login
	 * @param tableName
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#deleteTable(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void deleteTable(ByteBuffer login, String tableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.deleteTable(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param startRow
	 * @param endRow
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#deleteRows(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer, java.nio.ByteBuffer)
	 */
	public synchronized void deleteRows(ByteBuffer login, String tableName, ByteBuffer startRow, ByteBuffer endRow)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.deleteRows(login, tableName, startRow, endRow);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param exportDir
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#exportTable(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void exportTable(ByteBuffer login, String tableName, String exportDir)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.exportTable(login, tableName, exportDir);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param startRow
	 * @param endRow
	 * @param wait
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#flushTable(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer, java.nio.ByteBuffer, boolean)
	 */
	public synchronized void flushTable(ByteBuffer login, String tableName, ByteBuffer startRow, ByteBuffer endRow, boolean wait)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.flushTable(login, tableName, startRow, endRow, wait);
	}

	/**
	 * @param login
	 * @param tables
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getDiskUsage(java.nio.ByteBuffer, java.util.Set)
	 */
	public synchronized List<DiskUsage> getDiskUsage(ByteBuffer login, Set<String> tables)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.getDiskUsage(login, tables);
	}

	/**
	 * @param login
	 * @param tableName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getLocalityGroups(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, Set<String>> getLocalityGroups(ByteBuffer login, String tableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.getLocalityGroups(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param iteratorName
	 * @param scope
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getIteratorSetting(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.IteratorScope)
	 */
	public synchronized IteratorSetting getIteratorSetting(ByteBuffer login, String tableName, String iteratorName,
			IteratorScope scope) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.getIteratorSetting(login, tableName, iteratorName, scope);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param auths
	 * @param startRow
	 * @param startInclusive
	 * @param endRow
	 * @param endInclusive
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getMaxRow(java.nio.ByteBuffer, java.lang.String, java.util.Set, java.nio.ByteBuffer, boolean, java.nio.ByteBuffer, boolean)
	 */
	public synchronized ByteBuffer getMaxRow(ByteBuffer login, String tableName, Set<ByteBuffer> auths, ByteBuffer startRow,
			boolean startInclusive, ByteBuffer endRow, boolean endInclusive)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.getMaxRow(login, tableName, auths, startRow, startInclusive, endRow, endInclusive);
	}

	/**
	 * @param login
	 * @param tableName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getTableProperties(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, String> getTableProperties(ByteBuffer login, String tableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.getTableProperties(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param importDir
	 * @param failureDir
	 * @param setTime
	 * @throws TableNotFoundException
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#importDirectory(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	public synchronized void importDirectory(ByteBuffer login, String tableName, String importDir, String failureDir,
			boolean setTime) throws TableNotFoundException, org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.importDirectory(login, tableName, importDir, failureDir, setTime);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param importDir
	 * @throws TableExistsException
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#importTable(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void importTable(ByteBuffer login, String tableName, String importDir)
			throws TableExistsException, org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.importTable(login, tableName, importDir);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param maxSplits
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listSplits(java.nio.ByteBuffer, java.lang.String, int)
	 */
	public synchronized List<ByteBuffer> listSplits(ByteBuffer login, String tableName, int maxSplits)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.listSplits(login, tableName, maxSplits);
	}

	/**
	 * @param login
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listTables(java.nio.ByteBuffer)
	 */
	public synchronized Set<String> listTables(ByteBuffer login) throws TException {
		return delegate.listTables(login);
	}

	/**
	 * @param login
	 * @param tableName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listIterators(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, Set<IteratorScope>> listIterators(ByteBuffer login, String tableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.listIterators(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listConstraints(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, Integer> listConstraints(ByteBuffer login, String tableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.listConstraints(login, tableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param startRow
	 * @param endRow
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#mergeTablets(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer, java.nio.ByteBuffer)
	 */
	public synchronized void mergeTablets(ByteBuffer login, String tableName, ByteBuffer startRow, ByteBuffer endRow)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.mergeTablets(login, tableName, startRow, endRow);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param wait
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#offlineTable(java.nio.ByteBuffer, java.lang.String, boolean)
	 */
	public synchronized void offlineTable(ByteBuffer login, String tableName, boolean wait)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.offlineTable(login, tableName, wait);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param wait
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#onlineTable(java.nio.ByteBuffer, java.lang.String, boolean)
	 */
	public synchronized void onlineTable(ByteBuffer login, String tableName, boolean wait)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.onlineTable(login, tableName, wait);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param constraint
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeConstraint(java.nio.ByteBuffer, java.lang.String, int)
	 */
	public synchronized void removeConstraint(ByteBuffer login, String tableName, int constraint)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.removeConstraint(login, tableName, constraint);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param iterName
	 * @param scopes
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeIterator(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.util.Set)
	 */
	public synchronized void removeIterator(ByteBuffer login, String tableName, String iterName, Set<IteratorScope> scopes)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.removeIterator(login, tableName, iterName, scopes);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param property
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeTableProperty(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void removeTableProperty(ByteBuffer login, String tableName, String property)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.removeTableProperty(login, tableName, property);
	}

	/**
	 * @param login
	 * @param oldTableName
	 * @param newTableName
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TableExistsException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#renameTable(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void renameTable(ByteBuffer login, String oldTableName, String newTableName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TableExistsException,
			TException {
		delegate.renameTable(login, oldTableName, newTableName);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param groups
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#setLocalityGroups(java.nio.ByteBuffer, java.lang.String, java.util.Map)
	 */
	public synchronized void setLocalityGroups(ByteBuffer login, String tableName, Map<String, Set<String>> groups)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.setLocalityGroups(login, tableName, groups);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param property
	 * @param value
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#setTableProperty(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.lang.String)
	 */
	public synchronized void setTableProperty(ByteBuffer login, String tableName, String property, String value)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.setTableProperty(login, tableName, property, value);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param range
	 * @param maxSplits
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#splitRangeByTablets(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.Range, int)
	 */
	public synchronized Set<Range> splitRangeByTablets(ByteBuffer login, String tableName, Range range, int maxSplits)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.splitRangeByTablets(login, tableName, range, maxSplits);
	}

	/**
	 * @param login
	 * @param tableName
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#tableExists(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized boolean tableExists(ByteBuffer login, String tableName) throws TException {
		return delegate.tableExists(login, tableName);
	}

	/**
	 * @param login
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#tableIdMap(java.nio.ByteBuffer)
	 */
	public synchronized Map<String, String> tableIdMap(ByteBuffer login) throws TException {
		return delegate.tableIdMap(login);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param className
	 * @param asTypeName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#testTableClassLoad(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.lang.String)
	 */
	public synchronized boolean testTableClassLoad(ByteBuffer login, String tableName, String className, String asTypeName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.testTableClassLoad(login, tableName, className, asTypeName);
	}

	/**
	 * @param login
	 * @param tserver
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#pingTabletServer(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void pingTabletServer(ByteBuffer login, String tserver)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.pingTabletServer(login, tserver);
	}

	/**
	 * @param login
	 * @param tserver
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getActiveScans(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized List<ActiveScan> getActiveScans(ByteBuffer login, String tserver)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.getActiveScans(login, tserver);
	}

	/**
	 * @param login
	 * @param tserver
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getActiveCompactions(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized List<ActiveCompaction> getActiveCompactions(ByteBuffer login, String tserver)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.getActiveCompactions(login, tserver);
	}

	/**
	 * @param login
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getSiteConfiguration(java.nio.ByteBuffer)
	 */
	public synchronized Map<String, String> getSiteConfiguration(ByteBuffer login)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.getSiteConfiguration(login);
	}

	/**
	 * @param login
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getSystemConfiguration(java.nio.ByteBuffer)
	 */
	public synchronized Map<String, String> getSystemConfiguration(ByteBuffer login)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.getSystemConfiguration(login);
	}

	/**
	 * @param login
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getTabletServers(java.nio.ByteBuffer)
	 */
	public synchronized List<String> getTabletServers(ByteBuffer login) throws TException {
		return delegate.getTabletServers(login);
	}

	/**
	 * @param login
	 * @param property
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeProperty(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void removeProperty(ByteBuffer login, String property)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.removeProperty(login, property);
	}

	/**
	 * @param login
	 * @param property
	 * @param value
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#setProperty(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void setProperty(ByteBuffer login, String property, String value)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.setProperty(login, property, value);
	}

	/**
	 * @param login
	 * @param className
	 * @param asTypeName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#testClassLoad(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized boolean testClassLoad(ByteBuffer login, String className, String asTypeName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.testClassLoad(login, className, asTypeName);
	}

	/**
	 * @param login
	 * @param user
	 * @param properties
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#authenticateUser(java.nio.ByteBuffer, java.lang.String, java.util.Map)
	 */
	public synchronized boolean authenticateUser(ByteBuffer login, String user, Map<String, String> properties)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.authenticateUser(login, user, properties);
	}

	/**
	 * @param login
	 * @param user
	 * @param authorizations
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#changeUserAuthorizations(java.nio.ByteBuffer, java.lang.String, java.util.Set)
	 */
	public synchronized void changeUserAuthorizations(ByteBuffer login, String user, Set<ByteBuffer> authorizations)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.changeUserAuthorizations(login, user, authorizations);
	}

	/**
	 * @param login
	 * @param user
	 * @param password
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#changeLocalUserPassword(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer)
	 */
	public synchronized void changeLocalUserPassword(ByteBuffer login, String user, ByteBuffer password)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.changeLocalUserPassword(login, user, password);
	}

	/**
	 * @param login
	 * @param user
	 * @param password
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createLocalUser(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer)
	 */
	public synchronized void createLocalUser(ByteBuffer login, String user, ByteBuffer password)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.createLocalUser(login, user, password);
	}

	/**
	 * @param login
	 * @param user
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#dropLocalUser(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void dropLocalUser(ByteBuffer login, String user) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.dropLocalUser(login, user);
	}

	/**
	 * @param login
	 * @param user
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getUserAuthorizations(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized List<ByteBuffer> getUserAuthorizations(ByteBuffer login, String user)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.getUserAuthorizations(login, user);
	}

	/**
	 * @param login
	 * @param user
	 * @param perm
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#grantSystemPermission(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.SystemPermission)
	 */
	public synchronized void grantSystemPermission(ByteBuffer login, String user, SystemPermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.grantSystemPermission(login, user, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param table
	 * @param perm
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#grantTablePermission(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.TablePermission)
	 */
	public synchronized void grantTablePermission(ByteBuffer login, String user, String table, TablePermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.grantTablePermission(login, user, table, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param perm
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#hasSystemPermission(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.SystemPermission)
	 */
	public synchronized boolean hasSystemPermission(ByteBuffer login, String user, SystemPermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.hasSystemPermission(login, user, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param table
	 * @param perm
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#hasTablePermission(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.TablePermission)
	 */
	public synchronized boolean hasTablePermission(ByteBuffer login, String user, String table, TablePermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.hasTablePermission(login, user, table, perm);
	}

	/**
	 * @param login
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listLocalUsers(java.nio.ByteBuffer)
	 */
	public synchronized Set<String> listLocalUsers(ByteBuffer login) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.listLocalUsers(login);
	}

	/**
	 * @param login
	 * @param user
	 * @param perm
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#revokeSystemPermission(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.SystemPermission)
	 */
	public synchronized void revokeSystemPermission(ByteBuffer login, String user, SystemPermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.revokeSystemPermission(login, user, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param table
	 * @param perm
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#revokeTablePermission(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.TablePermission)
	 */
	public synchronized void revokeTablePermission(ByteBuffer login, String user, String table, TablePermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		delegate.revokeTablePermission(login, user, table, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param namespaceName
	 * @param perm
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#grantNamespacePermission(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.NamespacePermission)
	 */
	public synchronized void grantNamespacePermission(ByteBuffer login, String user, String namespaceName, NamespacePermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.grantNamespacePermission(login, user, namespaceName, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param namespaceName
	 * @param perm
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#hasNamespacePermission(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.NamespacePermission)
	 */
	public synchronized boolean hasNamespacePermission(ByteBuffer login, String user, String namespaceName, NamespacePermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.hasNamespacePermission(login, user, namespaceName, perm);
	}

	/**
	 * @param login
	 * @param user
	 * @param namespaceName
	 * @param perm
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#revokeNamespacePermission(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.NamespacePermission)
	 */
	public synchronized void revokeNamespacePermission(ByteBuffer login, String user, String namespaceName, NamespacePermission perm)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		delegate.revokeNamespacePermission(login, user, namespaceName, perm);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param options
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createBatchScanner(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.BatchScanOptions)
	 */
	public synchronized String createBatchScanner(ByteBuffer login, String tableName, BatchScanOptions options)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.createBatchScanner(login, tableName, options);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param options
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createScanner(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.ScanOptions)
	 */
	public synchronized String createScanner(ByteBuffer login, String tableName, ScanOptions options)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.createScanner(login, tableName, options);
	}

	/**
	 * @param scanner
	 * @return
	 * @throws UnknownScanner
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#hasNext(java.lang.String)
	 */
	public synchronized boolean hasNext(String scanner) throws UnknownScanner, TException {
		return delegate.hasNext(scanner);
	}

	/**
	 * @param scanner
	 * @return
	 * @throws NoMoreEntriesException
	 * @throws UnknownScanner
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#nextEntry(java.lang.String)
	 */
	public synchronized KeyValueAndPeek nextEntry(String scanner) throws NoMoreEntriesException, UnknownScanner,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.nextEntry(scanner);
	}

	/**
	 * @param scanner
	 * @param k
	 * @return
	 * @throws NoMoreEntriesException
	 * @throws UnknownScanner
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#nextK(java.lang.String, int)
	 */
	public synchronized ScanResult nextK(String scanner, int k) throws NoMoreEntriesException, UnknownScanner,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.nextK(scanner, k);
	}

	/**
	 * @param scanner
	 * @throws UnknownScanner
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#closeScanner(java.lang.String)
	 */
	public synchronized void closeScanner(String scanner) throws UnknownScanner, TException {
		delegate.closeScanner(scanner);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param cells
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws MutationsRejectedException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#updateAndFlush(java.nio.ByteBuffer, java.lang.String, java.util.Map)
	 */
	public synchronized void updateAndFlush(ByteBuffer login, String tableName, Map<ByteBuffer, List<ColumnUpdate>> cells)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException,
			MutationsRejectedException, TException {
		delegate.updateAndFlush(login, tableName, cells);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param opts
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createWriter(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.WriterOptions)
	 */
	public synchronized String createWriter(ByteBuffer login, String tableName, WriterOptions opts)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.createWriter(login, tableName, opts);
	}

	/**
	 * @param writer
	 * @param cells
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#update(java.lang.String, java.util.Map)
	 */
	public synchronized void update(String writer, Map<ByteBuffer, List<ColumnUpdate>> cells) throws TException {
		delegate.update(writer, cells);
	}

	/**
	 * @param writer
	 * @throws UnknownWriter
	 * @throws MutationsRejectedException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#flush(java.lang.String)
	 */
	public synchronized void flush(String writer) throws UnknownWriter, MutationsRejectedException, TException {
		delegate.flush(writer);
	}

	/**
	 * @param writer
	 * @throws UnknownWriter
	 * @throws MutationsRejectedException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#closeWriter(java.lang.String)
	 */
	public synchronized void closeWriter(String writer) throws UnknownWriter, MutationsRejectedException, TException {
		delegate.closeWriter(writer);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param row
	 * @param updates
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#updateRowConditionally(java.nio.ByteBuffer, java.lang.String, java.nio.ByteBuffer, org.apache.accumulo.proxy.thrift.ConditionalUpdates)
	 */
	public synchronized ConditionalStatus updateRowConditionally(ByteBuffer login, String tableName, ByteBuffer row,
			ConditionalUpdates updates) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.updateRowConditionally(login, tableName, row, updates);
	}

	/**
	 * @param login
	 * @param tableName
	 * @param options
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TableNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createConditionalWriter(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.ConditionalWriterOptions)
	 */
	public synchronized String createConditionalWriter(ByteBuffer login, String tableName, ConditionalWriterOptions options)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TableNotFoundException, TException {
		return delegate.createConditionalWriter(login, tableName, options);
	}

	/**
	 * @param conditionalWriter
	 * @param updates
	 * @return
	 * @throws UnknownWriter
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#updateRowsConditionally(java.lang.String, java.util.Map)
	 */
	public synchronized Map<ByteBuffer, ConditionalStatus> updateRowsConditionally(String conditionalWriter,
			Map<ByteBuffer, ConditionalUpdates> updates)
			throws UnknownWriter, org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.updateRowsConditionally(conditionalWriter, updates);
	}

	/**
	 * @param conditionalWriter
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#closeConditionalWriter(java.lang.String)
	 */
	public synchronized void closeConditionalWriter(String conditionalWriter) throws TException {
		delegate.closeConditionalWriter(conditionalWriter);
	}

	/**
	 * @param row
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getRowRange(java.nio.ByteBuffer)
	 */
	public synchronized Range getRowRange(ByteBuffer row) throws TException {
		return delegate.getRowRange(row);
	}

	/**
	 * @param key
	 * @param part
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getFollowing(org.apache.accumulo.proxy.thrift.Key, org.apache.accumulo.proxy.thrift.PartialKey)
	 */
	public synchronized Key getFollowing(Key key, PartialKey part) throws TException {
		return delegate.getFollowing(key, part);
	}

	/**
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#systemNamespace()
	 */
	public synchronized String systemNamespace() throws TException {
		return delegate.systemNamespace();
	}

	/**
	 * @return
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#defaultNamespace()
	 */
	public synchronized String defaultNamespace() throws TException {
		return delegate.defaultNamespace();
	}

	/**
	 * @param login
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listNamespaces(java.nio.ByteBuffer)
	 */
	public synchronized List<String> listNamespaces(ByteBuffer login) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.listNamespaces(login);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#namespaceExists(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized boolean namespaceExists(ByteBuffer login, String namespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.namespaceExists(login, namespaceName);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceExistsException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#createNamespace(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void createNamespace(ByteBuffer login, String namespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceExistsException, TException {
		delegate.createNamespace(login, namespaceName);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws NamespaceNotEmptyException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#deleteNamespace(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized void deleteNamespace(ByteBuffer login, String namespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException,
			NamespaceNotEmptyException, TException {
		delegate.deleteNamespace(login, namespaceName);
	}

	/**
	 * @param login
	 * @param oldNamespaceName
	 * @param newNamespaceName
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws NamespaceExistsException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#renameNamespace(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void renameNamespace(ByteBuffer login, String oldNamespaceName, String newNamespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException,
			NamespaceExistsException, TException {
		delegate.renameNamespace(login, oldNamespaceName, newNamespaceName);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param property
	 * @param value
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#setNamespaceProperty(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.lang.String)
	 */
	public synchronized void setNamespaceProperty(ByteBuffer login, String namespaceName, String property, String value)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		delegate.setNamespaceProperty(login, namespaceName, property, value);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param property
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeNamespaceProperty(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized void removeNamespaceProperty(ByteBuffer login, String namespaceName, String property)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		delegate.removeNamespaceProperty(login, namespaceName, property);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getNamespaceProperties(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, String> getNamespaceProperties(ByteBuffer login, String namespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		return delegate.getNamespaceProperties(login, namespaceName);
	}

	/**
	 * @param login
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#namespaceIdMap(java.nio.ByteBuffer)
	 */
	public synchronized Map<String, String> namespaceIdMap(ByteBuffer login)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, TException {
		return delegate.namespaceIdMap(login);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param setting
	 * @param scopes
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#attachNamespaceIterator(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.IteratorSetting, java.util.Set)
	 */
	public synchronized void attachNamespaceIterator(ByteBuffer login, String namespaceName, IteratorSetting setting,
			Set<IteratorScope> scopes) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		delegate.attachNamespaceIterator(login, namespaceName, setting, scopes);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param name
	 * @param scopes
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeNamespaceIterator(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.util.Set)
	 */
	public synchronized void removeNamespaceIterator(ByteBuffer login, String namespaceName, String name, Set<IteratorScope> scopes)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		delegate.removeNamespaceIterator(login, namespaceName, name, scopes);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param name
	 * @param scope
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#getNamespaceIteratorSetting(java.nio.ByteBuffer, java.lang.String, java.lang.String, org.apache.accumulo.proxy.thrift.IteratorScope)
	 */
	public synchronized IteratorSetting getNamespaceIteratorSetting(ByteBuffer login, String namespaceName, String name,
			IteratorScope scope) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		return delegate.getNamespaceIteratorSetting(login, namespaceName, name, scope);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listNamespaceIterators(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, Set<IteratorScope>> listNamespaceIterators(ByteBuffer login, String namespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		return delegate.listNamespaceIterators(login, namespaceName);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param setting
	 * @param scopes
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#checkNamespaceIteratorConflicts(java.nio.ByteBuffer, java.lang.String, org.apache.accumulo.proxy.thrift.IteratorSetting, java.util.Set)
	 */
	public synchronized void checkNamespaceIteratorConflicts(ByteBuffer login, String namespaceName, IteratorSetting setting,
			Set<IteratorScope> scopes) throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		delegate.checkNamespaceIteratorConflicts(login, namespaceName, setting, scopes);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param constraintClassName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#addNamespaceConstraint(java.nio.ByteBuffer, java.lang.String, java.lang.String)
	 */
	public synchronized int addNamespaceConstraint(ByteBuffer login, String namespaceName, String constraintClassName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		return delegate.addNamespaceConstraint(login, namespaceName, constraintClassName);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param id
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#removeNamespaceConstraint(java.nio.ByteBuffer, java.lang.String, int)
	 */
	public synchronized void removeNamespaceConstraint(ByteBuffer login, String namespaceName, int id)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		delegate.removeNamespaceConstraint(login, namespaceName, id);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#listNamespaceConstraints(java.nio.ByteBuffer, java.lang.String)
	 */
	public synchronized Map<String, Integer> listNamespaceConstraints(ByteBuffer login, String namespaceName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		return delegate.listNamespaceConstraints(login, namespaceName);
	}

	/**
	 * @param login
	 * @param namespaceName
	 * @param className
	 * @param asTypeName
	 * @return
	 * @throws AccumuloException
	 * @throws AccumuloSecurityException
	 * @throws NamespaceNotFoundException
	 * @throws TException
	 * @see org.apache.accumulo.proxy.thrift.AccumuloProxy.Iface#testNamespaceClassLoad(java.nio.ByteBuffer, java.lang.String, java.lang.String, java.lang.String)
	 */
	public synchronized boolean testNamespaceClassLoad(ByteBuffer login, String namespaceName, String className, String asTypeName)
			throws org.apache.accumulo.proxy.thrift.AccumuloException,
			org.apache.accumulo.proxy.thrift.AccumuloSecurityException, NamespaceNotFoundException, TException {
		return delegate.testNamespaceClassLoad(login, namespaceName, className, asTypeName);
	}


  }

}
