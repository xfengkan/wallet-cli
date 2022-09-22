
package org.tron.walletcli;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import java.util.*;
import org.tron.common.crypto.Sha256Sm3Hash;
import lombok.extern.slf4j.Slf4j;
import org.tron.api.GrpcAPI.*;
import org.tron.common.utils.*;
import org.tron.core.config.Configuration;
import org.tron.core.exception.CipherException;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.contract.AccountContract;
import org.tron.protos.contract.AccountContract.AccountUpdateContract;
import org.tron.protos.contract.AccountContract.SetAccountIdContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.UnfreezeAssetContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.UpdateAssetContract;
import org.tron.protos.contract.BalanceContract;
import org.tron.protos.contract.BalanceContract.FreezeBalanceContract;
import org.tron.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.tron.protos.contract.BalanceContract.WithdrawBalanceContract;
import org.tron.protos.contract.ProposalContract.ProposalApproveContract;
import org.tron.protos.contract.ProposalContract.ProposalCreateContract;
import org.tron.protos.contract.ProposalContract.ProposalDeleteContract;
import org.tron.protos.contract.WitnessContract.VoteWitnessContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.ParticipateAssetIssueContract;
import org.tron.protos.contract.WitnessContract.WitnessCreateContract;

import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.common.crypto.ECKey;

import org.tron.protos.contract.WitnessContract.WitnessUpdateContract;
import org.tron.walletserver.WalletApi;
import org.tron.walletserver.GrpcClient;

import java.io.IOException;

@Slf4j
public class KxfSolidity {

  private static GrpcClient rpcCli = init();

  public void createAccount(byte[] owner_address, byte[] account_address,
      int type) {
    AccountContract.AccountCreateContract.Builder builder = AccountContract.AccountCreateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    builder.setAccountAddress(ByteString.copyFrom(account_address));
    Protocol.AccountType accountType = Protocol.AccountType.values()[type];
    builder.setType(accountType);

    AccountContract.AccountCreateContract account = builder.build();
    rpcCli.createAccount2(account);

  }


  public void transferContract(byte[] owner_address, byte[] to_address,
      long amount) {
    BalanceContract.TransferContract.Builder builder = BalanceContract.TransferContract.newBuilder();
    builder.setAmount(amount);
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    builder.setToAddress(ByteString.copyFrom(to_address));

    BalanceContract.TransferContract contract = builder.build();
    //1.create   2.sig   3.broadcast  4.get
    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    if (transactionExtention == null) {
      return;
    }

    Transaction transaction = transactionExtention.getTransaction();
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      System.out.println("Transaction is empty");
      return;
    }

    System.out.println(Utils.printTransactionExceptId(transaction));
    System.out.println("before sign transaction hex string is " +
        ByteArray.toHexString(transaction.toByteArray()));
    //sign
    byte[] privateKey = ByteArray.fromHexString(
        "c209b57d51038ab6598d4622c92dfcf1e115f094aac964891c3ef4e101e43b2c");
    ECKey key = new ECKey(privateKey, true);
    transaction = TransactionUtils.sign(transaction, key);

