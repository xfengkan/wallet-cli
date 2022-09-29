
package org.tron.walletcli;
import com.google.protobuf.Any;
import org.bouncycastle.util.encoders.Hex;
import org.tron.protos.Protocol.Block;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import java.util.*;
import java.io.*;
import org.tron.common.crypto.Sha256Sm3Hash;
import lombok.extern.slf4j.Slf4j;
import org.tron.api.GrpcAPI.*;
import org.tron.common.utils.*;
import org.tron.core.config.Configuration;
import org.tron.core.exception.CancelException;
import org.tron.core.exception.CipherException;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Key;
import org.tron.protos.Protocol.Permission;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Result;
import org.tron.protos.Protocol.TransactionSign;
import org.tron.protos.contract.AccountContract;
import org.tron.protos.contract.AccountContract.AccountPermissionUpdateContract;
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
import org.tron.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.tron.protos.contract.SmartContractOuterClass.SmartContract;
import org.tron.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.tron.protos.contract.SmartContractOuterClass.UpdateEnergyLimitContract;
import org.tron.protos.contract.StorageContract.UpdateBrokerageContract;
import org.tron.protos.contract.WitnessContract.VoteWitnessContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.ParticipateAssetIssueContract;
import org.tron.protos.contract.WitnessContract.WitnessCreateContract;
import org.tron.protos.contract.BalanceContract.TransferContract;
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
    System.out.println(Utils.printTransactionExceptId(transaction));

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

  public void updateBrokerage(byte[] owner_address, int brokerage) {
    UpdateBrokerageContract.Builder updateBrokerageContract = UpdateBrokerageContract.newBuilder();
    updateBrokerageContract.setOwnerAddress(ByteString.copyFrom(owner_address));
    updateBrokerageContract.setBrokerage(brokerage);
    TransactionExtention transactionExtention = rpcCli.updateBrokerage(updateBrokerageContract.build());
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create trx failed!");
      if (transactionExtention != null) {
        System.out.println("Code = " + transactionExtention.getResult().getCode());
        System.out.println(
            "Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      }
      return;
    }

    processTransaction(transactionExtention);
  }

  public void updateEnergyLimitContract(byte[] ownerAddress, byte[] contractAddress, long originEnergyLimit) {
    UpdateEnergyLimitContract.Builder builder = UpdateEnergyLimitContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(ownerAddress));
    builder.setContractAddress(ByteString.copyFrom(contractAddress));
    builder.setOriginEnergyLimit(originEnergyLimit);
    UpdateEnergyLimitContract updateEnergyLimitContract = builder.build();

    TransactionExtention transactionExtention = rpcCli.updateEnergyLimit(updateEnergyLimitContract);
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create trx failed!");
      if (transactionExtention != null) {
        System.out.println("Code = " + transactionExtention.getResult().getCode());
        System.out.println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      }
      return;
    }

    processTransaction(transactionExtention);
  }

  //multi sign
  public void accountPermissionUpdate(byte[] owner, String permissionJson) {
    AccountPermissionUpdateContract.Builder builder = AccountPermissionUpdateContract.newBuilder();

    JSONObject permissions = JSONObject.parseObject(permissionJson);
    JSONObject owner_permission = permissions.getJSONObject("owner_permission");
    JSONObject witness_permission = permissions.getJSONObject("witness_permission");
    JSONArray active_permissions = permissions.getJSONArray("active_permissions");

    System.out.println("generate rpcCli accountPermissionUpdate");

    if (owner_permission != null) {
      Permission ownerPermission = json2Permission(owner_permission);
      builder.setOwner(ownerPermission);
    }
    System.out.println("after ownerPermission");
    if (witness_permission != null) {
      Permission witnessPermission = json2Permission(witness_permission);
      builder.setWitness(witnessPermission);
    }
    System.out.println("after witnessPermission");
    if (active_permissions != null) {
      List<Permission> activePermissionList = new ArrayList<>();
      for (int j = 0; j < active_permissions.size(); j++) {
        System.out.println("active_permissions"+j);
        JSONObject permission = active_permissions.getJSONObject(j);
        activePermissionList.add(json2Permission(permission));
      }
      builder.addAllActives(activePermissionList);
    }
    System.out.println("after active_permissions");
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    AccountPermissionUpdateContract contract = builder.build();
    System.out.println("before rpcCli accountPermissionUpdate");

    TransactionExtention transactionExtention = rpcCli.accountPermissionUpdate(contract);
    System.out.println("after rpcCli accountPermissionUpdate");
    processTransaction(transactionExtention);
  }


  private Permission json2Permission(JSONObject json) {
    Permission.Builder permissionBuilder = Permission.newBuilder();
    if (json.containsKey("type")) {
      int type = json.getInteger("type");
      permissionBuilder.setTypeValue(type);
    }
    if (json.containsKey("permission_name")) {
      String permission_name = json.getString("permission_name");
      permissionBuilder.setPermissionName(permission_name);
    }
    if (json.containsKey("threshold")) {
      long threshold = json.getLong("threshold");
      permissionBuilder.setThreshold(threshold);
    }
    if (json.containsKey("parent_id")) {
      int parent_id = json.getInteger("parent_id");
      permissionBuilder.setParentId(parent_id);
    }
    if (json.containsKey("operations")) {
      byte[] operations = ByteArray.fromHexString(json.getString("operations"));
      permissionBuilder.setOperations(ByteString.copyFrom(operations));
    }
    if (json.containsKey("keys")) {
      JSONArray keys = json.getJSONArray("keys");
      List<Key> keyList = new ArrayList<>();
      for (int i = 0; i < keys.size(); i++) {
        Key.Builder keyBuilder = Key.newBuilder();
        JSONObject key = keys.getJSONObject(i);
        String address = key.getString("address");
        long weight = key.getLong("weight");
        keyBuilder.setAddress(ByteString.copyFrom(decode58Check(address)));
        keyBuilder.setWeight(weight);
        keyList.add(keyBuilder.build());
      }
      permissionBuilder.addAllKeys(keyList);
    }
    return permissionBuilder.build();
  }

  private static byte[] decode58Check(String input) {
    byte[] decodeCheck = Base58.decode(input);
    if (decodeCheck.length <= 4) {
      return null;
    }
    byte[] decodeData = new byte[decodeCheck.length - 4];
    System.arraycopy(decodeCheck, 0, decodeData, 0, decodeData.length);
    byte[] hash0 = Sha256Sm3Hash.hash(decodeData);
    byte[] hash1 = Sha256Sm3Hash.hash(hash0);
    if (hash1[0] == decodeCheck[decodeData.length]
        && hash1[1] == decodeCheck[decodeData.length + 1]
        && hash1[2] == decodeCheck[decodeData.length + 2]
        && hash1[3] == decodeCheck[decodeData.length + 3]) {
      return decodeData;
    }
    return null;
  }

  //deploy
  public boolean deployContract(
      byte[] owner,
      String contractName,
      String ABI,
      String code,
      long feeLimit,
      long value,
      long consumeUserResourcePercent,
      long originEnergyLimit,
      long tokenValue,
      String tokenId,
      String libraryAddressPair,
      String compilerVersion) {

    CreateSmartContract contractDeployContract =
        WalletApi.createContractDeployContract(
            contractName,
            owner,
            ABI,
            code,
            value,
            consumeUserResourcePercent,
            originEnergyLimit,
            tokenValue,
            tokenId,
            libraryAddressPair,
            compilerVersion);

    TransactionExtention transactionExtention = rpcCli.deployContract(contractDeployContract);
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create trx failed!");
      if (transactionExtention != null) {
        System.out.println("Code = " + transactionExtention.getResult().getCode());
        System.out
            .println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      }
      return false;
    }

    TransactionExtention.Builder texBuilder = TransactionExtention.newBuilder();
    Transaction.Builder transBuilder = Transaction.newBuilder();
    Transaction.raw.Builder rawBuilder = transactionExtention.getTransaction().getRawData()
        .toBuilder();
    rawBuilder.setFeeLimit(feeLimit);
    transBuilder.setRawData(rawBuilder);
    for (int i = 0; i < transactionExtention.getTransaction().getSignatureCount(); i++) {
      ByteString s = transactionExtention.getTransaction().getSignature(i);
      transBuilder.setSignature(i, s);
    }
    for (int i = 0; i < transactionExtention.getTransaction().getRetCount(); i++) {
      Result r = transactionExtention.getTransaction().getRet(i);
      transBuilder.setRet(i, r);
    }
    texBuilder.setTransaction(transBuilder);
    texBuilder.setResult(transactionExtention.getResult());
    texBuilder.setTxid(transactionExtention.getTxid());
    transactionExtention = texBuilder.build();

    //    byte[] contractAddress = generateContractAddress(transactionExtention.getTransaction());
    //    System.out.println(
    //        "Your smart contract address will be: " + WalletApi.encode58Check(contractAddress));
    return processTransaction(transactionExtention);
  }

  public boolean triggerContract(
      byte[] owner,
      byte[] contractAddress,
      long callValue,
      byte[] data,
      long feeLimit,
      long tokenValue,
      String tokenId,
      boolean isConstant) {

    TriggerSmartContract triggerContract = WalletApi.triggerCallContract(owner, contractAddress, callValue,
        data, tokenValue, tokenId);
    TransactionExtention transactionExtention;
    if (isConstant) {
      transactionExtention = rpcCli.triggerConstantContract(triggerContract);
    } else {
      transactionExtention = rpcCli.triggerContract(triggerContract);
    }

    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create call trx failed!");
      System.out.println("Code = " + transactionExtention.getResult().getCode());
      System.out
          .println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      return false;
    }

    Transaction transaction = transactionExtention
        .getTransaction();

    if (transaction.getRetCount() != 0) {
      TransactionExtention.Builder builder =
          transactionExtention.toBuilder().clearTransaction().clearTxid();
      if (transaction.getRet(0).getRet() == Result.code.FAILED) {
        builder.setResult(builder.getResult().toBuilder().setResult(false));
      }
      System.out.println("Execution result = " + Utils.formatMessageString(builder.build()));
      return true;
    }

    TransactionExtention.Builder texBuilder = TransactionExtention.newBuilder();
    Transaction.Builder transBuilder = Transaction.newBuilder();
    Transaction.raw.Builder rawBuilder = transactionExtention.getTransaction().getRawData()
        .toBuilder();
    rawBuilder.setFeeLimit(feeLimit);
    transBuilder.setRawData(rawBuilder);
    for (int i = 0; i < transactionExtention.getTransaction().getSignatureCount(); i++) {
      ByteString s = transactionExtention.getTransaction().getSignature(i);
      transBuilder.setSignature(i, s);
    }
    for (int i = 0; i < transactionExtention.getTransaction().getRetCount(); i++) {
      Result r = transactionExtention.getTransaction().getRet(i);
      transBuilder.setRet(i, r);
    }
    texBuilder.setTransaction(transBuilder);
    texBuilder.setResult(transactionExtention.getResult());
    texBuilder.setTxid(transactionExtention.getTxid());
    transactionExtention = texBuilder.build();

    return processTransaction(transactionExtention);
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

    //System.out.println(Utils.printTransactionExceptId(transactionExtention.getTransaction()));
    System.out.println("before sign transaction hex string is " +
        ByteArray.toHexString(transaction.toByteArray()));

    System.out.println("transaction hash:" +
        ByteArray.toHexString(Sha256Sm3Hash.hash(transaction.getRawData().toByteArray())));

    //sign
    //byte[] privatekey = ByteArray.fromHexString("0ccb5fdaeba3a747983bd936b945268d044b46db2cdaf42827596aa008fd66e7");
    byte[] privateKey = ByteArray.fromHexString("c209b57d51038ab6598d4622c92dfcf1e115f094aac964891c3ef4e101e43b2c");
    ECKey key = new ECKey(privateKey, true);
    transaction = TransactionUtils.sign(transaction, key);
    System.out.println(Utils.printTransactionExceptId(transaction));

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
    System.out.println(" owner_address:");
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
      //String asset_name = "e6b58be8af95e5ad97e7aca6e4b8b2";
      byte[] asset_address = asset_name.getBytes();
      System.out.println("before transferAssetContract" + asset_name);
      client.transferAssetContract(asset_address, owner_address, account_address, 1);
      System.out.println("after transferAssetContract");

      //purchase trc 10
      client.participateAssetIssueContract(asset_address,account_address, owner_address, 10);

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
      client.unfreezeAssetContract(owner_address);
      System.out.println("after unfreezeAssetContract" + asset_name);

    } catch (Exception e) {
      System.out.println("exception");
    }

    //freeze

    try {
      System.out.println("before freezeBalanceContract");
      //freeze
      // client.freezeBalanceContract(owner_address, 3000000, 3,0, account_address);
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
      witness.put("TP8LKAf3R3FHDAcrQXuwBEWmaGrrUdRvzb", "1");
      client.voteWitnessContract(owner_address, witness, true);
      url = "www.tengx.com";
      //client.witnessUpdateContract(owner_address, url);
      System.out.println("after WitnessCreateContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

    //withdraw:
    try {
      System.out.println("before withdrawBalanceContract");
      //client.withdrawBalanceContract(owner_address);
      System.out.println("after withdrawBalanceContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

    //proposal
    try {
      System.out.println("before proposalCreateContract");
      HashMap<Long, Long> proposal = new HashMap<Long, Long>();
      proposal.put(1L, 2L);
      //client.proposalCreateContract(owner_address, proposal);
      System.out.println("after proposalCreateContract");

      //aprove
      client.proposalApproveContract(owner_address, 36, true);
      //client.proposalApproveContract(owner_address, 36, false);

      client.proposalDeleteContract(owner_address, 36);
      System.out.println("after proposalDeleteContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

    //set account id
    try {
      System.out.println("before setAccountIdContract");

      String accountId = "kxftest";
      client.setAccountIdContract(owner_address, accountId.getBytes());
      System.out.println("after setAccountIdContract");
    } catch (Exception e) {
      System.out.println("exception");
    }

    //updateBrokerage
    try {
      System.out.println("before updateBrokerage");
      client.updateBrokerage(owner_address, 12);
      System.out.println("after updateBrokerage");

      /*
      System.out.println("before updateEnergyLimitContract");
      String name = "e6b58be8af95e5ad97e7aca6e4b8b2";
      client.updateEnergyLimitContract(owner_address, name.getBytes(),55000);
      System.out.println("after updateEnergyLimitContract");
       */
    } catch (Exception e) {
      System.out.println("exception");
    }

    try {
      JSONObject permissions = new JSONObject();

      JSONObject owner_permission = new JSONObject();
      owner_permission.put("type", 0);
      owner_permission.put("id", 0);
      owner_permission.put("permission_name", "owner");
      owner_permission.put("threshold", 1);
      JSONArray own_keys = new JSONArray();
      JSONObject own_key1 = new JSONObject();
      own_key1.put("address", "TGQg6FD1gTNE6vAa837Ngc1sxC4xqb461E");
      own_key1.put("weight", 1);
      own_keys.add(own_key1);
      owner_permission.put("keys",own_keys);
      permissions.put("owner_permission", owner_permission);

      JSONObject witness_permission = new JSONObject();
      witness_permission.put("type", 1);
      witness_permission.put("id", 1);
      witness_permission.put("permission_name", "witness");
      witness_permission.put("threshold", 1);
      JSONArray wit_keys = new JSONArray();
      JSONObject wit_key1 = new JSONObject();
      wit_key1.put("address", "TCydA89Kxdiuw7RUiRNLemDPVqmQF7W5nt");
      wit_key1.put("weight", 1);
      wit_keys.add(wit_key1);
      witness_permission.put("keys",wit_keys);
      permissions.put("witness_permission", witness_permission);

      JSONArray active_permissions = new JSONArray();
      JSONObject active = new JSONObject();
      active.put("type", 2);
      active.put("id", 2);
      active.put("permission_name", "active0");
      active.put("threshold", 2);
      active.put("operations", "7fff1fc0037e0000000000000000000000000000000000000000000000000000");
      JSONArray active_keys = new JSONArray();
      JSONObject active_key1 = new JSONObject();
      active_key1.put("address", "TCydA89Kxdiuw7RUiRNLemDPVqmQF7W5nt");
      active_key1.put("weight", 1);
      JSONObject active_key2 = new JSONObject();
      active_key2.put("address", "TJvJPtSHsUbzYomuXbtHCVY1Ad2VgDarKz");
      active_key2.put("weight", 1);
      active_keys.add(active_key1);
      active_keys.add(active_key2);
      active.put("keys",active_keys);
      active_permissions.add(active);
      permissions.put("active_permissions", active_permissions);

      String permissionJson = permissions.toJSONString();
      System.out.println("permissionJson " + permissionJson);
      System.out.println("before accountPermissionUpdate");
      // client.accountPermissionUpdate(owner_address, permissionJson);
      System.out.println("after accountPermissionUpdate");

      //validate
      String to = "TCydA89Kxdiuw7RUiRNLemDPVqmQF7W5nt";
      byte[] to_address = WalletApi.decodeFromBase58Check(to);
      String private0 = "0ccb5fdaeba3a747983bd936b945268d044b46db2cdaf42827596aa008fd66e7";
      String private1 = "b86fb0b05060629894b3869c089664decef8e868bfba167b830ebe5586ee4c86";
      {
        //String private2 = "8E812436A0E3323166E1F0E8BA79E19E217B2C4A53C970D4CCA0CFB1078979DF";
        long amount = 10000000L;
        Transaction transaction = createTransaction(owner_address, to_address, amount);
        System.out.println("after createTransaction");
        TransactionExtention transactionExtention = addSignByApi(transaction,
            ByteArray.fromHexString(private0));
        //TransactionExtention transactionExtention = WalletApi
        //    .addSignByApi(transaction, ByteArray.fromHexString(private0));
        // System.out.println(Utils.printTransaction(transactionExtention));
        //TransactionSignWeight transactionSignWeight = WalletApi
        //   .getTransactionSignWeight(transactionExtention.getTransaction());
        System.out.println("after addSignByApi1");
        TransactionSignWeight transactionSignWeight = rpcCli.getTransactionSignWeight(transactionExtention.getTransaction());
        System.out.println("after transactionSignWeight1");
        //  System.out.println(Utils.printTransactionSignWeight(transactionSignWeight));

        //transactionExtention = WalletApi
        //    .addSignByApi(transactionExtention.getTransaction(), ByteArray.fromHexString(private1));
        //System.out.println(Utils.printTransaction(transactionExtention));
        //transactionSignWeight = WalletApi
        //    .getTransactionSignWeight(transactionExtention.getTransaction());
        transactionExtention = addSignByApi(transactionExtention.getTransaction(),
            ByteArray.fromHexString(private1));
//    System.out.println(Utils.printTransactionSignWeight(transactionSignWeight));
        System.out.println("after transactionSignWeight12");

        transactionSignWeight = rpcCli.getTransactionSignWeight(transactionExtention.getTransaction());
        System.out.println(Utils.printTransactionExceptId(transactionExtention.getTransaction()));
        rpcCli.broadcastTransaction(transactionExtention.getTransaction());
        //transactionExtention = WalletApi
        //    .addSignByApi(transactionExtention.getTransaction(), ByteArray.fromHexString(private2));
      }

      {
        //String private2 = "8E812436A0E3323166E1F0E8BA79E19E217B2C4A53C970D4CCA0CFB1078979DF";
        long amount = 10000000L;
        Transaction transaction = createTransaction(owner_address, to_address, amount);
        System.out.println("after createTransaction");
        TransactionExtention transactionExtention = addSignByApi(transaction,
            ByteArray.fromHexString(private0));
        System.out.println("after addSignByApi13");
        TransactionSignWeight transactionSignWeight = rpcCli.getTransactionSignWeight(
            transactionExtention.getTransaction());
        System.out.println("after transactionSignWeight123");
        transactionSignWeight = rpcCli.getTransactionSignWeight(transactionExtention.getTransaction());
        rpcCli.broadcastTransaction(transactionExtention.getTransaction());
      }

        {
        //1.create trans 2.mutilsign 3.getSignWeight 4. getApprovedList 5.broadcast
        long amount = 10000000L;
        Transaction transaction = createTransaction(owner_address, to_address, amount);
        System.out.println("after createTransaction22");
        TransactionExtention transactionExtention = addSignByApi(transaction,
            ByteArray.fromHexString(private0));
        System.out.println("after addSignByApi");
        TransactionSignWeight transactionSignWeight = rpcCli.getTransactionSignWeight(transaction);
        System.out.println("after transactionSignWeight22");
        transactionSignWeight = rpcCli.getTransactionSignWeight(transactionExtention.getTransaction());
        rpcCli.broadcastTransaction(transactionExtention.getTransaction());
      }


    } catch (Exception e) {
      System.out.println("exception");
    }

    //deploy smartcontract
    try {
      long feeLimit = 1000000;
      long value = 15;
      long consumeUserResourcePercent = 100;
      long originEnergyLimit = 100000;
      long tokenValue = 0;
      String tokenId = "";
      String libraryAddressPair = "";
      String compilerVersion = "0.5.10";

      File file = new File("helloword_abi.txt");
      BufferedReader br = new BufferedReader(new FileReader(file));
      String abi = "";
      while ((abi = br.readLine()) != null) {
        System.out.println(abi);
      }

      client.deployContract(owner_address, "hellword_kxf", abi,
     "608060405234801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002a57600080fd5b506104428061003a6000396000f3fe608060405234801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002a57600080fd5b50600436106100505760003560e01c80636630f88f14610055578063ce6d41de14610189575b600080fd5b61010e6004803603602081101561006b57600080fd5b810190808035906020019064010000000081111561008857600080fd5b82018360208201111561009a57600080fd5b803590602001918460018302840111640100000000831117156100bc57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f82011690508083019250505050505050919291929050505061020c565b6040518080602001828103825283818151815260200191508051906020019080838360005b8381101561014e578082015181840152602081019050610133565b50505050905090810190601f16801561017b5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6101916102c7565b6040518080602001828103825283818151815260200191508051906020019080838360005b838110156101d15780820151818401526020810190506101b6565b50505050905090810190601f1680156101fe5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b60608160009080519060200190610224929190610369565b5060008054600181600116156101000203166002900480601f0160208091040260200160405190810160405280929190818152602001828054600181600116156101000203166002900480156102bb5780601f10610290576101008083540402835291602001916102bb565b820191906000526020600020905b81548152906001019060200180831161029e57829003601f168201915b50505050509050919050565b606060008054600181600116156101000203166002900480601f01602080910402602001604051908101604052809291908181526020018280546001816001161561010002031660029004801561035f5780601f106103345761010080835404028352916020019161035f565b820191906000526020600020905b81548152906001019060200180831161034257829003601f168201915b5050505050905090565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f106103aa57805160ff19168380011785556103d8565b828001600101855582156103d8579182015b828111156103d75782518255916020019190600101906103bc565b5b5090506103e591906103e9565b5090565b61040b91905b808211156104075760008160009055506001016103ef565b5090565b9056fea26474726f6e582039e25aa00277b8b5561bb3ec7323496592c59e7a5df951971abc03a2d1c4da4764736f6c634300050a0031",
       feeLimit,
       value,
       consumeUserResourcePercent,
       originEnergyLimit,
       tokenValue,
       tokenId,
       libraryAddressPair,
       compilerVersion);

      String contractAddress = "";
      long callValue = 0;
      String methodStr = "postMessage(string)"; // function postMessage(string memory value) public returns (string memory) {
      boolean isHex = false;
      String argsStr = "kxftest";
      byte[] input = new byte[0];
      if (!methodStr.equalsIgnoreCase("#")) {
        input = Hex.decode(AbiUtil.parseMethod(methodStr, argsStr, isHex));
      }

      long feeLimit1 = 5000;
      long tokenValue1= 0;
      String tokenId1 = "";
      client.triggerContract(owner_address, contractAddress.getBytes(),
      callValue,
          input,
          feeLimit1,
       tokenValue1,
          tokenId1,
      true);

    } catch (Exception e) {
      System.out.println("exception");
    }


  }

  public static TransactionExtention addSignByApi(Transaction transaction, byte[] privateKey)
      throws CancelException {
    transaction = TransactionUtils.setExpirationTime(transaction);
    String tipsString = "Please input permission id.";
    transaction = TransactionUtils.setPermissionId(transaction, tipsString);
    TransactionSign.Builder builder = TransactionSign.newBuilder();
    builder.setPrivateKey(ByteString.copyFrom(privateKey));
    builder.setTransaction(transaction);
    return rpcCli.addSign(builder.build());
  }

  public static Transaction createTransaction(byte[] from, byte[] to, long amount) {
    Transaction.Builder transactionBuilder = Transaction.newBuilder();
    //Block newestBlock = WalletApi.getBlock(-1);
    Block newestBlock = rpcCli.getBlock(-1);

    Transaction.Contract.Builder contractBuilder = Transaction.Contract.newBuilder();
    TransferContract.Builder transferContractBuilder = TransferContract.newBuilder();
    transferContractBuilder.setAmount(amount);
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsOwner = ByteString.copyFrom(from);
    transferContractBuilder.setToAddress(bsTo);
    transferContractBuilder.setOwnerAddress(bsOwner);
    try {
      Any any = Any.pack(transferContractBuilder.build());
      contractBuilder.setParameter(any);
    } catch (Exception e) {
      return null;
    }
    contractBuilder.setType(Transaction.Contract.ContractType.TransferContract);
    transactionBuilder.getRawDataBuilder().addContract(contractBuilder)
        .setTimestamp(System.currentTimeMillis())
        .setExpiration(newestBlock.getBlockHeader().getRawData().getTimestamp() + 10 * 60 * 60 * 1000);
    Transaction transaction = transactionBuilder.build();
    Transaction refTransaction = setReference(transaction, newestBlock);
    return refTransaction;
  }

  public static Transaction setReference(Transaction transaction, Block newestBlock) {
    long blockHeight = newestBlock.getBlockHeader().getRawData().getNumber();
    byte[] blockHash = getBlockHash(newestBlock).getBytes();
    byte[] refBlockNum = ByteArray.fromLong(blockHeight);
    Transaction.raw rawData = transaction.getRawData().toBuilder()
        .setRefBlockHash(ByteString.copyFrom(ByteArray.subArray(blockHash, 8, 16)))
        .setRefBlockBytes(ByteString.copyFrom(ByteArray.subArray(refBlockNum, 6, 8)))
        .build();
    return transaction.toBuilder().setRawData(rawData).build();
  }

  public static Sha256Sm3Hash getBlockHash(Block block) {
    return Sha256Sm3Hash.of(block.getBlockHeader().getRawData().toByteArray());
  }

}

