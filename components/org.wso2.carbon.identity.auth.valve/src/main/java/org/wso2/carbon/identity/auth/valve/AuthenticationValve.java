/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.auth.valve;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.auth.service.*;
import org.wso2.carbon.identity.auth.service.exception.AuthClientException;
import org.wso2.carbon.identity.auth.service.exception.AuthRuntimeException;
import org.wso2.carbon.identity.auth.service.exception.AuthServerException;
import org.wso2.carbon.identity.auth.service.exception.AuthenticationFailException;
import org.wso2.carbon.identity.auth.service.factory.AuthenticationRequestBuilderFactory;
import org.wso2.carbon.identity.auth.service.module.ResourceConfig;
import org.wso2.carbon.identity.auth.service.module.ResourceConfigKey;
import org.wso2.carbon.identity.auth.valve.internal.AuthenticationValveServiceHolder;
import org.wso2.carbon.identity.auth.valve.util.AuthHandlerManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;


/**
 * AuthenticationValve can be used to intercept any request.
 */
public class AuthenticationValve extends ValveBase {

    private static final String AUTH_CONTEXT = "auth-context";
    private static final String AUTH_HEADER_NAME = "WWW-Authenticate";

    private static final Log log = LogFactory.getLog(AuthenticationValve.class);

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        AuthenticationManager authenticationManager = AuthHandlerManager.getInstance().getAuthenticationManager();
        ResourceConfig securedResource = authenticationManager.getSecuredResource(new ResourceConfigKey(request
                .getRequestURI(), request.getMethod()));
        if (securedResource == null || !securedResource.isSecured()) {
            getNext().invoke(request, response);
            return;
        }

        if ( log.isDebugEnabled() ) {
            log.debug("AuthenticationValve hit on secured resource : " + request.getRequestURI());
        }
        AuthenticationContext authenticationContext = null;
        AuthenticationResult authenticationResult = null;
        try {

            AuthenticationRequest.AuthenticationRequestBuilder authenticationRequestBuilder = AuthHandlerManager
                    .getInstance().getRequestBuilder(request, response).createRequestBuilder(request, response);
            authenticationContext = new AuthenticationContext(authenticationRequestBuilder.build());
            authenticationContext.setResourceConfig(securedResource);
            //Do authentication.
            authenticationResult = authenticationManager.authenticate(authenticationContext);

            AuthenticationStatus authenticationStatus = authenticationResult.getAuthenticationStatus();
            if ( authenticationStatus.equals(AuthenticationStatus.SUCCESS) ) {
                //Set the User object as an attribute for further references.
                request.setAttribute(AUTH_CONTEXT, authenticationContext);
                getNext().invoke(request, response);
            } else {
                handleErrorResponse(authenticationContext, response, HttpServletResponse.SC_UNAUTHORIZED);
            }
        } catch ( AuthClientException e ) {
            handleErrorResponse(authenticationContext, response, HttpServletResponse.SC_BAD_REQUEST);
        } catch ( AuthServerException e ) {
            handleErrorResponse(authenticationContext, response, HttpServletResponse.SC_BAD_REQUEST);
        } catch ( AuthenticationFailException e ) {
            handleErrorResponse(authenticationContext, response, HttpServletResponse.SC_UNAUTHORIZED);
        } catch (AuthRuntimeException e) {
            handleErrorResponse(authenticationContext, response, HttpServletResponse.SC_UNAUTHORIZED);
        }


    }

    private void handleErrorResponse(AuthenticationContext authenticationContext, Response response, int error) throws
            IOException {
        StringBuilder value = new StringBuilder(16);
        value.append("realm user=\"");
        if ( authenticationContext.getUser() != null ) {
            value.append(authenticationContext.getUser().getUserName());
        }
        value.append('\"');
        response.setHeader(AUTH_HEADER_NAME, value.toString());
        response.sendError(error);
    }
}
