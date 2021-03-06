package com.b_lam.resplash.data.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.b_lam.resplash.Resplash;
import com.b_lam.resplash.data.model.AccessToken;
import com.b_lam.resplash.data.model.Me;
import com.b_lam.resplash.data.model.User;
import com.b_lam.resplash.data.service.UserService;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IntDef;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Auth manager.
 *
 * A manager class to manage the authorization information.
 *
 * A process of authorize is as follow:
 * 0. Get access token.
 * 1. Request {@link Me} by a HTTP request.
 * 2. Request {@link User} by a HTTP request.
 *
 * */

public class AuthManager
        implements UserService.OnRequestMeProfileListener, UserService.OnRequestUserProfileListener {

    private static AuthManager instance;

    public static AuthManager getInstance() {
        if (instance == null) {
            synchronized (AuthManager.class) {
                if (instance == null) {
                    instance = new AuthManager();
                }
            }
        }
        return instance;
    }

    private List<OnAuthDataChangedListener> listenerList;

    private Me me;
    private User user;
    private UserService service;

    private String access_token;
    private String username;
    private String first_name;
    private String last_name;
    private String email;
    private String avatar_path;
    private boolean authorized;

    private UserCollectionsManager collectionsManager; // cache of user's collections.

    public static final String PREFERENCE_NAME = "resplash_authorize_manager";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_AVATAR_PATH = "avatar_path";

    @StateRule
    private int state;
    public static final int FREEDOM_STATE = 0;
    public static final int LOADING_ME_STATE = 1;
    public static final int LOADING_USER_STATE = 2;

    @IntDef({FREEDOM_STATE, LOADING_ME_STATE, LOADING_USER_STATE})
    private @interface StateRule {
    }

    // if version code is increased, the user needs to log in again.
    private static final String KEY_VERSION = "version";
    private static final int VERSION_CODE = 8;

    private AuthManager() {
        SharedPreferences sharedPreferences = Resplash.getInstance()
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);

        updateVersion(sharedPreferences);

        this.listenerList = new ArrayList<>();

        this.access_token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
        this.authorized = !TextUtils.isEmpty(access_token);

        if (authorized) {
            this.username = sharedPreferences.getString(KEY_USERNAME, null);
            this.first_name = sharedPreferences.getString(KEY_FIRST_NAME, null);
            this.last_name = sharedPreferences.getString(KEY_LAST_NAME, null);
            this.email = sharedPreferences.getString(KEY_EMAIL, null);
            this.avatar_path = sharedPreferences.getString(KEY_AVATAR_PATH, null);
        }
        this.collectionsManager = new UserCollectionsManager();

        this.me = null;
        this.user = null;
        this.service = UserService.getService();

        this.state = FREEDOM_STATE;
    }

    private void updateVersion(SharedPreferences sharedPreferences) {
        int versionNow = sharedPreferences.getInt(KEY_VERSION, 0);
        String token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null);

        if ((versionNow < VERSION_CODE) && !TextUtils.isEmpty(token)) {

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(KEY_VERSION, VERSION_CODE);
            editor.putString(KEY_ACCESS_TOKEN, null);
            editor.putString(KEY_USERNAME, null);
            editor.putString(KEY_FIRST_NAME, null);
            editor.putString(KEY_LAST_NAME, null);
            editor.putString(KEY_EMAIL, null);
            editor.putString(KEY_AVATAR_PATH, null);
            editor.apply();
        }
    }

    public void logout() {
        service.cancel();

        SharedPreferences.Editor editor = Resplash.getInstance()
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_ACCESS_TOKEN, null);
        editor.putString(KEY_USERNAME, null);
        editor.putString(KEY_FIRST_NAME, null);
        editor.putString(KEY_LAST_NAME, null);
        editor.putString(KEY_EMAIL, null);
        editor.putString(KEY_AVATAR_PATH, null);
        editor.apply();

        this.access_token = null;
        this.username = null;
        this.first_name = null;
        this.last_name = null;
        this.email = null;
        this.avatar_path = null;
        this.authorized = false;
        this.collectionsManager.clearCollections();

        this.me = null;
        this.user = null;
        this.state = FREEDOM_STATE;

        for (int i = 0; i < listenerList.size(); i++) {
            listenerList.get(i).onLogout();
        }
    }

    // HTTP request.

    public void requestPersonalProfile() {
        if (authorized) {
            service.cancel();
            state = LOADING_ME_STATE;
            service.requestMeProfile(this);
        }
    }

    public void cancelRequest() {
        service.cancel();
    }

    // getter.

    public Me getMe() {
        return me;
    }

    public User getUser() {
        return user;
    }

    public String getAccessToken() {
        return access_token;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return first_name;
    }

    public String getLastName() {
        return last_name;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarPath() {
        return avatar_path;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public int getState() {
        return state;
    }

    public UserCollectionsManager getCollectionsManager() {
        return collectionsManager;
    }

    // setter.

    public void updateUser(User u) {
        this.user = u;
    }

    public void writeAccessToken(AccessToken token) {
        SharedPreferences.Editor editor = Resplash.getInstance()
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_ACCESS_TOKEN, token.access_token);
        editor.apply();

        access_token = token.access_token;
        authorized = true;

        Log.d("AUTH MANAGER", access_token);

        for (int i = 0; i < listenerList.size(); i++) {
            listenerList.get(i).onWriteAccessToken();
        }
    }

    public void writeUserInfo(Me me) {
        SharedPreferences.Editor editor = Resplash.getInstance()
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_USERNAME, me.username);
        editor.putString(KEY_FIRST_NAME, me.first_name);
        editor.putString(KEY_LAST_NAME, me.last_name);
        editor.putString(KEY_EMAIL, me.email);
        editor.apply();

        this.me = me;
        username = me.username;
        first_name = me.first_name;
        last_name = me.last_name;
        email = me.email;

        for (int i = 0; i < listenerList.size(); i++) {
            listenerList.get(i).onWriteUserInfo();
        }
    }

    public void writeUserInfo(User user) {
        SharedPreferences.Editor editor = Resplash.getInstance()
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_USERNAME, user.username);
        editor.putString(KEY_FIRST_NAME, user.first_name);
        editor.putString(KEY_LAST_NAME, user.last_name);
        editor.putString(KEY_AVATAR_PATH, user.profile_image.large);
        editor.apply();

        this.user = user;
        avatar_path = user.profile_image.large;

        for (int i = 0; i < listenerList.size(); i++) {
            listenerList.get(i).onWriteAvatarPath();
        }
    }

    // interface.

    // on auth data changed swipeListener.

    public interface OnAuthDataChangedListener {
        void onWriteAccessToken();

        void onWriteUserInfo();

        void onWriteAvatarPath();

        void onLogout();
    }

    public void addOnWriteDataListener(OnAuthDataChangedListener l) {
        listenerList.add(l);
    }

    public void removeOnWriteDataListener(OnAuthDataChangedListener l) {
        listenerList.remove(l);
    }

    // on request me profile swipeListener.

    @Override
    public void onRequestMeProfileSuccess(Call<Me> call, Response<Me> response) {
        if (response.isSuccessful() && response.body() != null && isAuthorized()) {
            state = LOADING_USER_STATE;
            writeUserInfo(response.body());
            service.requestUserProfile(response.body().username, this);
        } else if (isAuthorized()) {
            service.requestMeProfile(this);
        }
    }

    @Override
    public void onRequestMeProfileFailed(Call<Me> call, Throwable t) {
        if (isAuthorized()) {
            service.requestMeProfile(this);
        }
    }

    // on request user profile swipeListener.

    @Override
    public void onRequestUserProfileSuccess(Call<User> call, Response<User> response) {
        if (response.isSuccessful() && response.body() != null && isAuthorized()) {
            state = FREEDOM_STATE;
            writeUserInfo(response.body());
        } else if (isAuthorized()) {
            service.requestUserProfile(me.username, this);
        }
    }

    @Override
    public void onRequestUserProfileFailed(Call<User> call, Throwable t) {
        if (isAuthorized()) {
            service.requestUserProfile(me.username, this);
        }
    }
}