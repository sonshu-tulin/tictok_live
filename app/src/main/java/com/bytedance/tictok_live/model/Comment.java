package com.bytedance.tictok_live.model;

/**
 * 评论实体类
 */
public class Comment {
    private String createdAt;
    private String name;
    private String avatar;
    private String comment;
    private String id;

    public Comment(){}

    public Comment(String createdAt, String name, String avatar, String comment, String id) {
        this.createdAt = createdAt;
        this.name = name;
        this.avatar = avatar;
        this.comment = comment;
        this.id = id;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "createdAt='" + createdAt + '\'' +
                ", name='" + name + '\'' +
                ", avatar='" + avatar + '\'' +
                ", comment='" + comment + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
