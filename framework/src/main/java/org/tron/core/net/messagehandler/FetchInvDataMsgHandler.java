package org.tron.core.net.messagehandler;

import static org.tron.core.config.args.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.discover.node.statistics.MessageCount;
import org.tron.common.overlay.message.Message;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule.BlockId;
import org.tron.core.config.Parameter.NodeConstant;
import org.tron.core.exception.P2pException;
import org.tron.core.exception.P2pException.TypeEnum;
import org.tron.core.net.TronNetDelegate;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.net.message.FetchInvDataMessage;
import org.tron.core.net.message.MessageTypes;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.net.message.TransactionsMessage;
import org.tron.core.net.message.TronMessage;
import org.tron.core.net.peer.Item;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.net.service.AdvService;
import org.tron.core.net.service.SyncService;
import org.tron.protos.Protocol.Inventory.InventoryType;
import org.tron.protos.Protocol.ReasonCode;
import org.tron.protos.Protocol.Transaction;

@Slf4j(topic = "net")
@Component
public class FetchInvDataMsgHandler implements TronMsgHandler {

  @Autowired
  private TronNetDelegate tronNetDelegate;

  @Autowired
  private SyncService syncService;

  @Autowired
  private AdvService advService;

  private static final int MAX_SIZE = 1_000_000;

  @Override
  public void processMessage(PeerConnection peer, TronMessage msg) throws P2pException {

    FetchInvDataMessage fetchInvDataMsg = (FetchInvDataMessage) msg;

    check(peer, fetchInvDataMsg);

    InventoryType type = fetchInvDataMsg.getInventoryType();
    List<Transaction> transactions = Lists.newArrayList();

    int size = 0;

    for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
      Item item = new Item(hash, type);
      Message message = advService.getMessage(item);
      if (message == null) {
        try {
          message = tronNetDelegate.getData(hash, type);
        } catch (Exception e) {
          logger.error("Fetch item {} failed. reason: {}", item, hash, e.getMessage());
          peer.disconnect(ReasonCode.FETCH_FAIL);
          return;
        }
      }

      if (type == InventoryType.BLOCK) {
        BlockId blockId = ((BlockMessage) message).getBlockCapsule().getBlockId();
        if (peer.getBlockBothHave().getNum() < blockId.getNum()) {
          peer.setBlockBothHave(blockId);
        }
        peer.sendMessage(message);
      } else {
        transactions.add(((TransactionMessage) message).getTransactionCapsule().getInstance());
        size += ((TransactionMessage) message).getTransactionCapsule().getInstance()
            .getSerializedSize();
        if (size > MAX_SIZE) {
          peer.sendMessage(new TransactionsMessage(transactions));
          transactions = Lists.newArrayList();
          size = 0;
        }
      }
    }
    if (!transactions.isEmpty()) {
      peer.sendMessage(new TransactionsMessage(transactions));
    }
  }

  private void check(PeerConnection peer, FetchInvDataMessage fetchInvDataMsg) throws P2pException {
    MessageTypes type = fetchInvDataMsg.getInvMessageType();

    if (type == MessageTypes.TRX) {
      for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
        if (peer.getAdvInvSpread().getIfPresent(new Item(hash, InventoryType.TRX)) == null) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "not spread inv: {}" + hash);
        }
      }
      int fetchCount = peer.getNodeStatistics().messageStatistics.tronInTrxFetchInvDataElement
          .getCount(10);
      int maxCount = advService.getTrxCount().getCount(60);
      if (fetchCount > maxCount) {
        throw new P2pException(TypeEnum.BAD_MESSAGE,
            "maxCount: " + maxCount + ", fetchCount: " + fetchCount);
      }
    } else {
      boolean isAdv = true;
      for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
        if (peer.getAdvInvSpread().getIfPresent(new Item(hash, InventoryType.BLOCK)) == null) {
          isAdv = false;
          break;
        }
      }
      if (isAdv) {
        MessageCount tronOutAdvBlock = peer.getNodeStatistics().messageStatistics.tronOutAdvBlock;
        tronOutAdvBlock.add(fetchInvDataMsg.getHashList().size());
        int outBlockCountIn1min = tronOutAdvBlock.getCount(60);
        int producedBlockIn2min = 120_000 / BLOCK_PRODUCED_INTERVAL;
        if (outBlockCountIn1min > producedBlockIn2min) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "producedBlockIn2min: " + producedBlockIn2min
              + ", outBlockCountIn1min: " + outBlockCountIn1min);
        }
      } else {
        if (!peer.isNeedSyncFromUs()) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "no need sync");
        }
        for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
          long blockNum = new BlockId(hash).getNum();
          long minBlockNum =
              peer.getLastSyncBlockId().getNum() - 2 * NodeConstant.SYNC_FETCH_BATCH_NUM;
          if (blockNum < minBlockNum) {
            throw new P2pException(TypeEnum.BAD_MESSAGE,
                "minBlockNum: " + minBlockNum + ", blockNum: " + blockNum);
          }
          if (peer.getSyncBlockIdCache().getIfPresent(hash) != null) {
            throw new P2pException(TypeEnum.BAD_MESSAGE,
                new BlockId(hash).getString() + " is exist");
          }
          peer.getSyncBlockIdCache().put(hash, System.currentTimeMillis());
        }
      }
    }
  }

}
