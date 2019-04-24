package com.vodworks.chaincode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;

import java.util.List;

public class TTChainCode extends ChaincodeBase {

    private static Log _logger = LogFactory.getLog(TTChainCode.class);

    @Override
    public Response init(ChaincodeStub chaincodeStub) {

        try {
            _logger.info("Init java simple chaincode");

            String func = chaincodeStub.getFunction();
            if (!func.equals("init")) {
                return newErrorResponse("function other than init is not supported");
            }

            List<String> args = chaincodeStub.getParameters();
            if (args.size() != 4) {
                return newErrorResponse("Incorrect number of arguments. Expecting 4");
            }

            // Initialize the chaincode
            String account1Key = args.get(0);
            int account1Value = Integer.parseInt(args.get(1));
            String account2Key = args.get(2);
            int account2Value = Integer.parseInt(args.get(3));

            _logger.info(String.format("account %s, value = %s; account %s, value %s", account1Key, account1Value, account2Key, account2Value));
            chaincodeStub.putStringState(account1Key, args.get(1));
            chaincodeStub.putStringState(account2Key, args.get(3));

        } catch (Exception ex) {
            return newErrorResponse(ex);
        }

        return newSuccessResponse();
    }

    @Override
    public Response invoke(ChaincodeStub chaincodeStub) {
        try {
            _logger.info("Invoke java simple chaincode");

            String func = chaincodeStub.getFunction();
            List<String> params = chaincodeStub.getParameters();

            if (func.equals("invoke")) {
                return invoke(chaincodeStub, params);
            }

            if (func.equals("delete")) {
                return delete(chaincodeStub, params);
            }

            if (func.equals("query")) {
                return query(chaincodeStub, params);
            }

            return newErrorResponse("Invalid invoke function name. Expecting one of: [\"invoke\", \"delete\", \"query\"]");

        } catch (Throwable e) {
            return newErrorResponse(e);
        }
    }

    private Response invoke(ChaincodeStub stub, List<String> args) {

        if (args.size() != 3) {
            return newErrorResponse("Incorrect number of arguments. Expecting 3");
        }

        String accountFromKey = args.get(0);
        String accountToKey = args.get(1);

        String accountFromValueStr = stub.getStringState(accountFromKey);
        if (accountFromValueStr == null) {
            return newErrorResponse(String.format("Entity %s not found", accountFromKey));
        }

        int accountFromValue = Integer.parseInt(accountFromValueStr);

        String accountToValueStr = stub.getStringState(accountToKey);
        if (accountToValueStr == null) {
            return newErrorResponse(String.format("Entity %s not found", accountToKey));
        }

        int accountToValue = Integer.parseInt(accountToValueStr);

        int amount = Integer.parseInt(args.get(2));

        if (amount > accountFromValue) {
            return newErrorResponse(String.format("not enough money in account %s", accountFromKey));
        }

        accountFromValue -= amount;
        accountToValue += amount;

        _logger.info(String.format("new value of A: %s", accountFromValue));
        _logger.info(String.format("new value of B: %s", accountToValue));

        stub.putStringState(accountFromKey, Integer.toString(accountFromValue));
        stub.putStringState(accountToKey, Integer.toString(accountToValue));

        _logger.info("Transfer complete");

        return newSuccessResponse("invoke finished successfully");
    }

    // Deletes an entity from state
    private Response delete(ChaincodeStub stub, List<String> args) {

        if (args.size() != 1) {
            return newErrorResponse("Incorrect number of arguments. Expecting 1");
        }

        String key = args.get(0);

        // Delete the key from the state in ledger
        stub.delState(key);

        return newSuccessResponse();
    }

    // query callback representing the query of a chaincode
    private Response query(ChaincodeStub stub, List<String> args) {

        if (args.size() != 1) {
            return newErrorResponse("Incorrect number of arguments. Expecting name of the person to query");
        }

        String key = args.get(0);
        //byte[] stateBytes
        String val = stub.getStringState(key);

        if (val == null) {
            return newErrorResponse(String.format("Error: state for %s is null", key));
        }

        _logger.info(String.format("Query Response:\nName: %s, Amount: %s\n", key, val));
        return newSuccessResponse(val);
    }

    public static void main(String[] args) {

        new TTChainCode().start(args);
    }
}