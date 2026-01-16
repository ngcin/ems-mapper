package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.*;
import com.ngcin.ems.mapper.core.IdType;

/**
 * Test entity with version control (@Version annotation).
 */
@Table("t_product_v2")
public class ProductV2 {

    @Id(type = IdType.AUTO)
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "price")
    private Double price;

    @Version
    @Column(name = "version")
    private Integer version;

    public ProductV2() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
