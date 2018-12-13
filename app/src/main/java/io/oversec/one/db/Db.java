package io.oversec.one.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.LruCache;

import io.oversec.one.BuildConfig;
import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.crypto.EncryptionMethod;
import io.oversec.one.crypto.Issues;
import io.oversec.one.crypto.encoding.AsciiArmouredGpgXCoder;
import io.oversec.one.crypto.encoding.Base64XCoder;
import io.oversec.one.crypto.encoding.ZeroWidthXCoder;
import io.oversec.one.ovl.AbstractOverlayButtonInputView;
import io.oversec.one.ui.WarningActivity;
import roboguice.util.Ln;

import java.util.*;

public final class Db {
    private static final String COL_TIMESTAMP = "tts";

    private static final String COL_PADDING_LEFT = "padl";
    private static final String COL_PADDING_TOP = "padt";
    private static final String COL_RADIUS = "rasiud";
    private static final String COL_TEXTSIZE = "txtsize";
    private static final String COL_FG_COLOR = "fgcolor";
    private static final String COL_BG_COLOR = "bgcolor";
    private static final String COL_BUTTON_COLOR = "btcolor";

    private static final String COL_NAME = "name";
    private static final String COL_ENABLED = "ena";
    private static final String COL_START_HIDDEN = "hidden";
    private static final String COL_NOTIFICATION = "notification";
    private static final String COL_SHOW_STATUS_ICON = "ssi";
    private static final String COL_SHOW_INFO_BUTTON = "bt_info";
    private static final String COL_SHOW_HIDE_BUTTON = "bt_hide";
    private static final String COL_SHOW_DECRYPT_BUTTON = "bt_decrypt";
    private static final String COL_SHOW_CONFIG_BUTTON = "bt_config";

    private static final String COL_BTENCRYPT_DELTA_X = "dec_dx";
    private static final String COL_BTENCRYPT_DELTA_Y = "dec_dy";
    private static final String COL_BTDECRYPT_DELTA_X = "enc_dx";
    private static final String COL_BTDECRYPT_DELTA_Y = "enc_dy";
    private static final String COL_INFOMODE_LANDSCAPE_X = "info_lx";
    private static final String COL_INFOMODE_LANDSCAPE_Y = "info_ly";
    private static final String COL_INFOMODE_PORTRAIT_X = "info_px";
    private static final String COL_INFOMODE_PORTRAIT_Y = "info_py";
    private static final String COL_HIDE_LANDSCAPE_X = "hide_lx";
    private static final String COL_HIDE_LANDSCAPE_Y = "hide_ly";
    private static final String COL_HIDE_PORTRAIT_X = "hide_px";
    private static final String COL_HIDE_PORTRAIT_Y = "hide_py";

    private static final String COL_CONFIG_LANDSCAPE_X = "config_lx";
    private static final String COL_CONFIG_LANDSCAPE_Y = "config_ly";
    private static final String COL_CONFIG_PORTRAIT_X = "config_px";
    private static final String COL_CONFIG_PORTRAIT_Y = "config_py";

    private static final String COL_CAMERA_LANDSCAPE_X = "camera_lx";
    private static final String COL_CAMERA_LANDSCAPE_Y = "camera_ly";
    private static final String COL_CAMERA_PORTRAIT_X = "camera_px";
    private static final String COL_CAMERA_PORTRAIT_Y = "camera_py";

    private static final String COL_COMPOSE_LANDSCAPE_X = "compose_lx";
    private static final String COL_COMPOSE_LANDSCAPE_Y = "compose_ly";
    private static final String COL_COMPOSE_PORTRAIT_X = "compose_px";
    private static final String COL_COMPOSE_PORTRAIT_Y = "compose_py";

    private static final String COL_BTENCRYPT_IMEFULLSCREEN_X = "enc_imefsx";
    private static final String COL_BTENCRYPT_IMEFULLSCREEN_Y = "enc_imefsy";
    private static final String COL_BTDECRYPT_IMEFULLSCREEN_X = "dec_imefsx";
    private static final String COL_BTDECRYPT_IMEFULLSCREEN_Y = "dec_imefsy";
    private static final String COL_BTENCRYPT_ANCHOR_H = "enc_anchor_h";
    private static final String COL_BTENCRYPT_ANCHOR_V = "enc_anchor_v";
    private static final String COL_BTDECRYPT_ANCHOR_H = "dec_anchor_h";
    private static final String COL_BTDECRYPT_ANCHOR_V = "dec_anchor_v";
    private static final String COL_HQ_SCRAPE = "hq";

    private static final String COL_SHOW_INFO_ON_TAP = "i_tap";
    private static final String COL_SHOW_INFO_ON_LONGTAP = "i_longtap";
    private static final String COL_TOGGLE_ENCRYPT_LONGTAP = "e_longtap";
    private static final String COL_SHOW_ENCRYPT_BUTTON = "bt_enc";


    private static final String COL_PADDER_PGP = "pad_pgp";
    private static final String COL_PADDER_SYM = "pad_sym";
    private static final String COL_XCODER_PGP = "xcoder_pgp";
    private static final String COL_XCODER_SYM = "xcoder_sym";
    private static final String COL_LAST_ENC_METHOD = "last_enc_method";

    private static final String COL_VOVERFLOW = "vov";
    private static final String COL_ABOVE = "above";
    private static final String COL_FORCEENCRYPTIONPARAMS = "fep";
    private static final String COL_NEWLINE = "crlf";

    private static final String COL_STORE_ENCRYPTION_PARAMS_PER_PACKAGE_ONLY = "seppp";

    private static final String COL_MAX_INNER_PADDING = "max_inner_padding";

    private static final String COL_SHOW_CAMERA_BUTTON = "bt_cam";

    private static final String COL_SHOW_COMPOSE_BUTTON = "bt_compose";

    private static final String COL_INCLUDE_NON_IMPORTANT_VIEWS = "sniv";

    private static final String COL_SHOWUSERINTERACTIONDIALOGSIMMEDIATELY = " suidi";

    private static final String COL_SPREAD_INVISIBLE_ENCODING = "sie";

    private static final String COL_DONT_SHOW_DECRYPTION_FAILED = "dsdf";

    private static final String TABLE_PACKAGES = "packages";


    private static final String PREF_BOSS_MODE = "BOSSMODE";


    private final SQLiteDatabase mDb;
    private final SharedPreferences mPrefs;
    private final Context mCtx;
    private final Set<String> mIgnorePackages = new HashSet<>();
    private final Core mCore;

    public Db(Core core) {
        mCtx = core.getCtx();
        mCore = core;
        MyDatabaseHelper dbHelper = new MyDatabaseHelper(mCtx);
        mDb = dbHelper.getWritableDatabase();
        mPrefs = mCtx.getSharedPreferences("prefs", 0);
        mIgnorePackages.add(mCtx.getPackageName());
        mIgnorePackages.add(Core.PACKAGE_SYTEMUI);
    }


    public boolean isShowDecryptOverlay(String packageName) {
        if (packageName == null) {
            return false;
        }
        if (mCtx.getPackageName().equals(packageName)) {
            return false;
        }

        boolean res = isAppEnabled(packageName);

        return res;
    }


    private void fireDecryptOverlayLayoutParamsChanged(String packagename) {

        mCore.fireDecryptOverlayLayoutParamsChanged(packagename);
    }


    public int getDecryptOverlayTextColor(String packagename) {
        int res = getIntValue(COL_FG_COLOR, packagename, ContextCompat.getColor(mCtx, R.color.textNodeDefaultFG));
        return res;
    }

    public int getDecryptOverlayBgColor(String packagename) {
        int res = getIntValue(COL_BG_COLOR, packagename, ContextCompat.getColor(mCtx, R.color.textNodeDefaultBG));
        return res;
    }

    public int getDecryptOverlayTextSize(String packagename) {
        return getIntValue(COL_TEXTSIZE, packagename, 8);
    }

