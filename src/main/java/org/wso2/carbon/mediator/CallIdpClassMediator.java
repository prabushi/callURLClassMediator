package org.wso2.carbon.mediator;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import javax.cache.Cache;
import javax.cache.CacheBuilder;
import javax.cache.CacheConfiguration.Duration;
import javax.cache.CacheConfiguration.ExpiryType;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CallIdpClassMediator extends AbstractMediator implements ManagedLifecycle {
    private static final String USER_AGENT = "User-Agent";
    private static final String CACHE_BUILDER_NAME = "cacheBuilder";
    private static final String CACHE_MANAGER_NAME = "cacheManager";

    private static Cache<String, Integer> cache = null;
    private static CacheManager cacheManager = null;
    private static CacheBuilder<String, Integer> cacheBuilder = null;

    private int statusCode;

    @Override
    public boolean mediate(MessageContext messageContext) {

        //extract token from the header
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        Map<String, String> headers = (Map) axis2MessageContext.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String token = headers.get("token");

        //check the cached responses
        int code = getCachedResponse(token);

        if (code != -1) {
            //if the corresponding response is available in the cache for request token
            statusCode = code;

        } else {
            //if the token is not already there in the cache, call IdP and validate the token

            //retrieve IdP url
            String idpUrl = (String) messageContext.getProperty("IDP-URL");

            //validate the token with the IdP
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost request = new HttpPost(idpUrl);
            request.addHeader("User-Agent", USER_AGENT);

            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair("Token", token));

            try {
                request.setEntity(new UrlEncodedFormEntity(urlParameters));

                //check validation status
                HttpResponse response = client.execute(request);
                statusCode = response.getStatusLine().getStatusCode();

                //add the token and relevant response code to the cache
                cache.put(token, statusCode);

            } catch (IOException e) {
                handleException("Exception occurred while validating with the IdP.", e, messageContext);
            }
        }
        if (statusCode != 200) {
            //if the token is not valid, esb flow will be directed to the fault sequence
            handleException("Invalid token" + token, messageContext);
        }
        return true;
    }

    /**
     * Returns the cached responses if the key is already there, if not returns -1
     *
     * @param key cache key
     * @return response status code or -1
     */
    private int getCachedResponse(String key) {
        try {
            return cache.get(key);

        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Initialize the cache
     *
     * @param synapseEnvironment
     */
    @Override
    public void init(SynapseEnvironment synapseEnvironment) {
        if (cacheManager == null) {
            cacheManager = Caching.getCacheManager(CACHE_MANAGER_NAME);
        }
        if (cacheBuilder == null) {
            cacheBuilder = cacheManager.createCacheBuilder(CACHE_BUILDER_NAME);
        }
        //cache clearing timeout is 20 seconds
        cache = cacheBuilder.setExpiry(ExpiryType.MODIFIED, new Duration(TimeUnit.SECONDS, 20)).build();

    }

    @Override
    public void destroy() {
        cache.removeAll();
        cache = null;
        cacheManager.removeCache("cacheBuilder");
        cacheBuilder = null;
        cacheManager = null;
    }
}
