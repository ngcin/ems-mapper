package com.ngcin.ems.mapper;

import org.apache.ibatis.annotations.*;

import java.io.Serializable;
import java.util.List;

public interface BaseMapper<T> {

//    @SelectProvider(type = BaseMapperProvider.class, method = "getById")
//    T getById(Serializable id);

    @InsertProvider(type = BaseMapperProvider.class, method = "insert")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(T entity);

//    @UpdateProvider(type = BaseMapperProvider.class, method = "updateById")
//    int updateById(T entity);
//
//    @DeleteProvider(type = BaseMapperProvider.class, method = "physicalDeleteById")
//    int physicalDeleteById(Serializable id);
//
//    @DeleteProvider(type = BaseMapperProvider.class, method = "deleteById")
//    int deleteById(Serializable id);
//
//    @SelectProvider(type = BaseMapperProvider.class, method = "selectAll")
//    List<T> selectAll();
//
//    @SelectProvider(type = BaseMapperProvider.class, method = "select")
//    List<T> select(T entity);
//
//    @DeleteProvider(type = BaseMapperProvider.class, method = "logicDeleteByEntity")
//    int delete(T entity);
//
//    @DeleteProvider(type = BaseMapperProvider.class, method = "physicalDelete")
//    int physicalDelete(T entity);

}
