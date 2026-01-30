package com.ngcin.ems.test.mapper;

import com.ngcin.ems.mapper.BaseMapper;
import com.ngcin.ems.test.entity.InheritedUser;

/**
 * Mapper interface for InheritedUser entity.
 * Used to test that entities with inherited fields work with BaseMapper operations.
 */
public interface InheritedUserMapper extends BaseMapper<InheritedUser> {
}
