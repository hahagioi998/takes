/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.takes.facets.auth.social;

import com.jcabi.http.request.JdkRequest;
import com.jcabi.http.response.JsonResponse;
import com.jcabi.http.response.RestResponse;
import com.jcabi.http.response.XmlResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.json.JsonObject;
import lombok.EqualsAndHashCode;
import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.facets.auth.Identity;
import org.takes.facets.auth.Pass;
import org.takes.misc.Href;
import org.takes.misc.Opt;
import org.takes.rq.RqHref;

/**
 * Github OAuth landing/callback page.
 *
 * <p>The class is immutable and thread-safe.
 *
 * @since 0.1
 * @checkstyle MultipleStringLiteralsCheck (500 lines)
 */
@EqualsAndHashCode(of = { "app", "key" })
public final class PsGithub implements Pass {

    /**
     * Access token.
     */
    private static final String ACCESS_TOKEN = "access_token";

    /**
     * Code.
     */
    private static final String CODE = "code";

    /**
     * Login.
     */
    private static final String LOGIN = "login";

    /**
     * App name.
     */
    private final String app;

    /**
     * Key.
     */
    private final String key;

    /**
     * GitHub OAuth url.
     */
    private final String github;

    /**
     * GitHub API url.
     */
    private final String api;

    /**
     * Ctor.
     * @param gapp Github app
     * @param gkey Github key
     */
    public PsGithub(final String gapp, final String gkey) {
        this(gapp, gkey, "https://github.com", "https://api.github.com");
    }

    /**
     * Ctor.
     * @param gapp Github app
     * @param gkey Github key
     * @param gurl Github OAuth server
     * @param aurl Github API server
     * @checkstyle ParameterNumberCheck (2 lines)
     */
    PsGithub(final String gapp, final String gkey,
        final String gurl, final String aurl) {
        this.app = gapp;
        this.key = gkey;
        this.github = gurl;
        this.api = aurl;
    }

    @Override
    public Opt<Identity> enter(final Request request)
        throws IOException {
        final Href href = new RqHref.Base(request).href();
        final Iterator<String> code = href.param(PsGithub.CODE).iterator();
        if (!code.hasNext()) {
            throw new HttpException(
                HttpURLConnection.HTTP_BAD_REQUEST,
                "code is not provided by Github"
            );
        }
        return new Opt.Single<>(
            this.fetch(this.token(href.toString(), code.next()))
        );
    }

    @Override
    public Response exit(final Response response,
        final Identity identity) {
        return response;
    }

    /**
     * Get user name from Github, with the token provided.
     * @param token Github access token
     * @return The user found in Github
     * @throws IOException If fails
     */
    private Identity fetch(final String token) throws IOException {
        // @checkstyle MethodBodyCommentsCheck (2 lines)
        // @checkstyle LineLengthCheck (1 line)
        // @see https://developer.github.com/changes/2020-02-10-deprecating-auth-through-query-param/
        final String uri = new Href(this.api).path("user").toString();
        return PsGithub.parse(
            new JdkRequest(uri)
                .header("accept", "application/json")
                .header("Authorization", String.format("token %s", token))
                .fetch().as(RestResponse.class)
                .assertStatus(HttpURLConnection.HTTP_OK)
                .as(JsonResponse.class).json().readObject()
        );
    }

    /**
     * Retrieve Github access token.
     * @param home Home of this page
     * @param code Github "authorization code"
     * @return The token
     * @throws IOException If failed
     */
    private String token(final String home, final String code)
        throws IOException {
        final String uri = new Href(this.github)
            .path(PsGithub.LOGIN).path("oauth").path(PsGithub.ACCESS_TOKEN)
            .toString();
        return new JdkRequest(uri)
            .method("POST")
            .header("Accept", "application/xml")
            .body()
            .formParam("client_id", this.app)
            .formParam("redirect_uri", home)
            .formParam("client_secret", this.key)
            .formParam(PsGithub.CODE, code)
            .back()
            .fetch().as(RestResponse.class)
            .assertStatus(HttpURLConnection.HTTP_OK)
            .as(XmlResponse.class)
            .assertXPath("/OAuth/access_token")
            .xml()
            .xpath("/OAuth/access_token/text()")
            .get(0);
    }

    /**
     * Make identity from JSON object.
     * @param json JSON received from Github
     * @return Identity found
     */
    private static Identity parse(final JsonObject json) {
        final Map<String, String> props = new HashMap<>(json.size());
        // @checkstyle MultipleStringLiteralsCheck (1 line)
        props.put(PsGithub.LOGIN, json.getString(PsGithub.LOGIN, "unknown"));
        props.put("avatar", json.getString("avatar_url", "#"));
        return new Identity.Simple(
            String.format("urn:github:%d", json.getInt("id")), props
        );
    }
}
