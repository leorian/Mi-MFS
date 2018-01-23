package com.mi.mfs.dao;

import com.mi.mfs.entity.MFSEntity;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 */
public interface MFSDao {

    /**
     * 保存实体
     *
     * @param mfsEntity
     */
    void saveMFSEntity(MFSEntity mfsEntity);

    /**
     * 查询路径
     *
     * @param uuid
     * @return
     */
    MFSEntity findMFSEntityByUUID(String uuid);
}
