package com.ngcin.ems.mapper.core;

import com.ngcin.ems.mapper.IPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Page<T> implements IPage<T> {

    private int current = 1;

    private int size = 10;

    private long total = 0;

    private List<T> records = Collections.emptyList();


    public Page() {
    }

    public Page(int current, int size) {
        this.current = current;
        this.size = size;
    }

    public Page(int current, int size, List<T> records) {
        this.current = current;
        this.size = size;
        this.records = records;
    }

    public Page(int current, int size, long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records;
    }


    public Page<T> setCurrent(int current) {
        this.current = current > 0 ? current : 1;
        return this;
    }

    public Page<T> setSize(int size) {
        this.size = size > 0 ? size : 10;
        return this;
    }

    public Page<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    public IPage<T> setRecords(List<T> records) {
        this.records = records == null ? new ArrayList<>() : records;
        return this;
    }


    @Override
    public int getCurrent() {
        return current;
    }

    @Override
    public int getSize() {
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

}
