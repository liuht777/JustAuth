package me.zhyd.oauth.request;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.config.AuthSource;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.model.AuthUserGender;
import me.zhyd.oauth.utils.GlobalAuthUtil;
import me.zhyd.oauth.utils.StringUtils;
import me.zhyd.oauth.utils.UrlBuilder;

import java.util.Map;

/**
 * qq登录
 *
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com)
 * @author yangkai.shen (https://xkcoding.com)
 * @version 1.0
 * @since 1.8
 */
public class AuthQqRequest extends AuthDefaultRequest {
    public AuthQqRequest(AuthConfig config) {
        super(config, AuthSource.QQ);
    }

    @Override
    protected AuthToken getAccessToken(AuthCallback authCallback) {
        HttpResponse response = doGetAuthorizationCode(authCallback.getCode());
        Map<String, String> accessTokenObject = GlobalAuthUtil.parseStringToMap(response.body());
        if (!accessTokenObject.containsKey("access_token")) {
            throw new AuthException("Unable to get token from qq using code [" + authCallback.getCode() + "]: " + accessTokenObject);
        }
        return AuthToken.builder()
            .accessToken(accessTokenObject.get("access_token"))
            .expireIn(Integer.valueOf(accessTokenObject.get("expires_in")))
            .refreshToken(accessTokenObject.get("refresh_token"))
            .build();
    }

    @Override
    protected AuthUser getUserInfo(AuthToken authToken) {
        String openId = this.getOpenId(authToken);
        HttpResponse response = doGetUserInfo(authToken);
        JSONObject object = JSONObject.parseObject(response.body());
        if (object.getIntValue("ret") != 0) {
            throw new AuthException(object.getString("msg"));
        }
        String avatar = object.getString("figureurl_qq_2");
        if (StringUtils.isEmpty(avatar)) {
            avatar = object.getString("figureurl_qq_1");
        }

        String location = String.format("%s-%s", object.getString("province"), object.getString("city"));
        return AuthUser.builder()
            .username(object.getString("nickname"))
            .nickname(object.getString("nickname"))
            .avatar(avatar)
            .location(location)
            .uuid(openId)
            .gender(AuthUserGender.getRealGender(object.getString("gender")))
            .token(authToken)
            .source(AuthSource.QQ)
            .build();
    }

    private String getOpenId(AuthToken authToken) {
        HttpResponse response = HttpRequest.get(UrlBuilder.fromBaseUrl("https://graph.qq.com/oauth2.0/me")
            .queryParam("access_token", authToken.getAccessToken())
            .queryParam("unionid", config.isUnionId() ? 1 : 0)
            .build()).execute();
        if (response.isOk()) {
            String body = response.body();
            String removePrefix = StrUtil.replace(body, "callback(", "");
            String removeSuffix = StrUtil.replace(removePrefix, ");", "");
            String openId = StrUtil.trim(removeSuffix);
            JSONObject object = JSONObject.parseObject(openId);
            if (object.containsKey("error")) {
                throw new AuthException(object.get("error") + ":" + object.get("error_description"));
            }
            authToken.setOpenId(object.getString("openid"));
            if (object.containsKey("unionid")) {
                authToken.setUnionId(object.getString("unionid"));
            }
            return StringUtils.isEmpty(authToken.getUnionId()) ? authToken.getOpenId() : authToken.getUnionId();
        }

        throw new AuthException("request error");
    }

    /**
     * 返回获取userInfo的url
     *
     * @param authToken
     * @return 返回获取userInfo的url
     */
    @Override
    protected String userInfoUrl(AuthToken authToken) {
        return UrlBuilder.fromBaseUrl(source.userInfo())
            .queryParam("access_token", authToken.getAccessToken())
            .queryParam("oauth_consumer_key", config.getClientId())
            .queryParam("openid", authToken.getOpenId())
            .build();
    }
}
