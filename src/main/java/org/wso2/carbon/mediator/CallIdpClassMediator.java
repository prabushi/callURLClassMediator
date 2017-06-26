package org.wso2.carbon.mediator;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import static org.apache.http.protocol.HTTP.USER_AGENT;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CallIdpClassMediator extends AbstractMediator {

    @Override
    public boolean mediate(MessageContext messageContext) {

        //retrieve IdP url
        String idpUrl = (String) messageContext.getProperty("IDP-URL");

        //extract token from the header
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        Map<String, String> headers = (Map) axis2MessageContext.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String token = headers.get("token");

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
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                //if the token is not valid, esb flow will be directed to the default fault sequence
                handleException("Invalid token" + token, messageContext);
            }

        } catch (IOException e) {
            handleException("Exception occurred while validating with the IdP.", e, messageContext);
        }

        return true;
    }

}
