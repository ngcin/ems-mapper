package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Column;
import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.annotations.Table;
import com.ngcin.ems.mapper.core.IdType;

/**
 * Test entity with SNOWFLAKE ID (String type).
 */
@Table("t_transaction")
public class Transaction {

    @Id(type = IdType.SNOWFLAKE)
    private String transactionId;

    @Column(name = "txn_no")
    private String txnNo;

    @Column(name = "status")
    private String status;

    public Transaction() {
    }

    public Transaction(String txnNo, String status) {
        this.txnNo = txnNo;
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTxnNo() {
        return txnNo;
    }

    public void setTxnNo(String txnNo) {
        this.txnNo = txnNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", txnNo='" + txnNo + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
