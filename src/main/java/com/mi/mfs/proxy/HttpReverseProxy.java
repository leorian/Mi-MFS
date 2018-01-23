package com.mi.mfs.proxy;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.List;


public class HttpReverseProxy implements InitializingBean, DisposableBean {
    protected static Logger logger = LoggerFactory.getLogger(HttpReverseProxy.class);
    @Autowired
    private Environment env;
    public static final String P_LOG = "log";
    public static final String P_FORWARDEDFOR = "forwardip";
    public static final String P_PRESERVEHOST = "preserveHost";
    public static final String P_PRESERVECOOKIES = "preserveCookies";
    public static final String P_HANDLEREDIRECTS = "http.protocol.handle-redirects";
    public static final String P_CONNECTTIMEOUT = "http.socket.timeout";
    public static final String P_READTIMEOUT = "http.read.timeout";
    protected static final String P_TARGET_URI = "mfs.url";
    protected static final String ATTR_TARGET_URI =
            HttpReverseProxy.class.getSimpleName() + ".targetUri";
    protected static final String ATTR_TARGET_HOST =
            HttpReverseProxy.class.getSimpleName() + ".targetHost";
    protected boolean doLog = false;
    protected boolean doForwardIP = true;
    protected boolean doSendUrlFragment = true;
    protected boolean doPreserveHost = false;
    protected boolean doPreserveCookies = false;
    protected boolean doHandleRedirects = false;
    protected int connectTimeout = -1;
    protected int readTimeout = -1;
    protected String targetUri;
    protected URI targetUriObj;
    protected HttpHost targetHost;
    private HttpClient proxyClient;

