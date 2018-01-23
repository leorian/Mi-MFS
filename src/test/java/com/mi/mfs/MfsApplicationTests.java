package com.mi.mfs;

import com.mi.mfs.entity.MFSEntity;
import com.mi.mfs.service.MFSService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest
public class MfsApplicationTests {

	@Autowired
	private MFSService mfsService;

	@Test
	public void contextLoads() {
	}

	@Test
	public void test(){
		System.out.println(UUID.randomUUID().toString().replace("-",""));
	}

	/**
	 * http://192.168.164.129:8888/group1/M00/00/00/wKikgVpmjCyADXqHAAVFOL7FJU4.tar.gz
	 */
	@Test
	public void testSaveMFSEntity() {
		MFSEntity mfsEntity = new MFSEntity();
		mfsEntity.setUuid(UUID.randomUUID().toString().replace("-",""));
		mfsEntity.setPath("group1/M00/00/00/wKikgVpm2CGAIEEqAADkeeWnPoI248.pdf");
		mfsService.saveMFSEntity(mfsEntity);
		System.out.println(mfsEntity.getUuid());
	}
}
