package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Table;

/**
 * Test entity that extends BaseEntity to verify inheritance support.
 * The @Id field is inherited from BaseEntity.
 */
@Table("t_inherited_user")
public class InheritedUser extends BaseEntity {

    private String username;
    private String email;

    public InheritedUser() {
    }

    public InheritedUser(String username, String email) {
        this.username = username;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "InheritedUser{" +
                "id=" + getId() +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
