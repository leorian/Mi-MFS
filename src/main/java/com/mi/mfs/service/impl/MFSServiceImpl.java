package com.mi.mfs.service.impl;

import com.mi.mfs.dao.MFSDao;
import com.mi.mfs.entity.MFSEntity;
import com.mi.mfs.service.MFSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 */
@Component
public class MFSServiceImpl implements MFSService {

    @Autowired
    private MFSDao mfsDao;

    @Override
    public void saveMFSEntity(MFSEntity mfsEntity) {
        mfsDao.saveMFSEntity(mfsEntity);
    }

    @Override
    public MFSEntity findMFSEntityByUUID(String uuid) {
        return mfsDao.findMFSEntityByUUID(uuid);
    }
}
