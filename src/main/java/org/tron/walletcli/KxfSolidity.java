
package org.tron.walletcli;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import io.netty.util.internal.StringUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.*;
import org.tron.common.utils.*;
import org.tron.core.config.Configuration;
import org.tron.core.config.Parameter;
import org.tron.core.exception.CancelException;
import org.tron.core.exception.CipherException;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.zen.ShieldedAddressInfo;
import org.tron.core.zen.ShieldedNoteInfo;
import org.tron.core.zen.ShieldedTRC20NoteInfo;
import org.tron.core.zen.ShieldedTRC20Wrapper;
import org.tron.core.zen.ShieldedWrapper;
import org.tron.core.zen.ZenUtils;
import org.tron.core.zen.address.DiversifierT;
import org.tron.core.zen.address.ExpandedSpendingKey;
import org.tron.core.zen.address.FullViewingKey;
import org.tron.core.zen.address.SpendingKey;
import org.tron.keystore.StringUtils;
import org.tron.keystore.WalletFile;
import org.tron.keystore.WalletUtils;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.ChainParameters;
import org.tron.protos.Protocol.Exchange;
import org.tron.protos.Protocol.MarketOrder;
import org.tron.protos.Protocol.MarketOrderList;
import org.tron.protos.Protocol.MarketOrderPairList;
import org.tron.protos.Protocol.MarketPriceList;
import org.tron.protos.Protocol.Proposal;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.contract.AccountContract;
import org.tron.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.tron.protos.contract.BalanceContract;
import org.tron.protos.contract.ShieldContract.IncrementalMerkleVoucherInfo;
import org.tron.protos.contract.ShieldContract.OutputPoint;
import org.tron.protos.contract.ShieldContract.OutputPointInfo;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.common.crypto.ECKey;

import org.tron.walletserver.WalletApi;
import org.tron.walletserver.GrpcClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
public class KxfSolidity {
    private static GrpcClient rpcCli = init();
    public void createAccount(byte[] owner_address, byte[] account_address,
                                int type) throws CipherException, IOException {
        AccountContract.AccountCreateContract.Builder builder = AccountContract.AccountCreateContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner_address));
        builder.setAccountAddress(ByteString.copyFrom(account_address));
        Protocol.AccountType accout_type = Protocol.AccountType.values()[type];
        builder.setType(accout_type);

        AccountContract.AccountCreateContract accout = builder.build();
        rpcCli.createAccount2(accout);

    }


    public void transferContract(byte[] owner_address, byte[] to_address,
                                 long amount) throws CipherException, IOException {
        BalanceContract.TransferContract.Builder builder =  BalanceContract.TransferContract.newBuilder();
        builder.setAmount(amount);
        builder.setOwnerAddress(ByteString.copyFrom(owner_address));
        builder.setToAddress(ByteString.copyFrom(to_address));

        BalanceContract.TransferContract contract = builder.build();
        //1.create   2.sig   3.broadcast  4.get
        TransactionExtention transext = rpcCli.createTransaction2(contract);
        if (transext == null) {
            return;
        }

        Transaction transac = transext.getTransaction();
        if (transac == null || transac.getRawData().getContractCount() == 0) {
            System.out.println("Transaction is empty");
            return;
        }

        System.out.println(Utils.printTransactionExceptId(transac));
        System.out.println("before sign transaction hex string is " +
                ByteArray.toHexString(transac.toByteArray()));
        //sign
        byte[] privatekey = ByteArray.fromHexString("c209b57d51038ab6598d4622c92dfcf1e115f094aac964891c3ef4e101e43b2c");
        ECKey key = new ECKey(privatekey, true);
        transac = TransactionUtils.sign(transac, key);

        //BroadcastTransaction
        rpcCli.broadcastTransaction(transac);

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
        }
        */

        try {
            System.out.println("before transferContract");
            client.transferContract(owner_address, account_address, 10);
            System.out.println("after transferContract");
        } catch (Exception e) {
            System.out.println("exception");
        }



    }


}

