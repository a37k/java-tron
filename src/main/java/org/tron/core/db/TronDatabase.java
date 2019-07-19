package org.tron.core.db;

import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.iq80.leveldb.WriteOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.common.storage.leveldb.LevelDbDataSourceImpl;
import org.tron.common.storage.RocksDbDataSourceImpl;
import org.tron.core.config.args.Args;
import org.tron.core.db.api.IndexHelper;
import org.tron.core.db.common.DbSourceInter;
import org.tron.core.db2.core.ITronChainBase;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ItemNotFoundException;

@Slf4j(topic = "DB")
public abstract class TronDatabase<T> implements ITronChainBase<T> {

  protected DbSourceInter<byte[]> dbSource;
  @Getter
  private String dbName;

  @Autowired(required = false)
  protected IndexHelper indexHelper;

  protected TronDatabase(String dbName) {
    this.dbName = dbName;

    if ("LEVELDB".equals(Args.getInstance().getStorage().getDbEngine().toUpperCase())) {
      dbSource =
          new LevelDbDataSourceImpl(Args.getInstance().getOutputDirectoryByDbName(dbName),
              dbName,
              Args.getInstance().getStorage().getOptionsByDbName(dbName),
              new WriteOptions().sync(Args.getInstance().getStorage().isDbSync()));
    } else if ("ROCKSDB".equals(Args.getInstance().getStorage().getDbEngine().toUpperCase())) {
      String parentName = Paths.get(Args.getInstance().getOutputDirectoryByDbName(dbName), Args.getInstance().getStorage().getDbDirectory()).toString();

      dbSource =
          new RocksDbDataSourceImpl(Args.getInstance().getOutputDirectoryByDbName(dbName), dbName);
    }

    dbSource.initDB();
  }

  protected TronDatabase() {
  }

  public DbSourceInter<byte[]> getDbSource() {
    return dbSource;
  }

  /**
   * reset the database.
   */
  public void reset() {
    dbSource.resetDb();
  }

  /**
   * close the database.
   */
  @Override
  public void close() {
    dbSource.closeDB();
  }

  public abstract void put(byte[] key, T item);

  public abstract void delete(byte[] key);

  public abstract T get(byte[] key)
      throws InvalidProtocolBufferException, ItemNotFoundException, BadItemException;

  public T getUnchecked(byte[] key) {
    return null;
  }

  public abstract boolean has(byte[] key);

  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public Iterator<Entry<byte[], T>> iterator() {
    throw new UnsupportedOperationException();
  }
}
