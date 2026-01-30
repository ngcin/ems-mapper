package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.ISelect;
import com.ngcin.ems.mapper.core.Page;

import java.util.List;

public class PageHelper {
    private static final ThreadLocal<IPage<?>> LOCAL_PAGE = new ThreadLocal<>();

    public PageHelper() {
    }

    public static IPage<?> getLocalPage() {
        return LOCAL_PAGE.get();
    }

    private static void setLocalPage(IPage<?> page) {
        LOCAL_PAGE.set(page);
    }

    public static <T> IPage<T> page(int current, int size, ISelect<T> select) {
        try {
            IPage<T> page = new Page<>(current, size);
            setLocalPage(page);
            List<T> list = select.doSelect();
            if (null != list && list.size() == 1) {
                Object pageInfo = list.get(0);
                if (pageInfo instanceof Page<?>) {
                    return (Page<T>) pageInfo;
                }
            }
            return page.records(list);
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