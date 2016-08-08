/*******************************************************************************
 * (C) Copyright 2015 Somonar B.V.
 * Licensed under the Apache License under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS"BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package com.somonar.orrtiz.ofruugo;

import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.*;
import org.apache.http.client.methods.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.auth.*;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class FruugoServices {
    private static final String module = FruugoServices.class.getName();
    public static String systemResourceId="ofruugo";
    public static final String resource = "ofruugoUiLabels";
    public static final String resourceErr = "ofruugoErrorUiLabels";
    public static final String logPrefix = "in FruugoServices.java: ";
    public static Map<String, Object> getOrders(DispatchContext dctx, Map<String, Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        String errMsg = null;
        String hostProtocol = EntityUtilProperties.getPropertyValue(systemResourceId, "HostProtocol.test", delegator);
        String hostName = EntityUtilProperties.getPropertyValue(systemResourceId, "HostName.test", delegator);
        String hostPort = EntityUtilProperties.getPropertyValue(systemResourceId, "HostPort.test", delegator);
        String apiRequest = EntityUtilProperties.getPropertyValue(systemResourceId, "apiRequest.test.orders", delegator);
        String merchantId = EntityUtilProperties.getPropertyValue(systemResourceId, "merchantId", delegator);
        String merchantKey = EntityUtilProperties.getPropertyValue(systemResourceId, "merchantKey", delegator);
        String lastRetrieveDateTime = EntityUtilProperties.getPropertyValue(systemResourceId, "lastRetrieveDateTime", delegator);
        String requestUrl = hostProtocol + "://" + hostName  + apiRequest +"?from="+ lastRetrieveDateTime;
        String responseBody = null;
        Document orderDoc = null;
        //executing the rest action
        try {
            HttpHost targetHost = new HttpHost(hostName, Integer.parseInt(hostPort), hostProtocol);
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                new UsernamePasswordCredentials(merchantId, merchantKey)
            );
            // Create AuthCache instance
            AuthCache authCache = new BasicAuthCache();
            
            // Generate BASIC scheme object and add it to the local auth cache
            BasicScheme basicAuth = new BasicScheme();
            authCache.put(targetHost, basicAuth);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            
            // Add AuthCache to the execution context
            HttpClientContext httpContext = HttpClientContext.create();
            httpContext.setCredentialsProvider(credsProvider);
            httpContext.setAuthCache(authCache);
            
            HttpGet httpget = new HttpGet(requestUrl);
            HttpResponse response = httpClient.execute(httpget, httpContext);
            HttpEntity entity = response.getEntity();
            Integer responseStatus = response.getStatusLine().getStatusCode();
            if (responseStatus == 200) {
                InputStream rstream = entity.getContent();
                responseBody = rstream.toString();
                if (responseBody != null) {
                    processOrders(dctx, context, responseBody);
                }
            }
            if (responseStatus == 401) {
                Debug.logInfo(logPrefix + ": responseStatus = " + responseStatus + " - No credentials passed" ,module);
            }
            if (responseStatus == 404) {
                Debug.logInfo(logPrefix + ": responseStatus = " + responseStatus + " - No Data Found" ,module);
            }
            if (responseStatus == 405) {
                Debug.logInfo(logPrefix + ": responseStatus = " + responseStatus + " - not a correct connection" ,module);
            }
            if (responseStatus == 503) {
                Debug.logInfo(logPrefix + ": responseStatus = " + responseStatus + " - No Data Found" ,module);
            }
        } catch (IOException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        Map<String, Object> result = ServiceUtil.returnSuccess("Fruugo orders processed");
        return result;
    }
    
    /**
     * processing the responseBody striing.
     */
    public static Map<String, Object> processOrders (DispatchContext dctx, Map<String, Object> context,  String responseBody) {
        Locale locale = (Locale) context.get("locale");
        Debug.logInfo("in FruugoServices.java         : Process Orders", module);
        Debug.logInfo("in FruugoServices.java         : --------------------", module);
        Debug.logInfo("in FruugoServices.java         : Getting Orders elements", module);
        Document doc = null;
        try {
            doc = UtilXml.readXmlDocument(responseBody);
        } 
        catch (SAXException e) {
            Debug.logError(e, module);
        } catch (ParserConfigurationException e) {
            Debug.logError(e, module);
        } catch (IOException e) {
            Debug.logError(e, module);
        }
        Element ordersElement = doc.getDocumentElement();
        List elementList = UtilXml.childElementList(ordersElement);
        if (UtilValidate.isNotEmpty(elementList)) {
            Debug.logInfo(
                    "in FruugoServices.java         : size of nodeElement = " + elementList.size(),
                    module);
            for (Iterator<? extends Element> i2 = elementList.iterator(); i2.hasNext();) {
                Element nodeElement = i2.next();
                Debug.logInfo(
                        "in FruugoServices.java         : nodeElement = " + nodeElement.getNodeName(),
                        module);
                processElement( dctx, context,  nodeElement, doc);
            }
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }
    
    /**
     * processing the order nodeElement.
     */
    public static Map<String, Object> processElement (DispatchContext dctx,Map<String, Object> context, Element order, Document doc) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        String fromDate = UtilDateTime.nowTimestamp().toString();
        String statusValue = UtilXml.childElementValue(order, "o:orderstatus", null);
        if (statusValue !="null") {
            Debug.logInfo(
                    "in FruugoServices.java         : status = " + statusValue,
                    module);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }
    
    /**
     * processing the order - address nodeElement.
     */
    public static Map<String, Object> processAddressElement (DispatchContext dctx,Map<String, Object> context, Element address, Document doc) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        String fromDate = UtilDateTime.nowTimestamp().toString();
        
        Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }
    
    /**
     * processing the order - orderLines nodeElement.
     */
    public static Map<String, Object> processOrderLinesElement (DispatchContext dctx,Map<String, Object> context, Element orderLines, Document doc) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        String fromDate = UtilDateTime.nowTimestamp().toString();
        
        Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }
    
    /**
     * processing the order - orderLine nodeElement.
     */
    public static Map<String, Object> processOrderLineElement (DispatchContext dctx,Map<String, Object> context, Element orderLine, Document doc) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        String fromDate = UtilDateTime.nowTimestamp().toString();
        
        Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }
}