    public void setDecryptOverlayTextSize(String packagename, int asize) {
        setIntValue(COL_TEXTSIZE, packagename, asize);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public void setDecryptOverlayTextColor(String packagename, int color) {
        setIntValue(COL_FG_COLOR, packagename, color);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public void setDecryptOverlayBgColor(String packagename, int color) {
        setIntValue(COL_BG_COLOR, packagename, color);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public void setButtonOverlayBgColor(String packagename, int color) {
        setIntValue(COL_BUTTON_COLOR, packagename, color);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public int getButtonOverlayBgColor(String packagename) {
        int res = getIntValue(COL_BUTTON_COLOR, packagename, ContextCompat.getColor(mCtx, R.color.buttonDefaultBG));
        return res;
    }


    public int getDecryptOverlayCornerRadius(String packagename) {
        return getIntValue(COL_RADIUS, packagename, 3);
    }

    public void setDecryptOverlayCornerRadius(String packagename, int radius) {
        setIntValue(COL_RADIUS, packagename, radius);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public int getDecryptOverlayPaddingLeft(String packagename) {
        return getIntValue(COL_PADDING_LEFT, packagename, 3);
    }

    public void setDecryptOverlayPaddingLeft(String packagename, int v) {
        setIntValue(COL_PADDING_LEFT, packagename, v);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public int getDecryptOverlayPaddingTop(String packagename) {
        return getIntValue(COL_PADDING_TOP, packagename, 3);
    }

    public void setDecryptOverlayPaddingTop(String packagename, int v) {
        setIntValue(COL_PADDING_TOP, packagename, v);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public void setButtonEncryptDeltaX(String packagename, int v) {
        setIntValue(COL_BTENCRYPT_DELTA_X, packagename, v);
    }

    public int getButtonEncryptDeltaX(String packagename, int def) {
        return getIntValue(COL_BTENCRYPT_DELTA_X, packagename, def);
    }

    public void setButtonEncryptDeltaY(String packagename, int v) {
        setIntValue(COL_BTENCRYPT_DELTA_Y, packagename, v);
    }

    public int getButtonEncryptDeltaY(String packagename, int def) {
        return getIntValue(COL_BTENCRYPT_DELTA_Y, packagename, def);
    }

    public void setButtonDecryptDeltaX(String packagename, int v) {
        setIntValue(COL_BTDECRYPT_DELTA_X, packagename, v);
    }

    public int getButtonDecryptDeltaX(String packagename, int def) {
        return getIntValue(COL_BTDECRYPT_DELTA_X, packagename, def);
    }

    public void setButtonDecryptDeltaY(String packagename, int v) {
        setIntValue(COL_BTDECRYPT_DELTA_Y, packagename, v);
    }

    public int getButtonDecryptDeltaY(String packagename, int def) {
        return getIntValue(COL_BTDECRYPT_DELTA_Y, packagename, def);
    }


    public void setButtonInfomodeLandscapeX(String packagename, int v) {
        setIntValue(COL_INFOMODE_LANDSCAPE_X, packagename, v);
    }

    public int getButtonInfomodeLandscapeX(String packagename, int def) {
        return getIntValue(COL_INFOMODE_LANDSCAPE_X, packagename, def);
    }

    public void setButtonInfomodeLandscapeY(String packagename, int v) {
        setIntValue(COL_INFOMODE_LANDSCAPE_Y, packagename, v);
    }

    public int getButtonInfomodeLandscapeY(String packagename, int def) {
        return getIntValue(COL_INFOMODE_LANDSCAPE_Y, packagename, def);
    }

    public void setButtonInfomodePortraitX(String packagename, int v) {
        setIntValue(COL_INFOMODE_PORTRAIT_X, packagename, v);
    }

    public int getButtonInfomodePortraitX(String packagename, int def) {
        return getIntValue(COL_INFOMODE_PORTRAIT_X, packagename, def);
    }

    public void setButtonInfomodePortraitY(String packagename, int v) {
        setIntValue(COL_INFOMODE_PORTRAIT_Y, packagename, v);
    }

    public int getButtonInfomodePortraitY(String packagename, int def) {
        return getIntValue(COL_INFOMODE_PORTRAIT_Y, packagename, def);
    }


    public void setButtonHideLandscapeX(String packagename, int v) {
        setIntValue(COL_HIDE_LANDSCAPE_X, packagename, v);
    }

    public int getButtonHideLandscapeX(String packagename, int def) {
        return getIntValue(COL_HIDE_LANDSCAPE_X, packagename, def);
    }

    public void setButtonHideLandscapeY(String packagename, int v) {
        setIntValue(COL_HIDE_LANDSCAPE_Y, packagename, v);
    }

    public int getButtonHideLandscapeY(String packagename, int def) {
        return getIntValue(COL_HIDE_LANDSCAPE_Y, packagename, def);
    }

    public void setButtonHidePortraitX(String packagename, int v) {
        setIntValue(COL_HIDE_PORTRAIT_X, packagename, v);
    }

    public int getButtonHidePortraitX(String packagename, int def) {
        return getIntValue(COL_HIDE_PORTRAIT_X, packagename, def);
    }

    public void setButtonHidePortraitY(String packagename, int v) {
        setIntValue(COL_HIDE_PORTRAIT_Y, packagename, v);
    }

    public int getButtonHidePortraitY(String packagename, int def) {
        return getIntValue(COL_HIDE_PORTRAIT_Y, packagename, def);
    }


    public void setButtonConfigLandscapeX(String packagename, int v) {
        setIntValue(COL_CONFIG_LANDSCAPE_X, packagename, v);
    }

    public int getButtonConfigLandscapeX(String packagename, int def) {
        return getIntValue(COL_CONFIG_LANDSCAPE_X, packagename, def);
    }

    public void setButtonConfigLandscapeY(String packagename, int v) {
        setIntValue(COL_CONFIG_LANDSCAPE_Y, packagename, v);
    }

    public int getButtonConfigLandscapeY(String packagename, int def) {
        return getIntValue(COL_CONFIG_LANDSCAPE_Y, packagename, def);
    }

    public void setButtonConfigPortraitX(String packagename, int v) {
        setIntValue(COL_CONFIG_PORTRAIT_X, packagename, v);
    }

    public int getButtonConfigPortraitX(String packagename, int def) {
        return getIntValue(COL_CONFIG_PORTRAIT_X, packagename, def);
    }

    public void setButtonConfigPortraitY(String packagename, int v) {
        setIntValue(COL_CONFIG_PORTRAIT_Y, packagename, v);
    }

    public int getButtonConfigPortraitY(String packagename, int def) {
        return getIntValue(COL_CONFIG_PORTRAIT_Y, packagename, def);
    }


    public void setButtonCameraLandscapeX(String packagename, int v) {
        setIntValue(COL_CAMERA_LANDSCAPE_X, packagename, v);
    }

    public int getButtonCameraLandscapeX(String packagename, int def) {
        return getIntValue(COL_CAMERA_LANDSCAPE_X, packagename, def);
    }

    public void setButtonCameraLandscapeY(String packagename, int v) {
        setIntValue(COL_CAMERA_LANDSCAPE_Y, packagename, v);
    }

    public int getButtonCameraLandscapeY(String packagename, int def) {
        return getIntValue(COL_CAMERA_LANDSCAPE_Y, packagename, def);
    }

    public void setButtonCameraPortraitX(String packagename, int v) {
        setIntValue(COL_CAMERA_PORTRAIT_X, packagename, v);
    }

    public int getButtonCameraPortraitX(String packagename, int def) {
        return getIntValue(COL_CAMERA_PORTRAIT_X, packagename, def);
    }

    public void setButtonCameraPortraitY(String packagename, int v) {
        setIntValue(COL_CAMERA_PORTRAIT_Y, packagename, v);
    }

    public int getButtonCameraPortraitY(String packagename, int def) {
        return getIntValue(COL_CAMERA_PORTRAIT_Y, packagename, def);
    }


    public void setButtonComposeLandscapeX(String packagename, int v) {
        setIntValue(COL_COMPOSE_LANDSCAPE_X, packagename, v);
    }

    public int getButtonComposeLandscapeX(String packagename, int def) {
        return getIntValue(COL_COMPOSE_LANDSCAPE_X, packagename, def);
    }

    public void setButtonComposeLandscapeY(String packagename, int v) {
        setIntValue(COL_COMPOSE_LANDSCAPE_Y, packagename, v);
    }

    public int getButtonComposeLandscapeY(String packagename, int def) {
        return getIntValue(COL_COMPOSE_LANDSCAPE_Y, packagename, def);
    }

    public void setButtonComposePortraitX(String packagename, int v) {
        setIntValue(COL_COMPOSE_PORTRAIT_X, packagename, v);
    }

    public int getButtonComposePortraitX(String packagename, int def) {
        return getIntValue(COL_COMPOSE_PORTRAIT_X, packagename, def);
    }

    public void setButtonComposePortraitY(String packagename, int v) {
        setIntValue(COL_COMPOSE_PORTRAIT_Y, packagename, v);
    }

    public int getButtonComposePortraitY(String packagename, int def) {
        return getIntValue(COL_COMPOSE_PORTRAIT_Y, packagename, def);
    }


    public void setButtonEncryptImeFullscreenX(String packagename, int v) {
        setIntValue(COL_BTENCRYPT_IMEFULLSCREEN_X, packagename, v);
    }

    public int getButtonEncryptImeFullscreenX(String packagename, int def) {
        return getIntValue(COL_BTENCRYPT_IMEFULLSCREEN_X, packagename, def);
    }

    public void setButtonEncryptImeFullscreenY(String packagename, int v) {
        setIntValue(COL_BTENCRYPT_IMEFULLSCREEN_Y, packagename, v);
    }

    public int getButtonEncryptImeFullscreenY(String packagename, int def) {
        return getIntValue(COL_BTENCRYPT_IMEFULLSCREEN_Y, packagename, def);
    }

    public void setButtonDecryptImeFullscreenX(String packagename, int v) {
        setIntValue(COL_BTDECRYPT_IMEFULLSCREEN_X, packagename, v);
    }

    public int getButtonDecryptImeFullscreenX(String packagename, int def) {
        return getIntValue(COL_BTDECRYPT_IMEFULLSCREEN_X, packagename, def);
    }

    public void setButtonDecryptImeFullscreenY(String packagename, int v) {
        setIntValue(COL_BTDECRYPT_IMEFULLSCREEN_Y, packagename, v);
    }

    public int getButtonDecryptImeFullscreenY(String packagename, int def) {
        return getIntValue(COL_BTDECRYPT_IMEFULLSCREEN_Y, packagename, def);
    }

    public AbstractOverlayButtonInputView.ANCHORH getButtonEncryptAnchorH(String packagename, AbstractOverlayButtonInputView.ANCHORH def) {
        return getIntValue(COL_BTENCRYPT_ANCHOR_H, packagename, (def == AbstractOverlayButtonInputView.ANCHORH.RIGHT ? 0 : 1)) == 0 ? AbstractOverlayButtonInputView.ANCHORH.RIGHT : AbstractOverlayButtonInputView.ANCHORH.LEFT;
    }

    public void setButtonEncryptAnchorH(String packagename, AbstractOverlayButtonInputView.ANCHORH anchorh) {
        setIntValue(COL_BTENCRYPT_ANCHOR_H, packagename, anchorh == AbstractOverlayButtonInputView.ANCHORH.RIGHT ? 0 : 1);
    }

    public AbstractOverlayButtonInputView.ANCHORV getButtonEncryptAnchorV(String packagename, AbstractOverlayButtonInputView.ANCHORV def) {
        return getIntValue(COL_BTENCRYPT_ANCHOR_V, packagename, (def == AbstractOverlayButtonInputView.ANCHORV.BOTTOM ? 0 : 1)) == 0 ? AbstractOverlayButtonInputView.ANCHORV.BOTTOM : AbstractOverlayButtonInputView.ANCHORV.TOP;
    }

    public void setButtonEncryptAnchorV(String packagename, AbstractOverlayButtonInputView.ANCHORV anchorv) {
        setIntValue(COL_BTENCRYPT_ANCHOR_V, packagename, anchorv == AbstractOverlayButtonInputView.ANCHORV.BOTTOM ? 0 : 1);
    }


    public AbstractOverlayButtonInputView.ANCHORH getButtonDecryptAnchorH(String packagename, AbstractOverlayButtonInputView.ANCHORH def) {
        return getIntValue(COL_BTDECRYPT_ANCHOR_H, packagename, (def == AbstractOverlayButtonInputView.ANCHORH.RIGHT ? 0 : 1)) == 0 ? AbstractOverlayButtonInputView.ANCHORH.RIGHT : AbstractOverlayButtonInputView.ANCHORH.LEFT;
    }

    public void setButtonDecryptAnchorH(String packagename, AbstractOverlayButtonInputView.ANCHORH ANCHORH) {
        setIntValue(COL_BTDECRYPT_ANCHOR_H, packagename, ANCHORH == AbstractOverlayButtonInputView.ANCHORH.RIGHT ? 0 : 1);
    }


    public AbstractOverlayButtonInputView.ANCHORV getButtonDecryptAnchorV(String packagename, AbstractOverlayButtonInputView.ANCHORV def) {
        return getIntValue(COL_BTDECRYPT_ANCHOR_V, packagename, (def == AbstractOverlayButtonInputView.ANCHORV.BOTTOM ? 0 : 1)) == 0 ? AbstractOverlayButtonInputView.ANCHORV.BOTTOM : AbstractOverlayButtonInputView.ANCHORV.TOP;
    }

    public void setButtonDecryptAnchorV(String packagename, AbstractOverlayButtonInputView.ANCHORV anchorv) {
        setIntValue(COL_BTDECRYPT_ANCHOR_V, packagename, anchorv == AbstractOverlayButtonInputView.ANCHORV.BOTTOM ? 0 : 1);
    }


    @SuppressLint("CommitPrefEdits")
    public void panic() {
        mPrefs.edit().putBoolean(PREF_BOSS_MODE, true).commit();
    }

    public boolean isBossMode() {
        return mPrefs.getBoolean(PREF_BOSS_MODE, false);
    }

    @SuppressLint("CommitPrefEdits")
    public void disablePanicMode() {
        mPrefs.edit().putBoolean(PREF_BOSS_MODE, false).commit();
    }


    public String getGpgPadder(String packagename) {
        return getStringValue(COL_PADDER_PGP, packagename, mCtx.getString(R.string.padder_lorem));
    }

    public void setGpgPadder(String packagename, String v) {
        setStringValue(COL_PADDER_PGP, packagename, v);
    }

    public String getSymPadder(String packagename) {
        return getStringValue(COL_PADDER_SYM, packagename, mCtx.getString(R.string.padder_lorem));
    }

    public void setSymPadder(String packagename, String v) {
        setStringValue(COL_PADDER_SYM, packagename, v);
    }

    public String getGpgXcoder(String packagename) {
        return getStringValue(COL_XCODER_PGP, packagename, ZeroWidthXCoder.ID);
    }

    public void setGpgXcoder(String packagename, String v) {
        setStringValue(COL_XCODER_PGP, packagename, v);
    }

    public String getSymXcoder(String packagename) {
        return getStringValue(COL_XCODER_SYM, packagename, AsciiArmouredGpgXCoder.ID);
    }

    public void setSymXcoder(String packagename, String v) {
        setStringValue(COL_XCODER_SYM, packagename, v);
    }

    public EncryptionMethod getLastEncryptionMethod(String packagename) {
        String v = getStringValue(COL_LAST_ENC_METHOD, packagename, null);
        return v == null ? null : EncryptionMethod.valueOf(v);
    }

    public void setLastEncryptionMethod(String packagename, EncryptionMethod m) {
        setStringValue(COL_LAST_ENC_METHOD, packagename, m.name());
    }


    public int getMaxInnerPadding(String packagename) {
        return getIntValue(COL_MAX_INNER_PADDING, packagename, 8);
    }

    public void setMaxInnerPadding(String packagename, int v) {
        setIntValue(COL_MAX_INNER_PADDING, packagename, v);
    }

    public boolean isAppEnabled(String packagename) {
        return getIntValue(COL_ENABLED, packagename, 0) > 0;
    }

    public void setAppEnabled(String packagename, boolean enabled) {
        setIntValue(COL_ENABLED, packagename, enabled ? 1 : 0);
        if (enabled && Issues.INSTANCE.hasSeriousIssues(packagename)) {
            WarningActivity.showAppWithSeriousIssuesEnabled(mCtx, packagename);
        }
    }


    public boolean isStoreEncryptionParamsPerPackageOnly(String packagename) {
        return getIntValue(COL_STORE_ENCRYPTION_PARAMS_PER_PACKAGE_ONLY, packagename, 0) > 0;
    }

    public void setStoreEncryptionParamsPerPackageOnly(String packagename, boolean enabled) {
        setIntValue(COL_STORE_ENCRYPTION_PARAMS_PER_PACKAGE_ONLY, packagename, enabled ? 1 : 0);
    }


    public boolean isStartHidden(String packagename) {
        boolean r = getIntValue(COL_START_HIDDEN, packagename, 0) > 0;
        return r;
    }

    public void setStartHidden(String packagename, boolean hidden) {
        setIntValue(COL_START_HIDDEN, packagename, hidden ? 1 : 0);

    }


    public boolean isShowStatusIcon(String packagename) {
        return getIntValue(COL_SHOW_STATUS_ICON, packagename, 0) > 0;
    }

    public void setShowStatusIcon(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_STATUS_ICON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowHideButton(String packagename) {
        return getIntValue(COL_SHOW_HIDE_BUTTON, packagename, 1) > 0;
    }

    public void setShowHideButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_HIDE_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowInfoButton(String packagename) {
        return getIntValue(COL_SHOW_INFO_BUTTON, packagename, 0) > 0;
    }

    public void setShowInfoButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_INFO_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }


    public boolean isShowCameraButton(String packagename) {
        if (!mCtx.getResources().getBoolean(R.bool.feature_takephoto)) {
            return false;
        }
        return getIntValue(COL_SHOW_CAMERA_BUTTON, packagename, 0) > 0;
    }

    public void setShowCameraButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_CAMERA_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowComposeButton(String packagename) {
        return getIntValue(COL_SHOW_COMPOSE_BUTTON, packagename, 0) > 0;
    }

    public void setShowComposeButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_COMPOSE_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }


    public boolean isShowDecryptButton(String packagename) {
        return getIntValue(COL_SHOW_DECRYPT_BUTTON, packagename, 1) > 0;
    }

    public void setShowDecryptButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_DECRYPT_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowConfigButton(String packagename) {
        return getIntValue(COL_SHOW_CONFIG_BUTTON, packagename, 1) > 0;
    }

    public void setShowConfigButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_CONFIG_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowInfoOnTap(String packagename) {
        return getIntValue(COL_SHOW_INFO_ON_TAP, packagename, 0) > 0;
    }

    public void setShowInfoOnTap(String packagename, boolean v) {
        setIntValue(COL_SHOW_INFO_ON_TAP, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowInfoOnLongTap(String packagename) {
        return getIntValue(COL_SHOW_INFO_ON_LONGTAP, packagename, 0) > 0;
    }

    public void setShowInfoOnLongTap(String packagename, boolean v) {
        setIntValue(COL_SHOW_INFO_ON_LONGTAP, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowEncryptButton(String packagename) {
        return getIntValue(COL_SHOW_ENCRYPT_BUTTON, packagename, 1) > 0;
    }

    public void setShowEncryptButton(String packagename, boolean enabled) {
        setIntValue(COL_SHOW_ENCRYPT_BUTTON, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isToggleEncryptButtonOnLongTap(String packagename) {
        return getIntValue(COL_TOGGLE_ENCRYPT_LONGTAP, packagename, 0) > 0;
    }

    public void setToggleEncryptButtonOnLongTap(String packagename, boolean v) {
        setIntValue(COL_TOGGLE_ENCRYPT_LONGTAP, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }


    public boolean isShowUserInteractionDialogsImmediately(String packagename) {
        return getIntValue(COL_SHOWUSERINTERACTIONDIALOGSIMMEDIATELY, packagename, 0) > 0;
    }

    public void setShowUserInteractionDialogsImmediately(String packagename, boolean v) {
        setIntValue(COL_SHOWUSERINTERACTIONDIALOGSIMMEDIATELY, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isVoverflow(String packagename) {
        return getIntValue(COL_VOVERFLOW, packagename, 0) > 0;
    }

    public void setVoverflow(String packagename, boolean v) {
        setIntValue(COL_VOVERFLOW, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isOverlayAboveInput(String packagename) {
        return getIntValue(COL_ABOVE, packagename, 1) > 0;
    }

    public void setOverlayAboveInput(String packagename, boolean v) {
        setIntValue(COL_ABOVE, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }


    public boolean isForceEncryptionParams(String packagename) {
        return getIntValue(COL_FORCEENCRYPTIONPARAMS, packagename, 0) > 0;
    }

    public void setForceEncryptionParams(String packagename, boolean v) {
        setIntValue(COL_FORCEENCRYPTIONPARAMS, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }


    public boolean isSpreadInvisibleEncoding(String packagename) {
        return getIntValue(COL_SPREAD_INVISIBLE_ENCODING, packagename, 0) > 0;
    }

    public void setSpreadInvisibleEncoding(String packagename, boolean v) {
        setIntValue(COL_SPREAD_INVISIBLE_ENCODING, packagename, v ? 1 : 0);
    }


    public boolean isDontShowDecryptionFailed(String packagename) {
        return getIntValue(COL_DONT_SHOW_DECRYPTION_FAILED, packagename, 0) > 0;
    }

    public void setDontShowDecryptionFailed(String packagename, boolean v) {
        setIntValue(COL_DONT_SHOW_DECRYPTION_FAILED, packagename, v ? 1 : 0);
    }

    public boolean isAppendNewLines(String packagename) {
        return getIntValue(COL_NEWLINE, packagename, 0) > 0;
    }

    public void setAppendNewLines(String packagename, boolean v) {
        setIntValue(COL_NEWLINE, packagename, v ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isHqScrape(String packagename) {
        return getIntValue(COL_HQ_SCRAPE, packagename, 0) > 0;
    }

    public void setHqScrape(String packagename, boolean enabled) {
        setIntValue(COL_HQ_SCRAPE, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isIncludeNonImportantViews(String packagename) {
        int defaultValue = 0;
        return getIntValue(COL_INCLUDE_NON_IMPORTANT_VIEWS, packagename, defaultValue) > 0;
    }

    public void setIncludeNonImportantViews(String packagename, boolean enabled) {
        setIntValue(COL_INCLUDE_NON_IMPORTANT_VIEWS, packagename, enabled ? 1 : 0);
        fireDecryptOverlayLayoutParamsChanged(packagename);
    }

    public boolean isShowNotification(String packagename) {
        if (!mCtx.getResources().getBoolean(R.bool.feature_notification)) {
            return false;
        }
        return getIntValue(COL_NOTIFICATION, packagename, 1) > 0;
    }

    public void setShowNotification(String packagename, boolean show) {
        setIntValue(COL_NOTIFICATION, packagename, show ? 1 : 0);
    }


    private LruCache<String, PackageCache> mPackageCache = new LruCache<>(50);


    private class PackageCache {
        private Map<String, Integer> mIntCache = Collections.synchronizedMap(new HashMap<String, Integer>());
        private Map<String, String> mStringCache = Collections.synchronizedMap(new HashMap<String, String>());
    }


    private synchronized PackageCache getOrCreatePackageCache(String packagename) {
        PackageCache r = mPackageCache.get(packagename);
        //Ln.d("PackageCache "+packagename+"  =  "+r);
        if (r == null) {
            r = new PackageCache();
            mPackageCache.put(packagename, r);
        }
        return r;
    }

    private synchronized void setIntValue(String col, String packagename, int value) {

        if (packagename == null) {
            Ln.w("got null packagename, ignoring this call");
            return;
        }
        PackageCache packageCache = getOrCreatePackageCache(packagename);

        Integer curValue = packageCache.mIntCache.get(col);
        if (curValue == null || curValue != value) {
            ContentValues values = new ContentValues();
            values.put(COL_NAME, packagename);
            values.put(col, value);
            int r = mDb.update(TABLE_PACKAGES, values, COL_NAME + "=?",
                    new String[]{packagename});
            if (r == 0) {
                mDb.insert(TABLE_PACKAGES, null, values);
            }
        }

        packageCache.mIntCache.put(col, value);
    }


    private synchronized int getIntValue(String col, String packagename, int def) {
        if (packagename == null) {
            Ln.w("got null packagename, return default value");
            return def;
        }

        // Ln.d("getIntValue %s   %s",col,packagename);

        PackageCache packageCache = getOrCreatePackageCache(packagename);
        if (!packageCache.mIntCache.containsKey(col)) {
            Cursor query = mDb.query(TABLE_PACKAGES, new String[]{col}, COL_NAME
                    + "=?", new String[]{packagename}, null, null, null);
            try {
                if (query.moveToFirst()) {
                    if (!query.isNull(0)) {
                        int r = query.getInt(0);
                        // Ln.d("getIntValue return "+r+" from query");
                        packageCache.mIntCache.put(col, r);
                        return r;
                    } else {
                        //  Ln.d("getIntValue return DEFAULT value from query A");
                        packageCache.mIntCache.put(col, null);
                        return def;
                    }
                } else {
                    // Ln.d("getIntValue return DEFAULT value from query B");
                    packageCache.mIntCache.put(col, null);
                    return def;
                }
            } finally {
                // Ln.d("getIntValue closeQuery");
                query.close();
            }
        } else {
            Integer curValue = packageCache.mIntCache.get(col);
            if (curValue == null) {
                // Ln.d("getIntValue return DEFAULT value from x cache");
                return def;
            } else {
                // Ln.d("getIntValue return "+curValue+" from x cache");
                return curValue;
            }
        }


    }

    private synchronized void setStringValue(String col, String packagename, String value) {
        if (packagename == null) {
            Ln.w("got null packagename, ignoring this call");
            return;
        }
        PackageCache packageCache = getOrCreatePackageCache(packagename);

        String curValue = packageCache.mStringCache.get(col);
        if (curValue == null || !curValue.equals(value)) {
            ContentValues values = new ContentValues();
            values.put(COL_NAME, packagename);
            values.put(col, value);
            int r = mDb.update(TABLE_PACKAGES, values, COL_NAME + "=?",
                    new String[]{packagename});
            if (r == 0) {
                mDb.insert(TABLE_PACKAGES, null, values);
            }
        }
        if (value != null) {
            packageCache.mStringCache.put(col, value);
        }
    }

    private synchronized String getStringValue(String col, String packagename, String def) {
        if (packagename == null) {
            Ln.w("got null packagename, return default value");
            return def;
        }

        // Ln.d("getIntValue %s   %s",col,packagename);

        PackageCache packageCache = getOrCreatePackageCache(packagename);
        if (!packageCache.mStringCache.containsKey(col)) {
            Cursor query = mDb.query(TABLE_PACKAGES, new String[]{col}, COL_NAME
                    + "=?", new String[]{packagename}, null, null, null);
            try {
                if (query.moveToFirst()) {
                    if (!query.isNull(0)) {
                        String r = query.getString(0);
                        // Ln.d("getIntValue return "+r+" from query");
                        packageCache.mStringCache.put(col, r);
                        return r;
                    } else {
                        //  Ln.d("getIntValue return DEFAULT value from query A");
                        packageCache.mStringCache.put(col, null);
                        return def;
                    }
                } else {
                    // Ln.d("getIntValue return DEFAULT value from query B");
                    packageCache.mStringCache.put(col, null);
                    return def;
                }
            } finally {
                // Ln.d("getIntValue closeQuery");
                query.close();
            }
        } else {
            String curValue = packageCache.mStringCache.get(col);
            if (curValue == null) {
                // Ln.d("getIntValue return DEFAULT value from x cache");
                return def;
            } else {
                // Ln.d("getIntValue return "+curValue+" from x cache");
                return curValue;
            }
        }


    }

    public Set<String> getIgnoredPackages() {
        return mIgnorePackages;
    }

    public String dumpSettings(String packagename) {
        StringBuilder sb = new StringBuilder();

        Cursor query = mDb.query(TABLE_PACKAGES, null, COL_NAME
                + "=?", new String[]{packagename}, null, null, null);
        if (query.moveToFirst()) {
            int cc = query.getColumnCount();
            for (int i = 0; i < cc; i++) {
                sb.append(query.getColumnName(i)).append(" = ").append(query.getString(i));
                sb.append("\n");
            }
        }
        query.close();
        return sb.toString();
    }


    public Long getInstallDate() {
        //note: install date is somehow disguised as the smallest INSERT date of any column in the main table
        //since we prefill some stuff in this table it is guaranteed to match the first use date!
        Long r = null;
        Cursor query = mDb.rawQuery("select min(" + COL_TIMESTAMP + ") from " + TABLE_PACKAGES, null);
        if (query.moveToFirst()) {
            r = query.getLong(0) * 1000;
        }
        query.close();
        return r;
    }


    class MyDatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "settings";

        private static final int DATABASE_VERSION = 87;

        // Database creation sql statement
        private final String DATABASE_CREATE = String
                .format("create table %s (%s text PRIMARY KEY,  " +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s integer," +
                                "%s string," +
                                "%s string," +
                                "%s string," +
                                "%s string," +
                                "%s string," +
                                "%s integer(4) default (cast(strftime('%s','now') as int)) NOT NULL" +
                                ")",
                        TABLE_PACKAGES,
                        COL_NAME,
                        COL_ENABLED,
                        COL_START_HIDDEN,
                        COL_NOTIFICATION,
                        COL_PADDING_LEFT,
                        COL_PADDING_TOP,
                        COL_RADIUS,
                        COL_TEXTSIZE,
                        COL_FG_COLOR,
                        COL_BG_COLOR,
                        COL_BUTTON_COLOR,
                        COL_SHOW_STATUS_ICON,
                        COL_SHOW_INFO_BUTTON,
                        COL_SHOW_HIDE_BUTTON,
                        COL_SHOW_DECRYPT_BUTTON,
                        COL_SHOW_CONFIG_BUTTON,
                        COL_BTENCRYPT_DELTA_X,
                        COL_BTENCRYPT_DELTA_Y,
                        COL_BTDECRYPT_DELTA_X,
                        COL_BTDECRYPT_DELTA_Y,
                        COL_INFOMODE_LANDSCAPE_X,
                        COL_INFOMODE_LANDSCAPE_Y,
                        COL_INFOMODE_PORTRAIT_X,
                        COL_INFOMODE_PORTRAIT_Y,
                        COL_HIDE_PORTRAIT_X,
                        COL_HIDE_PORTRAIT_Y,
                        COL_HIDE_LANDSCAPE_X,
                        COL_HIDE_LANDSCAPE_Y,
                        COL_CONFIG_PORTRAIT_X,
                        COL_CONFIG_PORTRAIT_Y,
                        COL_CONFIG_LANDSCAPE_X,
                        COL_CONFIG_LANDSCAPE_Y,
                        COL_BTENCRYPT_IMEFULLSCREEN_X,
                        COL_BTENCRYPT_IMEFULLSCREEN_Y,
                        COL_BTDECRYPT_IMEFULLSCREEN_X,
                        COL_BTDECRYPT_IMEFULLSCREEN_Y,
                        COL_BTDECRYPT_ANCHOR_H,
                        COL_BTDECRYPT_ANCHOR_V,
                        COL_BTENCRYPT_ANCHOR_H,
                        COL_BTENCRYPT_ANCHOR_V,
                        COL_HQ_SCRAPE,
                        COL_SHOW_INFO_ON_TAP,
                        COL_SHOW_INFO_ON_LONGTAP,
                        COL_TOGGLE_ENCRYPT_LONGTAP,
                        COL_SHOW_ENCRYPT_BUTTON,

                        COL_VOVERFLOW,
                        COL_ABOVE,
                        COL_FORCEENCRYPTIONPARAMS,
                        COL_NEWLINE,
                        COL_MAX_INNER_PADDING,
                        COL_SHOW_CAMERA_BUTTON,
                        COL_CAMERA_PORTRAIT_X,
                        COL_CAMERA_PORTRAIT_Y,
                        COL_CAMERA_LANDSCAPE_X,
                        COL_CAMERA_LANDSCAPE_Y,
                        COL_SHOW_COMPOSE_BUTTON,
                        COL_COMPOSE_PORTRAIT_X,
                        COL_COMPOSE_PORTRAIT_Y,
                        COL_COMPOSE_LANDSCAPE_X,
                        COL_COMPOSE_LANDSCAPE_Y,
                        COL_INCLUDE_NON_IMPORTANT_VIEWS,
                        COL_SHOWUSERINTERACTIONDIALOGSIMMEDIATELY,
                        COL_STORE_ENCRYPTION_PARAMS_PER_PACKAGE_ONLY,
                        COL_SPREAD_INVISIBLE_ENCODING,
                        COL_DONT_SHOW_DECRYPTION_FAILED,
                        COL_PADDER_SYM,
                        COL_PADDER_PGP,
                        COL_XCODER_SYM,
                        COL_XCODER_PGP,
                        COL_LAST_ENC_METHOD,
                        COL_TIMESTAMP,
                        "%s");
        private static final String DATABASE_PREFILL = "insert into %s (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) values (\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\")";

        private final PrefillValues[] PREENABLED_PACKAGES = new PrefillValues[]{
                new PrefillValues("com.google.android.apps.docs.editors.docs", AppType.NOTES), //Google Docs

                new PrefillValues("com.mywickr.wickr2", AppType.CHAT), //Wickr
                new PrefillValues("com.wire", AppType.CHAT), //Wire
                new PrefillValues("com.google.android.apps.fireball", AppType.CHAT), //Allo
                new PrefillValues("jp.naver.line.android", AppType.CHAT),
                new PrefillValues("org.telegram.messenger", AppType.CHAT),
                new PrefillValues("com.instagram.android", AppType.CHAT),
                new PrefillValues("com.android.mms", AppType.SMS),
                new PrefillValues("com.android.messaging", AppType.CHAT),
                new PrefillValues("fr.slvn.mms", AppType.SMS),
                new PrefillValues("com.facebook.orca", AppType.CHAT),
                new PrefillValues("com.myyearbook.m", AppType.CHAT),
                new PrefillValues("com.tencent.mm", AppType.CHAT),
                new PrefillValues("com.icq.mobile.client", AppType.CHAT),
                new PrefillValues("com.coolots.chaton", AppType.CHAT),
                new PrefillValues("com.sec.chaton", AppType.CHAT),
                new PrefillValues("com.keechat.client", AppType.CHAT),
                new PrefillValues("com.sgiggle.production", AppType.CHAT),
                new PrefillValues("com.thinkyeah.message", AppType.SMS),
                new PrefillValues("com.google.android.talk", AppType.CHAT),
                new PrefillValues("com.kakao.talk", AppType.CHAT),
                new PrefillValues("com.littleinc.MessageMe", AppType.CHAT),
                new PrefillValues("kik.android", AppType.CHAT),
                new PrefillValues("com.liquable.nemo", AppType.CHAT),
                new PrefillValues("com.maaii.maaii", AppType.CHAT),
                new PrefillValues("com.snapchat.android", AppType.CHAT),
                new PrefillValues("com.skype.raider", AppType.CHAT),
                new PrefillValues("com.viber.voip", AppType.CHAT),
                new PrefillValues("com.rebelvox.voxer", AppType.CHAT),
                new PrefillValues("com.socialnmobile.dictapps.notepad.color.note", AppType.NOTES),
                new PrefillValues("genius.mohammad.floating.stickies", AppType.NOTES),
                new PrefillValues("com.gs.stickit", AppType.NOTES),
                new PrefillValues("com.cg.stickynote", AppType.NOTES),
                new PrefillValues("pl.viverra.stickynoteswidget", AppType.NOTES),
                new PrefillValues("com.clarendon128.android.widget.stickynote", AppType.NOTES),
                new PrefillValues("com.symcoding.widget.stickynotes", AppType.CHAT),
                new PrefillValues("com.dvaslona.alarmnote", AppType.NOTES),
                new PrefillValues("com.movinapp.easypad", AppType.NOTES),
                new PrefillValues("com.mazesystem.fusen", AppType.NOTES),
                new PrefillValues("me.sanzui.sticky", AppType.NOTES),
                new PrefillValues("sticky.notes.live.wallpaper.boro", AppType.NOTES),
                new PrefillValues("org.artsplanet.android.orepanmemo", AppType.NOTES),
                new PrefillValues("com.thoughtpository.FastNotes", AppType.NOTES),
                new PrefillValues("com.electricpocket.bugme2.free", AppType.NOTES),
                new PrefillValues("com.houmiak.desknote", AppType.NOTES),
                new PrefillValues("com.gs.stickitpaid", AppType.NOTES),
                new PrefillValues("com.phoenix.memo", AppType.NOTES),
                new PrefillValues("de.qeepinboard", AppType.NOTES),
                new PrefillValues("com.ninexgen.stickernote", AppType.NOTES),
                new PrefillValues("com.symcoding.widget.stickynotesfull", AppType.NOTES),
                new PrefillValues("com.movinapp.quicknote", AppType.NOTES),
                new PrefillValues("jp.co.atgs.android.cutesticky", AppType.NOTES),
                new PrefillValues("com.tokasiki.android.sticky", AppType.NOTES),
                new PrefillValues("com.clearnotewidget", AppType.NOTES),
                new PrefillValues("marihart.stickynotes", AppType.NOTES),
                new PrefillValues("com.aaravmedia.stickynotes", AppType.NOTES),
                new PrefillValues("com.ivandroid.stickynote", AppType.NOTES),
                new PrefillValues("com.tremeric.stickynotes", AppType.NOTES),
                new PrefillValues("quick.stickynotes.pad.fridge.notes.lite", AppType.NOTES),
                new PrefillValues("com.newapps.stickynotes", AppType.NOTES),
                new PrefillValues("com.exp.kumamonsticky", AppType.NOTES),
                new PrefillValues("com.tremeric.stickynotes.plus", AppType.NOTES),
                new PrefillValues("com.nosapps.android.bothermenotes", AppType.NOTES),
                new PrefillValues("com.notepad.notes.free", AppType.NOTES),
                new PrefillValues("com.happyneko.stickit", AppType.NOTES),
                new PrefillValues("com.nosapps.android.bothermenotesadfree", AppType.NOTES),
                new PrefillValues("tk.eatheat.stickynotes", AppType.NOTES),
                new PrefillValues("com.coderscale.stickynote", AppType.NOTES),
                new PrefillValues("com.lockpostit", AppType.NOTES),
                new PrefillValues("com.superdevs.stickynotes", AppType.NOTES),
                new PrefillValues("com.harteg.NotesWidget", AppType.NOTES),
                new PrefillValues("com.ap.StickyNoteCustom", AppType.NOTES),
                new PrefillValues("com.houmiak.postme", AppType.NOTES),
                new PrefillValues("com.jaumo", AppType.CHAT),
                new PrefillValues("com.chatimity.android.chatimity", AppType.CHAT),
                new PrefillValues("com.unearby.sayhi", AppType.CHAT),
                new PrefillValues("com.minus.android", AppType.CHAT),
                new PrefillValues("com.waplog.social", AppType.CHAT),
                new PrefillValues("chatwap.in", AppType.CHAT),
                new PrefillValues("com.tencent.mobileqqi", AppType.CHAT),
                new PrefillValues("com.futurebits.instamessage.free", AppType.CHAT),
                new PrefillValues("com.camshare.camfrog.android", AppType.CHAT),
                new PrefillValues("air.es.chatvideo.mobile", AppType.CHAT),
                new PrefillValues("com.bluelionmobile.qeep.client.android", AppType.CHAT),
                new PrefillValues("net.lovoo.android", AppType.CHAT),
                new PrefillValues("chat.roid", AppType.CHAT),
                new PrefillValues("ru.mail", AppType.EMAIL),
                new PrefillValues("com.jnj.mocospace.android", AppType.CHAT),
                new PrefillValues("com.handmark.friendcaster.chat", AppType.CHAT),
                new PrefillValues("appinventor.ai_deniskdesign.GirlsChat", AppType.CHAT),
                new PrefillValues("air.Chat.org", AppType.CHAT),
                new PrefillValues("air.com.oligarch.vRoulette", AppType.CHAT),
                new PrefillValues("com.spartancoders.gtok", AppType.CHAT),
                new PrefillValues("com.my.chat", AppType.CHAT),
                new PrefillValues("com.axosoft.PureChat", AppType.CHAT),
                new PrefillValues("com.skout.android", AppType.CHAT),
                new PrefillValues("mobisocial.omlet", AppType.CHAT),
                new PrefillValues("com.c2call.app.android.friendcaller", AppType.CHAT),
                new PrefillValues("asp.emoticons.app", AppType.CHAT),
                new PrefillValues("com.rei.lolchatads", AppType.CHAT),
                new PrefillValues("com.okhello.typhoid.release", AppType.CHAT),
                new PrefillValues("com.paltalk.chat.android", AppType.CHAT),
                new PrefillValues("com.chatous.chatous", AppType.CHAT),
                new PrefillValues("appinventor.ai_deniskdesign.ChatChat", AppType.CHAT),
                new PrefillValues("com.teenchat", AppType.CHAT),
                new PrefillValues("com.sayhi.plugin.moxi", AppType.CHAT),
                new PrefillValues("ru.mobstudio.andgalaxy", AppType.CHAT),
                new PrefillValues("com.talkray.client", AppType.CHAT),
                new PrefillValues("com.FunForMobile.main", AppType.CHAT),
                new PrefillValues("air.com.videochatcommunity.chat", AppType.CHAT),
                new PrefillValues("com.taggedapp", AppType.CHAT),
                new PrefillValues("com.chatfrankly.android", AppType.CHAT),
                new PrefillValues("com.inbox.boro.lite", AppType.CHAT),
                new PrefillValues("com.whatsapp", AppType.CHAT),
                new PrefillValues("com.yahoo.mobile.client.android.im", AppType.CHAT),
                new PrefillValues("com.bsb.hike", AppType.CHAT),
                new PrefillValues("org.solovyev.android.messenger", AppType.CHAT),
                new PrefillValues("com.outfit7.tomsmessengerfree", AppType.CHAT),
                new PrefillValues("com.bbm", AppType.CHAT),
                new PrefillValues("net.daum.android.air", AppType.CHAT),
                new PrefillValues("com.aleskovacic.messenger", AppType.CHAT),
                new PrefillValues("im.mercury.android", AppType.CHAT),
                new PrefillValues("com.mplusapp", AppType.CHAT),
                new PrefillValues("org.apache.android.xmpp", AppType.CHAT),
                new PrefillValues("org.thoughtcrime.securesms", AppType.CHAT),
                new PrefillValues("com.instachat.android", AppType.CHAT),
                new PrefillValues("com.yahoo.mobile.client.android.imvideo", AppType.CHAT),
                new PrefillValues("com.nimbuzz", AppType.CHAT),
                new PrefillValues("ironroad.vms", AppType.CHAT),
                new PrefillValues("com.chatmessengerlite", AppType.CHAT),
                new PrefillValues("com.voyager.babble", AppType.CHAT),
                new PrefillValues("org.koxx.pure_messenger", AppType.CHAT),
                new PrefillValues("com.iwantim.iwi", AppType.CHAT),
                new PrefillValues("com.webaroo.replyall", AppType.CHAT),
                new PrefillValues("com.palringo.android", AppType.CHAT),
                new PrefillValues("com.hanbiro.android.messenger", AppType.CHAT),
                new PrefillValues("com.chad2win.Chad2Win", AppType.CHAT),
                new PrefillValues("com.shoutme", AppType.CHAT),
                new PrefillValues("com.Saudi.Messenger", AppType.CHAT),
                new PrefillValues("com.path.paperboy", AppType.CHAT),
                new PrefillValues("com.messenger.labs", AppType.CHAT),
                new PrefillValues("com.catfiz", AppType.CHAT),
                new PrefillValues("com.xiaomi.channel", AppType.CHAT),
                new PrefillValues("com.piip.android", AppType.CHAT),
                new PrefillValues("com.chatwala.chatwala", AppType.CHAT),
                new PrefillValues("com.homemeeting.msgr", AppType.CHAT),
                new PrefillValues("com.hellomessenger.androidclient", AppType.CHAT),
                new PrefillValues("com.appme.android", AppType.CHAT),
                new PrefillValues("com.openmatics.app.display.messenger", AppType.CHAT),
                new PrefillValues("com.coderplus.android.ipmsg", AppType.CHAT),
                new PrefillValues("mic.messenger.im", AppType.CHAT),
                new PrefillValues("hn.mayak", AppType.CHAT),
                new PrefillValues("com.rokoroku.lolmessenger", AppType.CHAT),
                new PrefillValues("com.radiumone.pingme", AppType.CHAT),
                new PrefillValues("com.myApp.main", AppType.CHAT),
                new PrefillValues("com.meeble.talkdroidpro", AppType.CHAT),
                new PrefillValues("com.weivapp", AppType.CHAT),
                new PrefillValues("com.jb.gosms", AppType.SMS),
                new PrefillValues("com.hellotext.hello", AppType.SMS),
                new PrefillValues("com.p1.chompsms", AppType.SMS),
                new PrefillValues("com.riteshsahu.SMSBackupRestore", AppType.SMS),
                new PrefillValues("com.klinker.android.evolve_sms", AppType.SMS),
                new PrefillValues("com.textra", AppType.SMS),
                new PrefillValues("com.kanokgems.smswidget", AppType.SMS),
                new PrefillValues("com.texty.sms", AppType.SMS),
                new PrefillValues("com.frozenex.latestnewsms", AppType.SMS),
                new PrefillValues("com.zegoggles.smssync", AppType.SMS),
                new PrefillValues("com.twentyfoursms", AppType.SMS),
                new PrefillValues("com.handcent.nextsms", AppType.SMS),
                new PrefillValues("com.idea.backup.smscontacts", AppType.SMS),
                new PrefillValues("com.tmnlab.autoresponder", AppType.SMS),
                new PrefillValues("com.jb.gosms.emoji", AppType.SMS),
                new PrefillValues("net.everythingandroid.smspopup", AppType.SMS),
                new PrefillValues("sun.way2sms.hyd.com", AppType.SMS),
                new PrefillValues("com.apps.ringtonesSMS", AppType.SMS),
                new PrefillValues("com.tmappz.smslibrary", AppType.SMS),
                new PrefillValues("com.funapps.smshub", AppType.SMS),
                new PrefillValues("com.vladlee.smsblacklist", AppType.SMS),
                new PrefillValues("com.ifreeindia.sms_mazaa", AppType.SMS),
                new PrefillValues("com.pansi.msg", AppType.SMS),
                new PrefillValues("com.gosms.LoveLove", AppType.SMS),
                new PrefillValues("com.mobilitysol.free.sms.collections", AppType.SMS),
                new PrefillValues("com.p1.chompsms.emoji", AppType.SMS),
                new PrefillValues("com.deluxeapps.lovesms", AppType.SMS),
                new PrefillValues("com.smart.sms.collection.nav", AppType.SMS),
                new PrefillValues("com.riteshsahu.SMSBackupRestoreNetworkAddon", AppType.SMS),
                new PrefillValues("com.popularapp.fakecall", AppType.SMS),
                new PrefillValues("kr.co.tictocplus", AppType.SMS),
                new PrefillValues("com.thinkyeah.smslocker", AppType.SMS),
                new PrefillValues("com.netqin.ps", AppType.CHAT),
                new PrefillValues("com.textra.emoji", AppType.CHAT),
                new PrefillValues("vn.android.smstextpics", AppType.SMS),
                new PrefillValues("cz.vojtisek.freesmssender", AppType.CHAT),
                new PrefillValues("com.p1.chompsms.themes", AppType.CHAT),
                new PrefillValues("com.mightytext.tablet", AppType.CHAT),
                new PrefillValues("com.gizmoquip.smstracker", AppType.SMS),
                new PrefillValues("com.uvm.smshub", AppType.SMS),
                new PrefillValues("ru.vsms", AppType.SMS),
                new PrefillValues("crometh.android.nowsms", AppType.SMS),
                new PrefillValues("com.jb.gosmspro.theme.icecream", AppType.SMS),
                new PrefillValues("com.cliqs.smslibrary", AppType.SMS),
                new PrefillValues("com.smitten.slidingmms", AppType.SMS),
                new PrefillValues("cz.mobilecity", AppType.CHAT),
                new PrefillValues("com.flufflydelusions.app.enotesclassiclite", AppType.NOTES),
                new PrefillValues("com.google.android.keep", AppType.CHAT),
                new PrefillValues("com.evernote", AppType.CHAT),
                new PrefillValues("air.com.adobe.pstouch", AppType.CHAT),
                new PrefillValues("de.softxperience.android.noteeverything", AppType.NOTES),
                new PrefillValues("com.workpail.inkpad.notepad.notes", AppType.NOTES),
                new PrefillValues("yong.app.notes", AppType.NOTES),
                new PrefillValues("com.task.notes", AppType.NOTES),
                new PrefillValues("kr.dinosoft.android.ExperienceG3Note_gl", AppType.NOTES),
                new PrefillValues("com.fihtdc.note", AppType.NOTES),
                new PrefillValues("my.handrite", AppType.NOTES),
                new PrefillValues("com.crazelle.app.notepad", AppType.NOTES),
                new PrefillValues("com.coco.notes", AppType.NOTES),
                new PrefillValues("app.angeldroid.safenotepad", AppType.NOTES),
                new PrefillValues("com.estrongs.android.pop", AppType.NOTES),
                new PrefillValues("com.naturalapps.notas", AppType.NOTES),
                new PrefillValues("com.bvalosek.cpuspy", AppType.NOTES),
                new PrefillValues("nl.jacobras.notes", AppType.NOTES),
                new PrefillValues("de.Notizen", AppType.NOTES),
                new PrefillValues("com.niks.notes", AppType.NOTES),
                new PrefillValues("com.fiistudio.fiinote", AppType.NOTES),
                new PrefillValues("notepad.memocool.free", AppType.NOTES),
                new PrefillValues("com.teragadgets.android.notes", AppType.NOTES),
                new PrefillValues("com.bigtexapps.android.notes", AppType.NOTES),
                new PrefillValues("com.blogspot.logpedia.note", AppType.NOTES),
                new PrefillValues("com.kydsessc.amzn", AppType.NOTES),
                new PrefillValues("com.steadee.callnote", AppType.NOTES),
                new PrefillValues("com.steadfastinnovation.android.projectpapyrus", AppType.NOTES),
                new PrefillValues("com.diotek.dionote", AppType.NOTES),
                new PrefillValues("com.Everyday_Note_1208074", AppType.NOTES),
                new PrefillValues("it.mm.android.securememo", AppType.NOTES),
                new PrefillValues("com.guardam.personalNotes", AppType.NOTES),
                new PrefillValues("com.fandfdev.notes", AppType.NOTES),
                new PrefillValues("com.android.demo.notepad3", AppType.NOTES),
                new PrefillValues("com.com.sixamthree.oversec.crypto.images.xcoder.f5.james.SmartNotepad", AppType.NOTES),
                new PrefillValues("it.sephiroth.inotes", AppType.NOTES),
                new PrefillValues("asciitek.ransomnotes", AppType.NOTES),
                new PrefillValues("com.ap.WidgetTest", AppType.NOTES),
                new PrefillValues("com.adpog.diary", AppType.NOTES),
                new PrefillValues("com.bvblogic.nimbusnote", AppType.NOTES),
                new PrefillValues("com.networktechnologytutor.musicnotetutorfree", AppType.NOTES),
                new PrefillValues("com.mbile.notes", AppType.NOTES),
                new PrefillValues("com.appbody.handyNote.note.free.google", AppType.NOTES),
                new PrefillValues("ih.note", AppType.NOTES),
                new PrefillValues("com.breadusoft.punchmemo", AppType.NOTES),
                new PrefillValues("com.appple.app.email", AppType.EMAIL),
                new PrefillValues("com.clearhub.wl", AppType.EMAIL),
                new PrefillValues("com.cloudmagic.mail", AppType.EMAIL),
                new PrefillValues("com.fsck.k9", AppType.EMAIL),
                new PrefillValues("com.google.android.apps.inbox", AppType.EMAIL),
                new PrefillValues("com.google.android.gm", AppType.EMAIL),
                new PrefillValues("com.google.email", AppType.EMAIL),
                new PrefillValues("com.mail.emails", AppType.EMAIL),
                new PrefillValues("com.mail.mobile.android.mail", AppType.EMAIL),
                new PrefillValues("com.microsoft.office.outlook", AppType.EMAIL),
                new PrefillValues("com.my.mail", AppType.EMAIL),
                new PrefillValues("com.nhn.android.mail", AppType.EMAIL),
                new PrefillValues("com.nimrod.msntooutlook", AppType.EMAIL),
                new PrefillValues("com.syntomo.email", AppType.EMAIL),
                new PrefillValues("com.trtf.blue", AppType.EMAIL),
                new PrefillValues("com.yahoo.mobile.client.android.mail", AppType.EMAIL),
                new PrefillValues("com.zoho.mail", AppType.EMAIL),
                new PrefillValues("me.bluemail.mail", AppType.EMAIL),
                new PrefillValues("org.kman.AquaMail", AppType.EMAIL),
                new PrefillValues("ru.mail.mailapp", AppType.EMAIL),
                new PrefillValues("ru.yandex.mail", AppType.EMAIL),
                new PrefillValues("com.android.email", AppType.EMAIL),
                new PrefillValues("com.google.android.apps.messaging", AppType.SMS)
        };

        public MyDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        // Method is called during creation of the database
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(DATABASE_CREATE);

            String fixedPackage = "";
            try {
                fixedPackage = mCtx.getResources().getString(R.string.feature_package).trim();
            } catch (Resources.NotFoundException ex) {
                //Noop
            }

            PrefillValues[] preenabledPackages;
            if (fixedPackage.length() == 0) {
                //Main oversec build, enable all
                preenabledPackages = PREENABLED_PACKAGES;
            } else {

                preenabledPackages = new PrefillValues[1];
                for (PrefillValues v : PREENABLED_PACKAGES) {
                    if (v.packageName.equals(fixedPackage)) {
                        preenabledPackages[0] = v;
                    }
                }
            }

            for (PrefillValues v : preenabledPackages) {

                boolean enabled = !Issues.INSTANCE.hasSeriousIssues(v.packageName);

                try {
                    String lastEncMethod = EncryptionMethod.SYM.name();
                    String symCoderId = ZeroWidthXCoder.ID;
                    String symPadderId = mCtx.getString(R.string.padder_lorem);
                    String gpgCoderId = ZeroWidthXCoder.ID;
                    String gpgPadderId = mCtx.getString(R.string.padder_lorem);
                    int storeEncryptionParamsPerPackageOnly = 0;
                    int showDecryptedTextAboveInput = 1;
                    switch (v.appType) {
                        case CHAT:
                            lastEncMethod = EncryptionMethod.SYM.name();
                            symCoderId = ZeroWidthXCoder.ID;
                            symPadderId = mCtx.getString(R.string.padder_lorem);
                            break;
                        case EMAIL:
                            lastEncMethod = EncryptionMethod.GPG.name();
                            gpgCoderId = AsciiArmouredGpgXCoder.ID;
                            gpgPadderId = null;
                            break;
                        case NOTES:
                            lastEncMethod = EncryptionMethod.GPG.name();
                            gpgCoderId = AsciiArmouredGpgXCoder.ID;
                            gpgPadderId = null;
                            storeEncryptionParamsPerPackageOnly = 1;
                            showDecryptedTextAboveInput = 0;
                            break;
                        case SMS:
                            lastEncMethod = EncryptionMethod.SYM.name();
                            symCoderId = Base64XCoder.ID;
                            symPadderId = null;
                            break;

                    }

                    database.execSQL(String.format(DATABASE_PREFILL,
                            TABLE_PACKAGES,
                            COL_NAME,
                            COL_ENABLED,
                            COL_LAST_ENC_METHOD,
                            COL_XCODER_SYM,
                            COL_PADDER_SYM,
                            COL_XCODER_PGP,
                            COL_PADDER_PGP,
                            COL_SHOW_CAMERA_BUTTON,
                            COL_STORE_ENCRYPTION_PARAMS_PER_PACKAGE_ONLY,
                            COL_ABOVE,
                            v.packageName,
                            enabled ? 1 : 0,
                            lastEncMethod,
                            symCoderId,
                            symPadderId,
                            gpgCoderId,
                            gpgPadderId,
                            0,/* showCameraButton,  NOT showing camera button initially*/
                            storeEncryptionParamsPerPackageOnly,
                            showDecryptedTextAboveInput));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            updateAppsSpecial(database);

        }

        // Method is called during an upgrade of the database,
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion,
                              int newVersion) {
            if (BuildConfig.DEBUG) {
                Log.w(MyDatabaseHelper.class.getName(),
                        "Upgrading database from version " + oldVersion + " to "
                                + newVersion + ", which will destroy all old data");
                database.execSQL("DROP TABLE IF EXISTS " + TABLE_PACKAGES);
                onCreate(database);
            } else {
                if (oldVersion < 83) {
                    String update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_BUTTON_COLOR);
                    database.execSQL(update);
                }
                if (oldVersion < 84) {
                    String update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_BTDECRYPT_ANCHOR_H);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_BTDECRYPT_ANCHOR_V);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_BTENCRYPT_ANCHOR_H);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_BTENCRYPT_ANCHOR_V);
                    database.execSQL(update);

                }

                if (oldVersion < 85) {
                    String update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_COMPOSE_LANDSCAPE_X);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_COMPOSE_LANDSCAPE_Y);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_COMPOSE_PORTRAIT_X);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_COMPOSE_PORTRAIT_Y);
                    database.execSQL(update);
                    update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_SHOW_COMPOSE_BUTTON);
                    database.execSQL(update);


                }

                if (oldVersion < 86) {
                    String update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_SPREAD_INVISIBLE_ENCODING);
                    database.execSQL(update);
                }


                if (oldVersion < 85 && oldVersion != newVersion) {
                    updateAppsSpecial(database);
                }

                if (oldVersion < 86 && oldVersion != newVersion) {
                    updateAppsSpecial_SpreadInvisibleEncoding(database);
                }

                if (oldVersion < 87) {
                    String update = String
                            .format("alter table %s add column %s integer;)",
                                    TABLE_PACKAGES,
                                    COL_DONT_SHOW_DECRYPTION_FAILED);
                    database.execSQL(update);
                }
            }
        }

        private void updateAppsSpecial_SpreadInvisibleEncoding(SQLiteDatabase database) {


            for (String packagename : Issues.INSTANCE.getPackagesThatNeedSpreadInvisibleEncoding()) {
                try {
                    String update = String
                            .format("update %s set  %s =1 where %s = '%s' and %s is null",
                                    TABLE_PACKAGES,
                                    COL_SPREAD_INVISIBLE_ENCODING,
                                    COL_NAME,
                                    packagename,
                                    COL_SPREAD_INVISIBLE_ENCODING);
                    database.execSQL(update);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }

        }

        private void updateAppsSpecial(SQLiteDatabase database) {


            for (String packagename : Issues.INSTANCE.getPackagesThatNeedComposeButton()) {
                try {
                    String update = String
                            .format("update %s set  %s =1 where %s = '%s' and %s is null",
                                    TABLE_PACKAGES,
                                    COL_SHOW_COMPOSE_BUTTON,
                                    COL_NAME,
                                    packagename,
                                    COL_SHOW_COMPOSE_BUTTON);
                    database.execSQL(update);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }

            for (String packagename : Issues.INSTANCE.getPackagesThatNeedIncludeNonImportantViews()) {
                try {
                    String update = String
                            .format("update %s set  %s =1 where %s = '%s' and %s is null",
                                    TABLE_PACKAGES,
                                    COL_INCLUDE_NON_IMPORTANT_VIEWS,
                                    COL_NAME,
                                    packagename,
                                    COL_INCLUDE_NON_IMPORTANT_VIEWS);
                    database.execSQL(update);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private enum AppType {
        CHAT,
        EMAIL,
        SMS,
        NOTES
    }

    private static class PrefillValues {
        String packageName;
        AppType appType;

        public PrefillValues(String packageName, AppType appType) {
            this.packageName = packageName;
            this.appType = appType;
        }

        @Override
        public String toString() {
            return "PrefillValues{" +
                    ", packageName='" + packageName + '\'' +
                    ", appType=" + appType +
                    '}';
        }
    }

    private Boolean mIsShowKnownIssuesTooltips;

    public boolean isShowKnownIssuesTooltips() {
        //don't show knwon issues tooltips for legacy users!
        if (mIsShowKnownIssuesTooltips != null) {
            return mIsShowKnownIssuesTooltips;
        }

        Long installDate = getInstallDate();
        if (installDate != null && installDate > 1473372000000L /*09 Sep 2016*/) {
            mIsShowKnownIssuesTooltips = true;
        } else {
            mIsShowKnownIssuesTooltips = false;
        }
        return mIsShowKnownIssuesTooltips;

    }

}
