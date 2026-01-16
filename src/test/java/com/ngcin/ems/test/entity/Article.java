package com.ngcin.ems.test.entity;

import com.ngcin.ems.mapper.annotations.Column;
import com.ngcin.ems.mapper.annotations.Deleted;
import com.ngcin.ems.mapper.annotations.Id;
import com.ngcin.ems.mapper.annotations.Table;
import com.ngcin.ems.mapper.core.IdType;

/**
 * Test entity with soft delete support (@Deleted annotation).
 */
@Table("t_article")
public class Article {

    @Id(type = IdType.AUTO)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Deleted
    @Column(name = "deleted")
    private Integer deleted;

    public Article() {
    }

    public Article(String title, String content) {
        this.title = title;
        this.content = content;
        this.deleted = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "Article{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", deleted=" + deleted +
                '}';
    }
}
