package io.oversec.one;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.LruCache;

import io.oversec.one.common.MainPreferences;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.DoDecryptHandler;
import io.oversec.one.crypto.gpg.OpenKeychainConnector;
import io.oversec.one.crypto.proto.Outer;
import io.oversec.one.ovl.NodeTextView;
import roboguice.util.Ln;

import java.util.Map;

public class EncryptionCache {

    //even though this cache is cleared when the users switches apps,
    //in case he stays in the same app for an extended amount of time, this cache
    //might fill up beyond resource availability, that's why we use an LRU cache instead of a simple map

    private final CryptoHandlerFacade mCryptoHandlerFacade;
    private final Context mCtx;
    private LruCache<String, TDROrFlag> mOrigTextToResultMap = new LruCache<>(100);

    public EncryptionCache(Context ctx, CryptoHandlerFacade cryptoHandlerFacade) {
        mCtx = ctx;
        mCryptoHandlerFacade = cryptoHandlerFacade;


        BroadcastReceiver aIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    //clear the cache on screen off, in case keys are cleared in OKC on screen off
                    // we need to immediately clear everything we have..
                    clear(CLEAR_REASON.SCREEN_OFF, null);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        ctx.registerReceiver(aIntentReceiver, filter);
    }

    public BaseDecryptResult get(String encText) {
        TDROrFlag v = mOrigTextToResultMap.get(encText);
        return v == null ? null : v.tdr;
    }

    private class TDROrFlag {
        BaseDecryptResult tdr;
        boolean isUserInteractionRequired;

        public TDROrFlag(BaseDecryptResult tdr) {
            this.tdr = tdr;
        }

        public TDROrFlag(boolean isUserInteractionRequired) {
            this.isUserInteractionRequired = isUserInteractionRequired;
        }
    }

    public synchronized boolean has(String encText) {
        TDROrFlag t = mOrigTextToResultMap.get(encText);

        Ln.d("EC: has..." + (t != null && t.tdr != null));
        return t != null && t.tdr != null;
    }

    public synchronized void put(String encText, BaseDecryptResult decryptResult) {
        Ln.d("EC: put....");
        mOrigTextToResultMap.put(encText, new TDROrFlag(decryptResult));
    }

    public synchronized void decryptMaybeAsync(String packageName, final String encText, Outer.Msg encodedData, final NodeTextView.TextViewDoDecryptHandler textViewDoDecryptHandler) {
        Ln.d("EC: decryptMaybeAsync...");
        TDROrFlag r = mOrigTextToResultMap.get(encText);
        if (r == null) {
            Ln.d("EC: decryptMaybeAsync...   async:");
            mCryptoHandlerFacade.decryptAsync(packageName, encodedData, new DoDecryptHandler() {
                @Override
                public void onResult(BaseDecryptResult tdr) {
                    Ln.d("EC: decryptMaybeAsync...  asynccompleted    onResult");
                    mOrigTextToResultMap.put(encText, new TDROrFlag(tdr));
                    textViewDoDecryptHandler.onResult(tdr);
                }

                @Override
                public void onUserInteractionRequired() {
                    Ln.d("EC: decryptMaybeAsync...  asynccompleted    onUserInteractionRequired");
                    mOrigTextToResultMap.put(encText, new TDROrFlag(true));
                    textViewDoDecryptHandler.onUserInteractionRequired();
                }

            }, encText);
        } else {
            Ln.d("EC: decryptMaybeAsync...   cache hit:");
            if (r.tdr != null) {
                Ln.d("EC: decryptMaybeAsync...   cache hit  onResult...");
                textViewDoDecryptHandler.onResult(r.tdr);
            } else if (r.isUserInteractionRequired) {
                textViewDoDecryptHandler.onUserInteractionRequired();
                Ln.d("EC: decryptMaybeAsync...   cache hit  onUserInteractionRequired...");
            }
        }
    }

    public synchronized void clear(CLEAR_REASON reason, String extra) {
        Ln.d("EC:  CLEAR   reason=" + reason + "  extra=" + extra);
        boolean relaxedCacheHandling = MainPreferences.INSTANCE.isRelaxEncryptionCache(mCtx);

        clearEntriesWithUserInteractionRequired();

        switch (reason) {
            case TREE_ROOT_CHANGED:
            case EMPTY_TREE:
            case INFOMODE_LEFT:
                if (relaxedCacheHandling) {
                    return;
                }
            case PACKAGE_CHANGED:
                if (mCtx.getPackageName().equals(extra)) {
                    return; //Oversec dialog, don't clear
                } else if (OpenKeychainConnector.PACKAGE_NAME.equals(extra)) {

                    return; //OpenKeyChain dialog
                } else if (Core.PACKAGE_SYTEMUI.equals(extra)) {
                    //user might have tapped on "clear cached passwords" in the Openkeychain notification
                    //since we don't have a callback for this event from Openkeychain
                    //we need to clear the cache everytime the Systemui is shown
                    break; //-> clear()
                }

                if (relaxedCacheHandling) {
                    return;
                }
            case CLEAR_CACHED_KEYS:
            case OVERSEC_HIDDEN:
            case SCREEN_OFF:
            case PANIC:
            default:
        }

        Ln.d("EC:  CLEAR REALLY");
        clear();
    }

    private synchronized void clearEntriesWithUserInteractionRequired() {
        for (Map.Entry<String, TDROrFlag> entry : mOrigTextToResultMap.snapshot().entrySet()) {
            if (entry.getValue().isUserInteractionRequired) {
                mOrigTextToResultMap.remove(entry.getKey());
            }
        }
    }

    private void clear() {
        mOrigTextToResultMap.evictAll();
    }

    public enum CLEAR_REASON {
        TREE_ROOT_CHANGED, EMPTY_TREE, PACKAGE_CHANGED, INFOMODE_LEFT, SCREEN_OFF, PANIC, CLEAR_CACHED_KEYS, OVERSEC_HIDDEN
    }
}
