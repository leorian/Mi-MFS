package com.mi.mfs.dao.impl;

import com.mi.mfs.dao.MFSDao;
import com.mi.mfs.entity.MFSEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 */
@Component
public class MFSDaoImpl implements MFSDao {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void saveMFSEntity(MFSEntity mfsEntity) {
        mongoTemplate.save(mfsEntity);
    }

    @Override
    public MFSEntity findMFSEntityByUUID(String uuid) {
        Query query=new Query(Criteria.where("uuid").is(uuid));
        MFSEntity mfsEntity=  mongoTemplate.findOne(query , MFSEntity.class);
        return mfsEntity;
    }
}
