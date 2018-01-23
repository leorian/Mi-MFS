package com.mi.mfs.controller;

import com.mi.mfs.entity.MFSEntity;
import com.mi.mfs.proxy.HttpReverseProxy;
import com.mi.mfs.service.MFSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by xiezg@317hu.com on 2018/1/23 0023.
 * 查看
 */
@Controller
@RequestMapping("view")
public class ViewController extends HttpReverseProxy {

    @Value("${mfs.url}")
    private String mfsUrl;

    @Autowired
    private MFSService mfsService;

    @RequestMapping(value = "{uuid}", method = RequestMethod.GET)
    public void index(HttpServletRequest request, HttpServletResponse response,
                      @PathVariable("uuid") String uuid) throws ServletException, IOException {
        service(request, response, uuid);
    }

    @Override
    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest, String uuid) {
        MFSEntity mfsEntity = mfsService.findMFSEntityByUUID(uuid);
        return mfsUrl + mfsEntity.getPath();
    }
}
