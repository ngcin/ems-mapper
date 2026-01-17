package com.ngcin.ems.mapper;

import com.ngcin.ems.mapper.core.MapperConsts;
import com.ngcin.ems.mapper.core.Page;
import org.apache.ibatis.annotations.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface BaseMapper<T> {

    @InsertProvider(type = BaseMapperProvider.class, method = "insert")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(T entity);

    @InsertProvider(type = BaseMapperProvider.class, method = "insertSelective")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSelective(T entity);

    @InsertProvider(type = BaseMapperProvider.class, method = "insertBatch")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBatch(@Param("list") List<T> entities);

    @UpdateProvider(type = BaseMapperProvider.class, method = "updateById")
    int updateById(T entity);

    @UpdateProvider(type = BaseMapperProvider.class, method = "updateSelectiveById")
    int updateSelectiveById(@Param("entity") T entity);

    @SelectProvider(type = BaseMapperProvider.class, method = "getById")
    T getById(@Param("id") Serializable id);

    @SelectProvider(type = BaseMapperProvider.class, method = "selectCount")
    long selectCount(@Param(MapperConsts.ENTITY_WHERE) T queryEntity);

    @SelectProvider(type = BaseMapperProvider.class, method = "selectBatchIds")
    List<T> selectBatchIds(@Param("ids") Collection<Serializable> ids);

    @SelectProvider(type = BaseMapperProvider.class, method = "selectAll")
    List<T> selectAll();

    @SelectProvider(type = BaseMapperProvider.class, method = "selectList")
    List<T> selectList(@Param(MapperConsts.ENTITY_WHERE) T query);

    @SelectProvider(type = BaseMapperProvider.class, method = "selectOne")
    T selectOne(@Param(MapperConsts.ENTITY_WHERE) T query);

    @UpdateProvider(type = BaseMapperProvider.class, method = "deleteById")
    int deleteById(@Param("id") Serializable id);

    @DeleteProvider(type = BaseMapperProvider.class, method = "removeById")
    int removeById(@Param("id") Serializable id);

    @UpdateProvider(type = BaseMapperProvider.class, method = "delete")
    int delete(@Param(MapperConsts.ENTITY_WHERE) T entity);

    @DeleteProvider(type = BaseMapperProvider.class, method = "remove")
    int remove(@Param(MapperConsts.ENTITY_WHERE) T entity);

    @SelectProvider(type = BaseMapperProvider.class, method = "selectPage")
    List<T> selectPage(@Param(MapperConsts.PAGE) IPage<T> page, @Param(MapperConsts.ENTITY_WHERE) T query);

    default IPage<T> page(@Param(MapperConsts.PAGE) IPage<T> page, @Param(MapperConsts.ENTITY_WHERE) T query) {
        List<T> list = selectPage(page, query);
        page.setRecords(list);
        return page;
    }
}
