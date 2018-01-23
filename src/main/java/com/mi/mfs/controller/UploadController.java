package com.mi.mfs.controller;

import com.alibaba.fastjson.JSON;
import com.mi.mfs.entity.MFSEntity;
import com.mi.mfs.proxy.FastDFSClient;
import com.mi.mfs.service.MFSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 * 上传
 */
@RestController
@RequestMapping("upload")
public class UploadController {

    @Autowired
    private MFSService mfsService;

    @RequestMapping(value = "", method = RequestMethod.POST)
    public String upload(@RequestParam("file") MultipartFile file,
                         HttpServletRequest request) throws IOException {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        System.out.println("fileName-->" + fileName);
        System.out.println("getContentType-->" + contentType);
        String fileId = FastDFSClient.uploadByteArray(file.getBytes(),fileName);
        MFSEntity mfsEntity = new MFSEntity();
        mfsEntity.setUuid(UUID.randomUUID().toString().replace("-",""));
        mfsEntity.setPath(fileId);
        mfsService.saveMFSEntity(mfsEntity);
        System.out.println("Upload remote file " + fileName + " ok, fileid=" + fileId);
        return JSON.toJSONString(mfsEntity);
    }
}