    //BroadcastTransaction
    rpcCli.broadcastTransaction(transaction);

  }

  public void transferAssetContract(byte[] asset_name, byte[] owner_address, byte[] to_address,
      long amount) {
    TransferAssetContract.Builder builder = TransferAssetContract.newBuilder();
    builder.setToAddress(ByteString.copyFrom(to_address));
    builder.setAssetName(ByteString.copyFrom(asset_name));
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    builder.setAmount(amount);

    TransferAssetContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.createTransferAssetTransaction2(contract);

    processTransaction(transactionExtention);
  }

  //create super node
  public void witnessCreateContract(byte[] owner_address, String url) {

    WitnessCreateContract.Builder builder = WitnessCreateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    builder.setUrl(ByteString.copyFrom(url.getBytes()));
    WitnessCreateContract contract = builder.build();

    TransactionExtention transactionExtention = rpcCli.createWitness2(contract);
    processTransaction(transactionExtention);
  }

  //vote super node
  public void voteWitnessContract(byte[] owner_address, HashMap<String, String> witness,
      boolean support) {

      VoteWitnessContract.Builder builder = VoteWitnessContract.newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(owner_address));
      for (String addressBase58 : witness.keySet()) {
        String value = witness.get(addressBase58);
        long count = Long.parseLong(value);
        VoteWitnessContract.Vote.Builder voteBuilder = VoteWitnessContract.Vote.newBuilder();
        byte[] address = WalletApi.decodeFromBase58Check(addressBase58);
        if (address == null) {
          continue;
        }
        voteBuilder.setVoteAddress(ByteString.copyFrom(address));
        voteBuilder.setVoteCount(count);
        builder.addVotes(voteBuilder.build());
      }

      VoteWitnessContract contract = builder.build();
      TransactionExtention transext = rpcCli.voteWitnessAccount2(contract);
      processTransaction(transext);

  }

  //update super node
  public void witnessUpdateContract(byte[] owner_address, String url) throws CipherException, IOException {
    WitnessUpdateContract.Builder builder = WitnessUpdateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    builder.setUpdateUrl(ByteString.copyFrom(url.getBytes()));
    WitnessUpdateContract contract = builder.build();

    TransactionExtention transext = rpcCli.updateWitness2(contract);
    processTransaction(transext);
  }

  //updateaccount name
  public void accountUpdateContract(byte[] account_name, byte[] owner_address) {
    AccountUpdateContract.Builder builder = AccountUpdateContract.newBuilder();
    builder.setAccountName(ByteString.copyFrom(account_name));
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    AccountUpdateContract contract = builder.build();

    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    processTransaction(transactionExtention);
  }

  //zhiya
  public void freezeBalanceContract(byte[] account_name, long frozen_balance, long frozen_duration,
        int resource, byte[] receiver_address) {
    FreezeBalanceContract.Builder builder = FreezeBalanceContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(account_name));
    builder.setFrozenBalance(frozen_balance);
    builder.setFrozenDuration(frozen_duration);
    builder.setResourceValue(resource);
    if (receiver_address != null) {
      builder.setReceiverAddress(ByteString.copyFrom(Objects.requireNonNull(receiver_address)));
    }
    FreezeBalanceContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    processTransaction(transactionExtention);
  }

  //unfreeze
  public void unfreezeBalanceContract(byte[] account_name, int resource, byte[] receiver_address) {
    UnfreezeBalanceContract.Builder builder = UnfreezeBalanceContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(account_name));
    builder.setResourceValue(resource);

    if (receiver_address != null) { //why cannot null
      builder.setReceiverAddress(ByteString.copyFrom(receiver_address));
    }
    UnfreezeBalanceContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    processTransaction(transactionExtention);
  }

  //get reward
  public void withdrawBalanceContract(byte[] account_name) {
    WithdrawBalanceContract.Builder builder = WithdrawBalanceContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(account_name));
    WithdrawBalanceContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    processTransaction(transactionExtention);
  }

  //unfreeze token
  public void unfreezeAssetContract(byte[] account_name) {
    UnfreezeAssetContract.Builder builder = UnfreezeAssetContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(account_name));
    UnfreezeAssetContract contract = builder.build();

    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    processTransaction(transactionExtention);
  }

  //update assertinfo
  public void updateAssetContract(byte[] ownerAddress, byte[] description, String url,
            long new_limit, long new_public_limit) {
    UpdateAssetContract.Builder builder = UpdateAssetContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(ownerAddress));
    builder.setDescription(ByteString.copyFrom(description));
    builder.setUrl(ByteString.copyFrom(url.getBytes()));
    builder.setNewLimit(new_limit);
    builder.setNewPublicLimit(new_public_limit);

    UpdateAssetContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
    processTransaction(transactionExtention);
  }

  //create proposal
  public void proposalCreateContract(byte[] ownerAddress, HashMap<Long, Long> parametersMap) {
    ProposalCreateContract.Builder builder = ProposalCreateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(ownerAddress));
    builder.putAllParameters(parametersMap);
    ProposalCreateContract contract = builder.build();

    TransactionExtention transactionExtention = rpcCli.proposalCreate(contract);
    processTransaction(transactionExtention);

  }

  //approve proposal
  public void proposalApproveContract(byte[] ownerAddress, long proposal_id, boolean is_add_approval) {
    ProposalApproveContract.Builder builder = ProposalApproveContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(ownerAddress));
    builder.setProposalId(proposal_id);
    builder.setIsAddApproval(is_add_approval);
    ProposalApproveContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.proposalApprove(contract);
    processTransaction(transactionExtention);
  }

  //del proposal
  public void proposalDeleteContract(byte[] ownerAddress, long proposal_id) {
    ProposalDeleteContract.Builder builder = ProposalDeleteContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(ownerAddress));
    builder.setProposalId(proposal_id);
    ProposalDeleteContract contract = builder.build();
    TransactionExtention transactionExtention = rpcCli.proposalDelete(contract);
    processTransaction(transactionExtention);
  }

  //set accout id
  public void setAccountIdContract(byte[] ownerAddress, byte[] accountIdBytes) {
    SetAccountIdContract.Builder builder = SetAccountIdContract.newBuilder();
    builder.setAccountId(ByteString.copyFrom(accountIdBytes));
    builder.setOwnerAddress(ByteString.copyFrom(ownerAddress));
    SetAccountIdContract contract = builder.build();
    Transaction transaction = rpcCli.createTransaction(contract);
    //if fail should return
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      System.out.println("Transaction is empty");
      return ;
    }

    System.out.println(Utils.printTransactionExceptId(transaction));
    System.out.println("before sign transaction hex string is " +
        ByteArray.toHexString(transaction.toByteArray()));

    System.out.println("transaction hash:" +
        ByteArray.toHexString(Sha256Sm3Hash.hash(transaction.getRawData().toByteArray())));

    //sign
    byte[] privateKey = ByteArray.fromHexString(
        "0ccb5fdaeba3a747983bd936b945268d044b46db2cdaf42827596aa008fd66e7");
    //byte[] privatekey = ByteArray.fromHexString("c209b57d51038ab6598d4622c92dfcf1e115f094aac964891c3ef4e101e43b2c");
    ECKey key = new ECKey(privateKey, true);
    transaction = TransactionUtils.sign(transaction, key);

    //BroadcastTransaction
    rpcCli.broadcastTransaction(transaction);
  }

  public void assetIssueContract(byte[] owner_address, String name, String abbrName,
      long totalSupply,
      int trxNum, int icoNum, int precision, long startTime,
      long endTime, int voteScore, String description, String url, long freeNetLimit,
      long publicFreeNetLimit, HashMap<String, String> frozenSupply
      ) {
    AssetIssueContract.Builder builder = AssetIssueContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));
    builder.setName(ByteString.copyFrom(name.getBytes()));
    builder.setAbbr(ByteString.copyFrom(abbrName.getBytes()));

    builder.setTotalSupply(totalSupply);
    builder.setTrxNum(trxNum);
    builder.setNum(icoNum);

    builder.setPrecision(precision);

    builder.setStartTime(startTime);
    builder.setEndTime(endTime);
    builder.setVoteScore(voteScore);

    builder.setDescription(ByteString.copyFrom(description.getBytes()));
    builder.setUrl(ByteString.copyFrom(url.getBytes()));
    builder.setFreeAssetNetLimit(freeNetLimit);
    builder.setPublicFreeAssetNetLimit(publicFreeNetLimit);

    System.out.println(
        "assetIssueContract " + totalSupply + " " + trxNum + " " + icoNum + " " + precision);
    System.out.println(
        "assetIssueContract " + startTime + " " + endTime + " " + description + " " + freeNetLimit);

    for (String daysStr : frozenSupply.keySet()) {
      String amountStr = frozenSupply.get(daysStr);
      long amount = Long.parseLong(amountStr);
      long days = Long.parseLong(daysStr);
      AssetIssueContract.FrozenSupply.Builder frozenSupplyBuilder
          = AssetIssueContract.FrozenSupply.newBuilder();
      frozenSupplyBuilder.setFrozenAmount(amount);
      frozenSupplyBuilder.setFrozenDays(days);
      builder.addFrozenSupply(frozenSupplyBuilder.build());
    }

    if (totalSupply <= 0) {
      System.out.println("totalSupply should greater than 0. but really is " + totalSupply);
      return;
    }

    if (trxNum <= 0) {
      System.out.println("trxNum should greater than 0. but really is " + trxNum);
      return;
    }

    if (icoNum <= 0) {
      System.out.println("num should greater than 0. but really is " + icoNum);
      return;
    }

    if (precision < 0) {
      System.out.println("precision should greater or equal to 0. but really is " + precision);
      return;
    }

    long now = System.currentTimeMillis();
    if (startTime <= now) {
      System.out.println("startTime should greater than now. but really is startTime("
          + startTime + ") now(" + now + ")");
      return;
    }
    if (endTime <= startTime) {
      System.out.println("endTime should greater or equal to startTime. but really is endTime("
          + endTime + ") startTime(" + startTime + ")");
      return;
    }

    if (freeNetLimit < 0) {
      System.out.println("freeAssetNetLimit should greater or equal to 0. but really is "
          + freeNetLimit);
      return;
    }
    if (publicFreeNetLimit < 0) {
      System.out.println("publicFreeAssetNetLimit should greater or equal to 0. but really is "
          + publicFreeNetLimit);
      return;
    }

    AssetIssueContract contract = builder.build();

    TransactionExtention transactionExtention = rpcCli.createAssetIssue2(contract);

    processTransaction(transactionExtention);

  }

  // 购买TRC-10代币
  public void participateAssetIssueContract(byte[] asset_name, byte[] owner_address,
      byte[] to_address,
      long amount) {
    ParticipateAssetIssueContract.Builder builder = ParticipateAssetIssueContract.newBuilder();
    builder.setToAddress(ByteString.copyFrom(to_address));
    builder.setAssetName(ByteString.copyFrom(asset_name));
    builder.setOwnerAddress(ByteString.copyFrom(owner_address));

    builder.setAmount(amount);

    ParticipateAssetIssueContract contract = builder.build();

    TransactionExtention transactionExtention =
        rpcCli.createParticipateAssetIssueTransaction2(contract);
    processTransaction(transactionExtention);

  }

  private boolean processTransaction(TransactionExtention transactionExtention) {
    if (transactionExtention == null) {
      System.out.println("transactionExtention is empty");
      return false;
    }
    Return ret = transactionExtention.getResult();
    if (!ret.getResult()) {
      System.out.println("Code = " + ret.getCode());
      System.out.println("Message = " + ret.getMessage().toStringUtf8());
      return false;
    }
    Transaction transaction = transactionExtention.getTransaction();
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      System.out.println("Transaction is empty");
      return false;
    }

    if (transaction.getRawData().getContract(0).getType()
        == ContractType.ShieldedTransferContract) {
      return false;
    }

    System.out.println(Utils.printTransactionExceptId(transactionExtention.getTransaction()));
    System.out.println("before sign transaction hex string is " +
        ByteArray.toHexString(transaction.toByteArray()));

    System.out.println("transaction hash:" +
        ByteArray.toHexString(Sha256Sm3Hash.hash(transaction.getRawData().toByteArray())));

    //sign
    //byte[] privatekey = ByteArray.fromHexString("0ccb5fdaeba3a747983bd936b945268d044b46db2cdaf42827596aa008fd66e7");
    byte[] privateKey = ByteArray.fromHexString("c209b57d51038ab6598d4622c92dfcf1e115f094aac964891c3ef4e101e43b2c");
    ECKey key = new ECKey(privateKey, true);
    transaction = TransactionUtils.sign(transaction, key);

    //BroadcastTransaction
    rpcCli.broadcastTransaction(transaction);
    return true;
  }

  public static GrpcClient init() {
    Config config = Configuration.getByPath("config.conf");

    String fullNode = "";
    String solidityNode = "";
    if (config.hasPath("soliditynode.ip.list")) {
      solidityNode = config.getStringList("soliditynode.ip.list").get(0);
    }
    if (config.hasPath("fullnode.ip.list")) {
      fullNode = config.getStringList("fullnode.ip.list").get(0);
    }
    System.out.println("Create GrpcClient");
    return new GrpcClient(fullNode, solidityNode);
  }

  public static void main(String[] args) {
    KxfSolidity client = new KxfSolidity();

    System.out.println("main");

    String own = "TGQg6FD1gTNE6vAa837Ngc1sxC4xqb461E";
    byte[] owner_address = WalletApi.decodeFromBase58Check(own);
    String accout = "TCydA89Kxdiuw7RUiRNLemDPVqmQF7W5nt";
    byte[] account_address = WalletApi.decodeFromBase58Check(accout);
    int type = 1;

    /*
    try {
      System.out.println("before createAccount");
      client.createAccount(owner_address, account_address, type);
      System.out.println("after createAccount");
    } catch (Exception e) {
      System.out.println("exception");
    }*/

    //acount

    try {
      System.out.println("before accountUpdateContract");
      client.transferContract(owner_address, account_address, 10);
      String owner_name = "e6b58be8af95e5ad97e7aca6e4b8b2";
      //update accout name
      client.accountUpdateContract(owner_name.getBytes(), owner_address);
      System.out.println("after accountUpdateContract");
    } catch (Exception e) {
      System.out.println("exception");
    }



    try {
      // create trc 10
      //Data startData = new Data(2022, 9, 21);
      long time = System.currentTimeMillis() + 50000;
      Date startData = new Date(time);
      long startTime = startData.getTime();
      time = time + 1000 * 3600 * 24 * 3L; //1 month
      Date end_data = new Date(time);
      long endTime = end_data.getTime();
      HashMap<String, String> frozenSupply = new HashMap<String, String>();
      frozenSupply.put("1", "2");
      String name = "e6b58be8af95e5ad97e7aca6e4b8b2";//ByteArray.toHexString("e6b58be8af95e5ad97e7aca6e4b8b2".getBytes());
      String abbrname = name;//ByteArray.toHexString("e6b58be8af95e5ad97e7aca6e4b8b2".getBytes());
      System.out.println("before assetIssueContract");
            /*
            client.assetIssueContract(owner_address,  name, abbrname, 1000,
            10, 10, 6, startTime, endTime, 0, "xfengtest", "www.baidu.com", 0,
            0, frozenSupply);
             */
      // transfer trc 10
      String asset_name = "1004966"; //1004966
      byte[] asset_address = asset_name.getBytes();
      System.out.println("before transferAssetContract" + asset_name);
      client.transferAssetContract(asset_address, owner_address, account_address, 100);
      System.out.println("after transferAssetContract");

      //purchase trc 10
      // client.participateAssetIssueContract(asset_address,account_address, owner_address, 10);

      //update assert info
      String update_des = "updatedis";
      String update_url = "www.bai12.com";
      long update_limit = 4;
      long update_publict_limit = 50;
      System.out.println("before updateAssetContract" + asset_name);
      //client.updateAssetContract(owner_address, update_des.getBytes(), update_url, update_limit, update_publict_limit);
      System.out.println("after updateAssetContract" + asset_name);

      //unfreeze assert
      System.out.println("before unfreezeAssetContract" + asset_name);
      //client.unfreezeAssetContract(owner_address);
      System.out.println("after unfreezeAssetContract" + asset_name);

    } catch (Exception e) {
      System.out.println("exception");
    }

    //freeze

    try {
      System.out.println("before freezeBalanceContract");
      //freeze
      client.freezeBalanceContract(owner_address, 3000000, 3,0, account_address);
      String url = "www.baidu.com";
      //unfreeze
      // client.unfreezeBalanceContract(owner_address, 0, account_address);
      System.out.println("after unfreezeBalanceContract");
    } catch (Exception e) {
      System.out.println("exception");
    }


    //super node
    try {
      System.out.println("before freezeBalanceContract");

      String url = "www.baidu.com";
      //create super
      // client.witnessCreateContract(owner_address, url);
      //vote
      HashMap<String, String> witness = new HashMap<String, String>();
      //witness.put("TYbgswVSQLXDyk3sYsHmxREEBbcZv4XBdA", "2");
      witness.put("TZBZ3LN7GqbCKzdghSWE5jKjZPJdbniMYk", "1");
      client.voteWitnessContract(owner_address, witness, true);
      url = "www.tengx.com";
      //client.witnessUpdateContract(owner_address, url);
      System.out.println("after WitnessCreateContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

    //withdraw:getwards
    try {
      System.out.println("before withdrawBalanceContract");
      //client.withdrawBalanceContract(owner_address);
      System.out.println("after withdrawBalanceContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

    //proposal
    try {
      System.out.println("before withdrawBalanceContract");
      //client.withdrawBalanceContract(owner_address);
      //HashMap<long, long> proposal = new HashMap<long, long>();
      //proposal.put(1, 2);
      //client.proposalCreateContract(owner_address, proposal);
      System.out.println("after withdrawBalanceContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

  }


}

