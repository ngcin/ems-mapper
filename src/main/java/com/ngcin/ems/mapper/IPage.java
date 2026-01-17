package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.Page;

import java.util.List;

public interface IPage<T> {

    long getCurrent();

    long getSize();

    long getTotal();

    long getPages();

    List<T> getRecords();

    Page<T> setRecords(List<T> records);

    boolean isHitCount();

    boolean isSearchCount();

    boolean isAsc();

    String getOrders();

    default long offset() {
        return (getCurrent() - 1) * getSize();
    }
}
