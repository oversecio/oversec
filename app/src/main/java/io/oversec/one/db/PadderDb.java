package io.oversec.one.db;

import android.content.Context;

import io.oversec.one.crypto.R;
import io.oversec.one.crypto.encoding.pad.PadderContent;

import net.rehacktive.waspdb.WaspDb;
import net.rehacktive.waspdb.WaspFactory;
import net.rehacktive.waspdb.WaspHash;

import java.util.Collections;
import java.util.List;

public class PadderDb {
    private static final String DATABASE_NAME = "padder4";
    private static PadderDb mInstance;

    private final WaspDb mDb;
    private final WaspHash mPadderContent;
    private final Context mCtx;

    public static synchronized PadderDb getInstance(Context ctx) {
        if (mInstance == null) {
            mInstance = new PadderDb(ctx.getApplicationContext());
        }
        return mInstance;
    }

    private PadderDb(Context ctx) {
        mCtx = ctx;
        mDb = WaspFactory.openOrCreateDatabase(ctx.getFilesDir().getPath(), DATABASE_NAME, null);
        boolean prefill = false;
        if (!mDb.existsHash("padder_content4")) {
            prefill = true;
        }
        mPadderContent = mDb.openOrCreateHash("padder_content4");
        if (prefill) {
            prefill();
        }
    }

    private void prefill() {
        add(new PadderContent("aa", mCtx.getString(R.string.padder_lorem), mCtx.getString(R.string.paddercontent_lorem)));
        add(new PadderContent("ab", mCtx.getString(R.string.padder_chicken), mCtx.getString(R.string.paddercontent_chicken)));
        add(new PadderContent("ab", mCtx.getString(R.string.padder_hansel_and_gretel), mCtx.getString(R.string.paddercontent_hansel_and_gretel)));
    }

    public List<PadderContent> getAllValues() {
        List<PadderContent> v = mPadderContent.getAllValues();
        if (v != null) {
            Collections.sort(v, PadderContent.Companion.getSortComparator());
        } else {
            return Collections.EMPTY_LIST;
        }

        return v;
    }

    public void delete(Long key) {
        mPadderContent.remove(key);
    }

    public void add(PadderContent content) {
        mPadderContent.put(content.getKey(), content);
    }


    public PadderContent get(Long id) {
        return mPadderContent.get(id);
    }

    public void update(PadderContent pc) {
        mPadderContent.put(pc.getKey(), pc);
    }
}
