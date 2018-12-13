package io.oversec.one;

import io.oversec.one.acs.Tree;

import io.oversec.one.crypto.AbstractEncryptionParams;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.CryptoHandlerFacade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import roboguice.util.Ln;

class UiThreadVars {

    private final Core mCore;

    private String mCurrentPackageName;

    //TODO consolidate those and possibly more to a single map with package specific information
    private Map<String, Boolean> mIsTemporaryHiddenMap = new HashMap<>();
    private Map<String, Integer> mLastDisplayTreeRootNodeIdMap = new HashMap<>();
    private Map<String, BaseDecryptResult> mLastScrapedDecryptResultMap = new HashMap<>();
    private Map<String, EncryptionParamsCache> mEncryptionParamsCacheMap = new HashMap<>();
    private Map<String, AbstractEncryptionParams> mLastSavedUserSelectedEncryptionParamsMap = new HashMap<>();
    private Map<String, CharSequence> mLastWindowStateChangedClassNameMap = new HashMap<>();
    private List<String> mCurrentlyDisplayedEncodedStrings = new ArrayList<>();

    private int mLastOrientation = 0;

    UiThreadVars(Core core) {
        mCore = core;
        mLastOrientation = core.getCtx().getResources().getConfiguration().orientation;
    }

    String getCurrentPackageName() {
        // mCore.checkUiThread();
        return mCurrentPackageName;
    }

    void setCurrentPackageName(String s) {
        mCore.checkUiThread();
        mCurrentPackageName = s;
    }

    int getLastOrientation() {
        mCore.checkUiThread();

        return mLastOrientation;
    }

    void setLastOrientation(int o) {
        mCore.checkUiThread();
        mLastOrientation = 0;
    }

    void setTemporaryHidden(String packageName, Boolean hide) {
        mCore.checkUiThread();

        mIsTemporaryHiddenMap.put(packageName, hide);
    }

    Boolean isTemporaryHidden(String packageName) {
        // mCore.checkUiThread();
        Boolean r = mIsTemporaryHiddenMap.get(packageName);
        return r;
    }

    Integer getLastDisplayTreeRootNodeId(String packageName) {
        mCore.checkUiThread();

        return mLastDisplayTreeRootNodeIdMap.get(packageName);
    }

    void setLastDIsplayTreeRootNodeId(String packageName, int id) {
        mCore.checkUiThread();

        mLastDisplayTreeRootNodeIdMap.put(packageName, id);
    }

    public void setLastScrapedDecryptResult(BaseDecryptResult r, String packageName) {
        mLastScrapedDecryptResultMap.put(packageName, r);
    }

    public void setUserSelectedEncryptionParams(String packagename, AbstractEncryptionParams params, String encodedText) {
        mLastSavedUserSelectedEncryptionParamsMap.put(packagename, params);
        EncryptionParamsCache epc = getEncryptionParamsCache(packagename);
        epc.saveEncryptionParams(encodedText, params);
    }


    public AbstractEncryptionParams getLastSavedUserSelectedEncryptionParams(String packagename) {
        return mLastSavedUserSelectedEncryptionParamsMap.get(packagename);
    }


    public synchronized AbstractEncryptionParams getUserSelectedEncryptionParamsX(String packagename) {
        if (mCore.getDb().isStoreEncryptionParamsPerPackageOnly(packagename)) {
            return getLastSavedUserSelectedEncryptionParams(packagename);
        } else {
            AbstractEncryptionParams r = getUserSelectedEncryptionParamsFromCurrentContent(packagename);
            if (r == null) {
                //fallback
                r = getLastSavedUserSelectedEncryptionParams(packagename);
            }
            return r;
        }

    }

    public synchronized AbstractEncryptionParams getUserSelectedEncryptionParamsFromCurrentContent(String packagename) {

        EncryptionParamsCache epc = getEncryptionParamsCache(packagename);
        AbstractEncryptionParams r = null;

        ListIterator<String> li = mCurrentlyDisplayedEncodedStrings.listIterator(mCurrentlyDisplayedEncodedStrings.size());

        // Iterate in reverse, i.e. somehow from bottom of screen
        while (li.hasPrevious()) {
            String s = li.previous();
            AbstractEncryptionParams params = epc.getEncryptionParams(s);
            if (params != null) {
                r = params;
                break;
            }
        }

        return r;
    }

    private void findAllEncodedTexts(Tree.TreeNode node, List<String> res) {
        if (node.isTextNode()) {
            if (CryptoHandlerFacade.Companion.isEncoded(mCore.getCtx(), node.getOrigTextString())) {
                res.add(node.getOrigTextString());
            }
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                findAllEncodedTexts(node.getChildAt(i), res);
            }
        }

    }

    public boolean hasUserSelectedEncryptionParams(String packagename) {
        mCore.checkUiThread();
        return getUserSelectedEncryptionParamsX(packagename) != null;
    }


    public AbstractEncryptionParams getBestEncryptionParams(String packageName) {

        AbstractEncryptionParams r = getUserSelectedEncryptionParamsX(packageName);
        if (r == null) {
            BaseDecryptResult tdr = mLastScrapedDecryptResultMap.get(packageName);
            if (tdr != null) {
                r = CryptoHandlerFacade.Companion.getInstance(mCore.getCtx()).getCryptoHandler(tdr).buildDefaultEncryptionParams(tdr);
            }
        }
        return r;
    }


    public synchronized void setDisplayRoot(Tree.TreeNode root) {
        mCurrentlyDisplayedEncodedStrings.clear();
        if (root != null) {
            findAllEncodedTexts(root, mCurrentlyDisplayedEncodedStrings);
        }
    }


    private synchronized EncryptionParamsCache getEncryptionParamsCache(String packagename) {
        EncryptionParamsCache r = mEncryptionParamsCacheMap.get(packagename);
        if (r == null) {
            r = new EncryptionParamsCache();
            mEncryptionParamsCacheMap.put(packagename, r);
        }
        return r;
    }

    public void setLastSavedUserSelectedEncryptionParams(AbstractEncryptionParams encryptionParams, String packagename) {
        mLastSavedUserSelectedEncryptionParamsMap.put(packagename, encryptionParams);
    }

    public void setLastWindowStateChangedClassName(CharSequence className, String packagename) {
        mCore.checkUiThread();
        mLastWindowStateChangedClassNameMap.put(packagename, className);
    }


    public CharSequence getLastWindowStateChangedClassName(String packageName) {
        mCore.checkUiThread();
        return mLastWindowStateChangedClassNameMap.get(packageName);
    }

    class EncryptionParamsCache {

        private static final int MAX_ENTRIES = 200;
        private Map<String, AbstractEncryptionParams> mEncryptionParamsMap = new LinkedHashMap<String, AbstractEncryptionParams>() {
            @Override
            protected boolean removeEldestEntry(Entry<String, AbstractEncryptionParams> eldest) {
                return size() > MAX_ENTRIES;
            }
        };

        public AbstractEncryptionParams getEncryptionParams(String encodedText) {
            AbstractEncryptionParams r = mEncryptionParamsMap.get(encodedText);
            return r;
        }

        public void saveEncryptionParams(String encodedText, AbstractEncryptionParams params) {
            mEncryptionParamsMap.put(encodedText, params);
        }
    }

    static String se(String s) {
        if (s.length() <= 8) {
            return s;
        } else {
            return s.substring(s.length() - 8);
        }
    }
}