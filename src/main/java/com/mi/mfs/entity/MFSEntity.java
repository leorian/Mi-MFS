package com.mi.mfs.entity;

import java.io.Serializable;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 */
public class MFSEntity implements Serializable {

    /**
     * UUID
     */
    private String uuid;

    /**
     * 存储路径
     */
    private String path;


    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
