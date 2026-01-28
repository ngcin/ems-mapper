package com.ngcin.ems.mapper.core;

import java.util.List;

public interface ISelect<T> {
    List<T> doSelect();
}