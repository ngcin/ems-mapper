package com.ngcin.ems.mapper.core;

import com.ngcin.ems.mapper.IPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Page<T> implements IPage<T> {

    private long current = 1;

    private long size = 10;

    private long total = 0;

    private List<T> records = Collections.emptyList();

    private boolean hitCount = false;

    private boolean searchCount = true;

    private boolean isAsc = false;

    private String orders = "";

    public Page() {
    }

    public Page(long current, long size) {
        this.current = current;
        this.size = size;
    }

    public Page<T> setCurrent(long current) {
        this.current = current > 0 ? current : 1;
        return this;
    }

    public Page<T> setSize(long size) {
        this.size = size > 0 ? size : 10;
        return this;
    }

    public Page<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    public Page<T> setRecords(List<T> records) {
        this.records = records == null ? new ArrayList<>() : records;
        return this;
    }

    public Page<T> setHitCount(boolean hitCount) {
        this.hitCount = hitCount;
        return this;
    }

    public Page<T> setSearchCount(boolean searchCount) {
        this.searchCount = searchCount;
        return this;
    }

    public Page<T> setAsc(boolean asc) {
        isAsc = asc;
        return this;
    }

    public Page<T> setOrders(String orders) {
        this.orders = orders;
        return this;
    }

    @Override
    public long getCurrent() {
        return current;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public long getPages() {
        if (getSize() == 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }

    @Override
    public List<T> getRecords() {
        return records;
    }

    @Override
    public boolean isHitCount() {
        return hitCount;
    }

    @Override
    public boolean isSearchCount() {
        return searchCount;
    }

    @Override
    public boolean isAsc() {
        return isAsc;
    }

    @Override
    public String getOrders() {
        return orders;
    }

    @Override
    public long offset() {
        return (current - 1) * size;
    }

    public Page<T> addOrder(String... orders) {
        if (orders != null && orders.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (String order : orders) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(order);
            }
            this.orders = sb.toString();
        }
        return this;
    }
}
