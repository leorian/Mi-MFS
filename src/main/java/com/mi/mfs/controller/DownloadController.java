package com.mi.mfs.controller;

import com.mi.mfs.entity.MFSEntity;
import com.mi.mfs.proxy.FastDFSClient;
import com.mi.mfs.service.MFSService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 * 下载
 */
@Controller
@RequestMapping("download")
public class DownloadController {
    @Autowired
    private MFSService mfsService;

    @RequestMapping(value = "{uuid}", method = RequestMethod.GET)
    public void download(HttpServletRequest request, HttpServletResponse response,
                         @PathVariable("uuid") String uuid) throws ServletException, IOException {
        MFSEntity mfsEntity = mfsService.findMFSEntityByUUID(uuid);
        InputStream inputStream = FastDFSClient.downloadFile(mfsEntity.getPath());
        IOUtils.copy(inputStream, response.getOutputStream());
    }
}
