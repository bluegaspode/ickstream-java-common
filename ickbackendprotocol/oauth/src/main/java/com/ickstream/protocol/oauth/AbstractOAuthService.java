/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.protocol.oauth;

import com.ickstream.protocol.common.exception.UnauthorizedException;
import com.ickstream.protocol.service.corebackend.*;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.Api;
import org.scribe.model.OAuthConstants;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuth10aServiceImpl;
import org.scribe.oauth.OAuthService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class AbstractOAuthService extends HttpServlet {

    protected abstract CoreBackendService getCoreBackendService();

    protected boolean isAddService(HttpServletRequest req) {
        return req.getRequestURI().endsWith("/addservice");
    }

    protected boolean isAddIdentity(HttpServletRequest req) {
        return req.getRequestURI().endsWith("/addidentity");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getParameter(OAuthConstants.REDIRECT_URI) != null) {
            req.getSession().setAttribute(OAuthConstants.REDIRECT_URI, req.getParameter(OAuthConstants.REDIRECT_URI));
        }
        if (req.getParameter(OAuthConstants.CLIENT_ID) != null) {
            req.getSession().setAttribute(OAuthConstants.CLIENT_ID, req.getParameter(OAuthConstants.CLIENT_ID));
        }

        if (isAddService(req) && req.getParameter("user_code") != null) {
            req.getSession().setAttribute("service_user_code", req.getParameter("user_code"));
        } else if (isAddIdentity(req) && req.getParameter("user_code") != null) {
            req.getSession().setAttribute("identity_user_code", req.getParameter("user_code"));
        }


        if (req.getParameter(OAuthConstants.TOKEN) == null && req.getParameter(OAuthConstants.CODE) == null && req.getParameter("error") == null) {
            OAuthService service;
            if (getScope() != null) {
                service = new ServiceBuilder()
                        .provider(getApiImplementation())
                        .apiKey(getApiKey())
                        .apiSecret(getApiSecret())
                        .scope(getScope())
                        .callback(getRedirectURL())
                        .build();
            } else {
                service = new ServiceBuilder()
                        .provider(getApiImplementation())
                        .apiKey(getApiKey())
                        .apiSecret(getApiSecret())
                        .callback(getRedirectURL())
                        .build();
            }
            Token requestToken = null;
            if (service instanceof OAuth10aServiceImpl) {
                requestToken = service.getRequestToken();
                req.getSession().setAttribute("secret", requestToken.getSecret());
            }
            String userAuthURL = service.getAuthorizationUrl(requestToken);
            resp.sendRedirect(userAuthURL);
        } else if (req.getParameter(OAuthConstants.CODE) != null || req.getParameter(OAuthConstants.TOKEN) != null) {
            OAuthService oAuthService;
            if (getScope() != null) {
                oAuthService = new ServiceBuilder()
                        .provider(getApiImplementation())
                        .apiKey(getApiKey())
                        .apiSecret(getApiSecret())
                        .scope(getScope())
                        .callback(getRedirectURL())
                        .build();
            } else {
                oAuthService = new ServiceBuilder()
                        .provider(getApiImplementation())
                        .apiKey(getApiKey())
                        .apiSecret(getApiSecret())
                        .callback(getRedirectURL())
                        .build();
            }
            String tokenCode = req.getParameter(OAuthConstants.CODE);
            if (tokenCode == null) {
                tokenCode = req.getParameter(OAuthConstants.TOKEN);
            }
            String verifierCode = req.getParameter(OAuthConstants.CODE);
            if (verifierCode == null) {
                verifierCode = req.getParameter(OAuthConstants.VERIFIER);
            }
            String secret = getApiSecret();
            if (oAuthService instanceof OAuth10aServiceImpl) {
                secret = req.getSession().getAttribute("secret").toString();
            }
            Token token = new Token(tokenCode, secret);
            Verifier verifier = new Verifier(verifierCode);
            Token accessToken = oAuthService.getAccessToken(token, verifier);
            if (req.getSession().getAttribute(OAuthConstants.REDIRECT_URI) != null && req.getSession().getAttribute(OAuthConstants.CLIENT_ID) != null) {
                ApplicationResponse application = getCoreBackendService().getApplication();

                if (accessToken != null && application != null && application.getActive()) {
                    ServiceResponse service = getCoreBackendService().getService();
                    String identity = getUserIdentity(oAuthService, service, accessToken);
                    String type = getIdentityType();
                    boolean linkService = false;
                    String identityUserCode = null;
                    if (req.getSession().getAttribute("service_user_code") != null) {
                        linkService = true;
                        identityUserCode = req.getSession().getAttribute("service_user_code").toString();
                    } else if (req.getSession().getAttribute("identity_user_code") != null) {
                        identityUserCode = req.getSession().getAttribute("identity_user_code").toString();
                    }
                    req.getSession().removeAttribute("service_user_code");
                    req.getSession().removeAttribute("identity_user_code");
                    if (identityUserCode != null) {
                        UserResponse user = null;
                        try {
                            user = getCoreBackendService().addIdentityToUser(identityUserCode, type, identity);
                            if (user != null && linkService) {
                                linkUserService(oAuthService, service, accessToken, user.getId());
                            }
                            if (user == null && !linkService) {
                                resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?error=identity_already_used");
                                req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                                req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                            } else {
                                resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?status=success");
                                req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                                req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                            }
                        } catch (Exception e) {
                            if (e.getCause() == null || !(e.getCause() instanceof UnauthorizedException)) {
                                e.printStackTrace();
                            }
                            resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?error=access_denied");
                            req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                            req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                        }
                    } else if (type != null && type.equals("email") && identity != null) {
                        String code = getCoreBackendService().createAuthorizationCodeForIdentity(type, identity, accessToken.getToken(), accessToken.getSecret(), req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString());

                        UserResponse user = getCoreBackendService().getUserByIdentity(type, identity);

                        if (user == null) {
                            String styleSheetUrl = System.getProperty("ickstream-core-stylesheet-url", "https://api.ickstream.com/ickstream-cloud-core");
                            resp.setContentType("text/html");
                            resp.setCharacterEncoding("utf-8");
                            resp.getWriter().append("<HTML>\n<HEAD>\n")
                                    .append(OAuthServiceHelper.getDeviceOptimizedStylesheetHeader())
                                    .append("</HEAD>\n<BODY>\n");
                            resp.getWriter().append("<div id=\"question\">No previous account exist, do you want to create a new one ?</div>");
                            resp.getWriter().append("<FORM action=\"").append(req.getRequestURI()).append("\" method=\"post\">");
                            resp.getWriter().append("<INPUT hidden=\"hidden\" name=\"" + OAuthConstants.CODE + "\" value=\"").append(code).append("\">");
                            resp.getWriter().append("<INPUT type=\"submit\" name=\"cancel\" value=\"Cancel\">");
                            resp.getWriter().append("<INPUT type=\"submit\" name=\"create\" value=\"Create new account\">");
                            resp.getWriter().append("</FORM>");
                            resp.getWriter().append("</BODY></HTML>");
                        } else {
                            resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?" + OAuthConstants.CODE + "=" + code);
                            req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                            req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                        }
                    } else {
                        resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?error=unsupported_response_type");
                        req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                        req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                    }
                } else if (application == null) {
                    resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?error=unauthorized_client");
                    req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                    req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                } else if (accessToken == null) {
                    resp.sendRedirect(req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString() + "?error=access_denied");
                    req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                    req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
                }
            } else {
                writeOutputWithoutRedirectURI(req, resp, oAuthService, accessToken);
                req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
                req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
            }
        } else {
            resp.setContentType("text/html");
            resp.getWriter().append("ERROR");
            req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
            req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getSession().getAttribute(OAuthConstants.REDIRECT_URI) != null) {
            String redirect_uri = req.getSession().getAttribute(OAuthConstants.REDIRECT_URI).toString();
            req.getSession().removeAttribute(OAuthConstants.REDIRECT_URI);
            req.getSession().removeAttribute(OAuthConstants.CLIENT_ID);
            String code = req.getParameter(OAuthConstants.CODE);
            Boolean create = req.getParameter("create") != null;
            if (code != null && create != null && create) {
                resp.sendRedirect(redirect_uri + "?" + OAuthConstants.CODE + "=" + code);
            } else {
                resp.sendRedirect(redirect_uri + "?error=access_denied");
            }
        }
    }

    protected void writeOutputWithoutRedirectURI(HttpServletRequest req, HttpServletResponse resp, OAuthService oAuthService, Token accessToken) throws IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("utf-8");
        resp.getWriter().append("<HTML>");
        resp.getWriter().append("<BODY>Welcome to ickStream\nThis is an internal page which you are not supposed to enter\n");
        resp.getWriter().append("</BODY>");
        resp.getWriter().append("</HTML>");
    }

    protected String getRedirectURL() {
        AuthenticationProviderResponse provider = getCoreBackendService().getAuthenticationProvider();
        if (provider != null) {
            return provider.getRedirectUrl();
        }
        return null;
    }

    protected String getApiSecret() {
        AuthenticationProviderResponse provider = getCoreBackendService().getAuthenticationProvider();
        if (provider != null) {
            return provider.getApiSecret();
        }
        return null;
    }

    protected String getApiKey() {
        AuthenticationProviderResponse provider = getCoreBackendService().getAuthenticationProvider();
        if (provider != null) {
            return provider.getApiKey();
        }
        return null;
    }

    protected abstract String getScope();

    protected abstract Class<? extends Api> getApiImplementation();

    protected abstract String getIdentityType();

    protected abstract String getUserIdentity(OAuthService oAuthService, ServiceResponse service, Token accessToken);

    protected String getServiceIdentity(OAuthService oAuthService, ServiceResponse service, Token accessToken) {
        return getUserIdentity(oAuthService, service, accessToken);
    }

    protected void linkUserService(OAuthService oAuthService, ServiceResponse service, Token accessToken, String userId) {
        String identity = getServiceIdentity(oAuthService, service, accessToken);
        UserServiceResponse userService = getCoreBackendService().getUserServiceByUser(userId);
        if (userService == null) {
            userService = new UserServiceResponse();
            userService.setUserId(userId);
        }
        UserServiceRequest updatedUserService = new UserServiceRequest(userService);
        updatedUserService.setIdentity(identity);
        setUserService(updatedUserService, accessToken);
    }

    protected void setUserService(UserServiceRequest userService, Token accessToken) {
        userService.setAccessToken(accessToken.getToken());
        userService.setAccessTokenSecret(accessToken.getSecret());

        getCoreBackendService().setUserService(userService);
    }


}
