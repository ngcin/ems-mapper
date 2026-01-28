package com.ngcin.ems.mapper.core;

import com.ngcin.ems.mapper.IPage;
import com.ngcin.ems.mapper.MapperException;

import java.util.List;

public class PageHelper {
    private static final ThreadLocal<IPage<?>> LOCAL_PAGE = new ThreadLocal<>();

    public PageHelper() {
    }

    public static IPage<?> getLocalPage() {
        return LOCAL_PAGE.get();
    }

    private static void setLocalPage(Page<?> page) {
        LOCAL_PAGE.set(page);
    }

    public static <T> IPage<T> page(int current, int size, ISelect<T> select) {
        try {
            Page<T> pageQuery = new Page<>(current, size);
            setLocalPage(pageQuery);
            List<T> list = select.doSelect();
            if (null != list && list.size() == 1) {
                Object pageInfo = list.get(0);
                if (pageInfo instanceof Page<?>) {
                    return (Page<T>) pageInfo;
                }
            }
            return new Page<>(current, size, pageQuery.getTotal(), list);
        } catch (Exception ex) {
            throw new MapperException("Do page error : " + ex.getMessage(), ex);
        } finally {
            cleanContext();
        }
    }

    public static void cleanContext() {
        LOCAL_PAGE.remove();
    }
}