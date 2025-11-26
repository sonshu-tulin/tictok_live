package com.bytedance.tictok_live.model;

/**
 * 主播信息
 */
public class HostInfo {
    private String createdAt;
    private String name;
    private String avatar;
    private String roomName;
    private int followerNum;
    private String id;

    public HostInfo() {}

    public HostInfo(String id, int followerNum, String roomName, String avatar, String name, String createdAt) {
        this.id = id;
        this.followerNum = followerNum;
        this.roomName = roomName;
        this.avatar = avatar;
        this.name = name;
        this.createdAt = createdAt;
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

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public int getFollowerNum() {
        return followerNum;
    }

    public void setFollowerNum(int followerNum) {
        this.followerNum = followerNum;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "HostInfo{" +
                "createdAt='" + createdAt + '\'' +
                ", name='" + name + '\'' +
                ", avatar='" + avatar + '\'' +
                ", roomName='" + roomName + '\'' +
                ", followerNum=" + followerNum +
                ", id='" + id + '\'' +
                '}';
    }
}
