package com.mi.mfs.service;

import com.mi.mfs.entity.MFSEntity;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 */
public interface MFSService {
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
