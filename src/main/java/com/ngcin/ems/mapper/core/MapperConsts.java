package com.ngcin.ems.mapper.core;

/**
 * MyBatis parameter name constants.
 */
public final class MapperConsts {

    private MapperConsts() {}

    /** Entity parameter name for update operations. */
    public static final String ENTITY = "et";

    /** Entity Where parameter name for query conditions. */
    public static final String ENTITY_WHERE = "ew";

    /** First parameter name (MyBatis default). */
    public static final String PARAM_1 = "param1";

    /** Page parameter name for pagination. */
    public static final String PAGE = "page";

    /** Optimistic lock old version parameter name. */
    public static final String VERSION_OLD = "version_old";

    public static final String IDS = "ids";
}