    protected String getTargetUri(HttpServletRequest servletRequest) {
        return (String) servletRequest.getAttribute(ATTR_TARGET_URI);
    }

    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        return (HttpHost) servletRequest.getAttribute(ATTR_TARGET_HOST);
    }

    protected String getConfigParam(String key) {
        return env.getProperty(key);
    }

    protected RequestConfig buildRequestConfig() {
        RequestConfig.Builder builder = RequestConfig.custom()
                .setRedirectsEnabled(doHandleRedirects)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout);
        return builder.build();
    }

    protected void initTarget() throws ServletException {
        targetUri = getConfigParam(P_TARGET_URI);
        if (targetUri == null)
            throw new ServletException(P_TARGET_URI + " is required.");
        //test it's valid
        try {
            targetUriObj = new URI(targetUri);
        } catch (Exception e) {
            throw new ServletException("Trying to process targetUri init parameter: " + e, e);
        }
        targetHost = URIUtils.extractHost(targetUriObj);
    }

    protected HttpClient createHttpClient(final RequestConfig requestConfig) {
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig).build();
    }

    protected HttpClient getProxyClient() {
        return proxyClient;
    }

    @Override
    public void destroy() {
        if (proxyClient instanceof Closeable) {
            try {
                ((Closeable) proxyClient).close();
            } catch (IOException e) {
                logger.error("While destroying servlet, shutting down HttpClient: " + e, e);
            }
        } else {
            if (proxyClient != null)
                proxyClient.getConnectionManager().shutdown();
        }
    }


    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse, String uuid)
            throws ServletException, IOException {
        if (servletRequest.getAttribute(ATTR_TARGET_URI) == null) {
            servletRequest.setAttribute(ATTR_TARGET_URI, targetUri);
        }
        if (servletRequest.getAttribute(ATTR_TARGET_HOST) == null) {
            servletRequest.setAttribute(ATTR_TARGET_HOST, targetHost);
        }

        String method = servletRequest.getMethod();
        String proxyRequestUri = rewriteUrlFromRequest(servletRequest, uuid);
        HttpRequest proxyRequest;
        if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
                servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
        } else {
            proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
        }

        copyRequestHeaders(servletRequest, proxyRequest);

        setXForwardedForHeader(servletRequest, proxyRequest);

        HttpResponse proxyResponse = null;
        try {
            proxyResponse = doExecute(servletRequest, servletResponse, proxyRequest);

            int statusCode = proxyResponse.getStatusLine().getStatusCode();
            servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());
            copyResponseHeaders(proxyResponse, servletRequest, servletResponse);

            if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
                servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
            } else {
                copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);
            }

        } catch (Exception e) {
            handleRequestException(proxyRequest, e);
        } finally {
            if (proxyResponse != null)
                consumeQuietly(proxyResponse.getEntity());
        }
    }

    protected void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
        if (proxyRequest instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
            abortableHttpRequest.abort();
        }
        if (e instanceof RuntimeException)
            throw (RuntimeException) e;
        if (e instanceof ServletException)
            throw (ServletException) e;
        if (e instanceof IOException)
            throw (IOException) e;
        throw new RuntimeException(e);
    }

    protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                     HttpRequest proxyRequest) throws IOException {
        if (doLog) {
            logger.debug("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- " +
                    proxyRequest.getRequestLine().getUri());
        }
        return proxyClient.execute(getTargetHost(servletRequest), proxyRequest);
    }

    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest)
            throws IOException {
        HttpEntityEnclosingRequest eProxyRequest =
                new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);
        eProxyRequest.setEntity(
                new InputStreamEntity(servletRequest.getInputStream(), getContentLength(servletRequest)));
        return eProxyRequest;
    }

    private long getContentLength(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    protected void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    protected void consumeQuietly(HttpEntity entity) {
        try {
            EntityUtils.consume(entity);
        } catch (IOException e) {//ignore
            logger.error(e.getMessage(), e);
        }
    }

    protected static final HeaderGroup hopByHopHeaders;

    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        @SuppressWarnings("unchecked")
        Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }
    }

    protected void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest,
                                     String headerName) {
        if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
            return;
        if (hopByHopHeaders.containsHeader(headerName))
            return;

        @SuppressWarnings("unchecked")
        Enumeration<String> headers = servletRequest.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String headerValue = headers.nextElement();
            if (!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                HttpHost host = getTargetHost(servletRequest);
                headerValue = host.getHostName();
                if (host.getPort() != -1)
                    headerValue += ":" + host.getPort();
            } else if (!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    private void setXForwardedForHeader(HttpServletRequest servletRequest,
                                        HttpRequest proxyRequest) {
        if (doForwardIP) {
            String forHeaderName = "X-Forwarded-For";
            String forHeader = servletRequest.getRemoteAddr();
            String existingForHeader = servletRequest.getHeader(forHeaderName);
            if (existingForHeader != null) {
                forHeader = existingForHeader + ", " + forHeader;
            }
            proxyRequest.setHeader(forHeaderName, forHeader);

            String protoHeaderName = "X-Forwarded-Proto";
            String protoHeader = servletRequest.getScheme();
            proxyRequest.setHeader(protoHeaderName, protoHeader);
        }
    }

    protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            copyResponseHeader(servletRequest, servletResponse, header);
        }
    }


    protected void copyResponseHeader(HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse, Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE) ||
                headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
        }
    }

    protected void copyProxyCookie(HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse, String headerValue) {
        List<HttpCookie> cookies = HttpCookie.parse(headerValue);
        String path = servletRequest.getContextPath();
        path += servletRequest.getServletPath();
        if (path.isEmpty()) {
            path = "/";
        }

        for (HttpCookie cookie : cookies) {
            String proxyCookieName = doPreserveCookies ? cookie.getName() : getCookieNamePrefix(cookie.getName()) + cookie.getName();
            Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
            servletCookie.setComment(cookie.getComment());
            servletCookie.setMaxAge((int) cookie.getMaxAge());
            servletCookie.setPath(path); //set to the path of the proxy servlet
            servletCookie.setSecure(cookie.getSecure());
            servletCookie.setVersion(cookie.getVersion());
            servletResponse.addCookie(servletCookie);
        }
    }

    protected String getRealCookie(String cookieValue) {
        StringBuilder escapedCookie = new StringBuilder();
        String cookies[] = cookieValue.split("[;,]");
        for (String cookie : cookies) {
            String cookieSplit[] = cookie.split("=");
            if (cookieSplit.length == 2) {
                String cookieName = cookieSplit[0].trim();
                if (cookieName.startsWith(getCookieNamePrefix(cookieName))) {
                    cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
                    if (escapedCookie.length() > 0) {
                        escapedCookie.append("; ");
                    }
                    escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
                }
            }
        }
        return escapedCookie.toString();
    }


    protected String getCookieNamePrefix(String name) {
        return "!Proxy!" + getClass().getName();
    }


    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        if (entity != null) {
            OutputStream servletOutputStream = servletResponse.getOutputStream();
            entity.writeTo(servletOutputStream);
        }
    }


    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest, String uuid) {
        StringBuilder uri = new StringBuilder(500);
        uri.append(getTargetUri(servletRequest));
        String pathInfo = servletRequest.getPathInfo();
        if (pathInfo != null) {
            uri.append(encodeUriQuery(pathInfo, true));
        }
        String queryString = servletRequest.getQueryString();
        String fragment = null;
        if (queryString != null) {
            int fragIdx = queryString.indexOf('#');
            if (fragIdx >= 0) {
                fragment = queryString.substring(fragIdx + 1);
                queryString = queryString.substring(0, fragIdx);
            }
        }

        queryString = rewriteQueryStringFromRequest(servletRequest, queryString);
        if (queryString != null && queryString.length() > 0) {
            uri.append('?');
            uri.append(encodeUriQuery(queryString, false));
        }

        if (doSendUrlFragment && fragment != null) {
            uri.append('#');
            uri.append(encodeUriQuery(fragment, false));
        }
        return uri.toString();
    }

    protected String rewriteQueryStringFromRequest(HttpServletRequest servletRequest, String queryString) {
        return queryString;
    }


    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
        final String targetUri = getTargetUri(servletRequest);
        if (theUrl.startsWith(targetUri)) {
            StringBuffer curUrl = servletRequest.getRequestURL();
            int pos;
            if ((pos = curUrl.indexOf("://")) >= 0) {
                if ((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
                    curUrl.setLength(pos);
                }
            }
            curUrl.append(servletRequest.getContextPath());
            curUrl.append(servletRequest.getServletPath());
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            theUrl = curUrl.toString();
        }
        return theUrl;
    }

    public String getTargetUri() {
        return targetUri;
    }

    protected static CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
        StringBuilder outBuf = null;
        Formatter formatter = null;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean escape = true;
            if (c < 128) {
                if (asciiQueryChars.get((int) c) && !(encodePercent && c == '%')) {
                    escape = false;
                }
            } else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {//not-ascii
                escape = false;
            }
            if (!escape) {
                if (outBuf != null)
                    outBuf.append(c);
            } else {
                if (outBuf == null) {
                    outBuf = new StringBuilder(in.length() + 5 * 3);
                    outBuf.append(in, 0, i);
                    formatter = new Formatter(outBuf);
                }
                formatter.format("%%%02X", (int) c);//TODO
            }
        }
        return outBuf != null ? outBuf : in;
    }

    protected static final BitSet asciiQueryChars;

    static {
        char[] c_unreserved = "_-!.~'()*".toCharArray();
        char[] c_punct = ",;:$&+=".toCharArray();
        char[] c_reserved = "?/[]@".toCharArray();
        asciiQueryChars = new BitSet(128);
        for (char c = 'a'; c <= 'z'; c++) asciiQueryChars.set((int) c);
        for (char c = 'A'; c <= 'Z'; c++) asciiQueryChars.set((int) c);
        for (char c = '0'; c <= '9'; c++) asciiQueryChars.set((int) c);
        for (char c : c_unreserved) asciiQueryChars.set((int) c);
        for (char c : c_punct) asciiQueryChars.set((int) c);
        for (char c : c_reserved) asciiQueryChars.set((int) c);
        asciiQueryChars.set((int) '%');
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String doLogStr = getConfigParam(P_LOG);
        if (doLogStr != null) {
            this.doLog = Boolean.parseBoolean(doLogStr);
        }

        String doForwardIPString = getConfigParam(P_FORWARDEDFOR);
        if (doForwardIPString != null) {
            this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
        }

        String preserveHostString = getConfigParam(P_PRESERVEHOST);
        if (preserveHostString != null) {
            this.doPreserveHost = Boolean.parseBoolean(preserveHostString);
        }

        String preserveCookiesString = getConfigParam(P_PRESERVECOOKIES);
        if (preserveCookiesString != null) {
            this.doPreserveCookies = Boolean.parseBoolean(preserveCookiesString);
        }

        String handleRedirectsString = getConfigParam(P_HANDLEREDIRECTS);
        if (handleRedirectsString != null) {
            this.doHandleRedirects = Boolean.parseBoolean(handleRedirectsString);
        }

        String connectTimeoutString = getConfigParam(P_CONNECTTIMEOUT);
        if (connectTimeoutString != null) {
            this.connectTimeout = Integer.parseInt(connectTimeoutString);
        }

        String readTimeoutString = getConfigParam(P_READTIMEOUT);
        if (readTimeoutString != null) {
            this.readTimeout = Integer.parseInt(readTimeoutString);
        }
        initTarget();
        proxyClient = createHttpClient(buildRequestConfig());
    }
}
