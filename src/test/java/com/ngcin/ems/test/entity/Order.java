package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Column;
import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.annotations.Table;
import com.ngcin.ems.mapper.core.IdType;

import java.math.BigDecimal;

/**
 * Test entity with UUID ID (String type).
 */
@Table("t_order")
public class Order {

    @Id(type = IdType.UUID)
    private String orderId;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "amount")
    private BigDecimal amount;

    public Order() {
    }

    public Order(String orderNo, BigDecimal amount) {
        this.orderNo = orderNo;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", amount=" + amount +
                '}';
    }
}
