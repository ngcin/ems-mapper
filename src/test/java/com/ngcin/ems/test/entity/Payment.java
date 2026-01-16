package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Column;
import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.annotations.Table;
import com.ngcin.ems.mapper.core.IdType;

import java.math.BigDecimal;

/**
 * Test entity with SNOWFLAKE ID (Long type).
 */
@Table("t_payment")
public class Payment {

    @Id(type = IdType.SNOWFLAKE)
    private Long paymentId;

    @Column(name = "payment_no")
    private String paymentNo;

    @Column(name = "amount")
    private BigDecimal amount;

    public Payment() {
    }

    public Payment(String paymentNo, BigDecimal amount) {
        this.paymentNo = paymentNo;
        this.amount = amount;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId=" + paymentId +
                ", paymentNo='" + paymentNo + '\'' +
                ", amount=" + amount +
                '}';
    }
}
