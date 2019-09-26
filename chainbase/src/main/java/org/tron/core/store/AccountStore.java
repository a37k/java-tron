package org.tron.core.store;

import static org.tron.common.utils.StringUtil.encode58Check;

import com.typesafe.config.ConfigObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.common.utils.Commons;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.db.accountstate.AccountStateCallBackUtils;
import org.tron.core.db.TronStoreWithRevoking;

@Slf4j(topic = "DB")
@Component
public class AccountStore extends TronStoreWithRevoking<AccountCapsule> {

  private static Map<String, byte[]> assertsAddress = new HashMap<>(); // key = name , value = address

  @Autowired
  private DynamicPropertiesStore dynamicPropertiesStore;

  @Autowired
  private AccountStateCallBackUtils accountStateCallBackUtils;

  @Autowired
  private AccountStore(@Value("account") String dbName) {
    super(dbName);
  }

  @Override
  public AccountCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new AccountCapsule(value);
  }


  @Override
  public void put(byte[] key, AccountCapsule item) {
    if (encode58Check(key).equals("TJ2aDMgeipmoZRuUEru2ri8t7TGkxnm6qY")) {
      logger.error("balance: " + "TJ2aDMgeipmoZRuUEru2ri8t7TGkxnm6qY balance" + item.getBalance()+ " amount "
          + item.getAllowance() + " fee" + dynamicPropertiesStore.getLatestBlockHeaderNumber());
    }

    super.put(key, item);
    accountStateCallBackUtils.accountCallBack(key, item);
  }

  /**
   * Max TRX account.
   */
  public AccountCapsule getSun() {
    return getUnchecked(assertsAddress.get("Sun"));
  }

  /**
   * Min TRX account.
   */
  public AccountCapsule getBlackhole() {
    return getUnchecked(assertsAddress.get("Blackhole"));
  }

  /**
   * Get foundation account info.
   */
  public AccountCapsule getZion() {
    return getUnchecked(assertsAddress.get("Zion"));
  }

  public static void setAccount(com.typesafe.config.Config config) {
    List list = config.getObjectList("genesis.block.assets");
    for (int i = 0; i < list.size(); i++) {
      ConfigObject obj = (ConfigObject) list.get(i);
      String accountName = obj.get("accountName").unwrapped().toString();
      byte[] address = Commons.decodeFromBase58Check(obj.get("address").unwrapped().toString());
      assertsAddress.put(accountName, address);
    }
  }

  @Override
  public void close() {
    super.close();
  }
}
