package com.ngcin.ems.mapper;

import java.util.List;

public interface IPage<T> {

    int getCurrent();

    int getSize();

    long getTotal();

    long getPages();

    List<T> getRecords();

    IPage<T> records(List<T> records);

}
