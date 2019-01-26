package io.oversec.one.acs;

import android.content.Context;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import io.oversec.one.acs.util.AccessibilityNodeInfoUtils;
import io.oversec.one.acs.util.SynchronizedPool;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.gpg.GpgCryptoHandler;
import io.oversec.one.crypto.proto.Outer;
import io.oversec.one.iab.Base64;
import io.oversec.one.iab.Base64DecoderException;
import roboguice.util.Ln;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the view tree (for performance reason, so that we do not have to query the whole tree through ACS every time something changes)
 */
public class Tree {

    private Context mCtx;

    private Map<Integer, TreeNode> mMap = new HashMap<>();

    private TreeNode mRootNode;


    public Tree(Context ctx) {
        mCtx = ctx;
    }


    public TreeNode get(int key) {
        return mMap.get(key);
    }

    public TreeNode get(AccessibilityNodeInfo node) {
        return get(getKey(node));
    }

    private int getKey(AccessibilityNodeInfo node) {
        return node.hashCode();
    }


    public TreeNode put(AccessibilityNodeInfo node) {
        TreeNode treeNode = obtain(node);
        mMap.put(getKey(node), treeNode);
        return treeNode;
    }


    public void removeSubtree(TreeNode node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            removeSubtreeSub(node.getChildAt(i));

        }
        node.mChildren.clear();
    }

    private void removeSubtreeSub(TreeNode node) {

        for (int i = 0; i < node.getChildCount(); i++) {
            removeSubtreeSub(node.getChildAt(i));
        }
        mMap.remove(node.getKey());
        node.recycle(false);


    }


    public void addRootNode(AccessibilityNodeInfo rootNode) {
        mRootNode = put(rootNode);
    }


    public void clear() {
        if (mRootNode != null) {
            mRootNode.recycle(true);
        }
        mRootNode = null;
        mMap.clear();
    }

    public TreeNode obtainDisplayTree() {
        TreeNode res = obtainForDisplay(mRootNode, false);
        if (res == null) {
            res = obtain(mRootNode, false);
        }
        return res;
    }


    private Map<String, Boolean> mClassesThatAreListOrScrollViews = new HashMap<>();

    private boolean isAListOrScrollView(AccessibilityNodeInfo node) {
        CharSequence classname = node.getClassName();
        if (classname == null) {
            //ouch. Sometimes happens when scanning a webview
            return false;
        }
        Boolean res = mClassesThatAreListOrScrollViews.get(classname.toString());
        if (res == null) {
            res = AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(mCtx,
                    node, ListView.class, ScrollView.class);
            if (res) {
                mClassesThatAreListOrScrollViews.put(node.getClassName().toString(), res);
            }
        }
        return res;
    }


    private Map<String, Boolean> mClassesThatAreEditTexts = new HashMap<>();

    public boolean isAWebView(AccessibilityNodeInfo node) {
        CharSequence classname = node.getClassName();
        if (classname == null) {
            //ouch. Sometimes happens when scanning a webview
            return false;
        }
        //TODO: might be different in older releases without chrome?
        return ("android.webkit.WebView".equals(classname.toString())); //TODO: toString really needed?
    }

    public boolean isAEditText(AccessibilityNodeInfo node) {
        CharSequence classname = node.getClassName();
        if (classname == null) {
            //ouch. Sometimes happens when scanning a webview
            return false;
        }
        Boolean res = mClassesThatAreEditTexts.get(classname.toString());
        if (res == null) {
            res = AccessibilityNodeInfoUtils.nodeMatchesClassByType(mCtx, node,
                    EditText.class);
            if (res) {
                mClassesThatAreEditTexts.put(node.getClassName().toString(), res);
            }
        }
        return res;
    }


    private Map<String, Boolean> mClassesThatAreTextViews = new HashMap<>();

    public boolean isATextView(AccessibilityNodeInfo node) {
        CharSequence classname = node.getClassName();
        if (classname == null) {
            //ouch. Sometimes happens when scanning a webview
            return false;
        }
        Boolean res = mClassesThatAreTextViews.get(classname.toString());
        if (res == null) {
            res = AccessibilityNodeInfoUtils.nodeMatchesClassByType(mCtx, node,
                    TextView.class);
            if (res) {
                mClassesThatAreTextViews.put(node.getClassName().toString(), res);
            }
        }
        return res;
    }

    private static SynchronizedPool<TreeNode> mPool = new SynchronizedPool<>(200);


    private TreeNode obtain(AccessibilityNodeInfo node) {
        TreeNode res = mPool.acquire();
        if (res == null) {
            res = new TreeNode();
        }
        res.sealed = false;
        if (Ln.isDebugEnabled()) {
            dumpTreeNodePool("obtain B " + super.toString());
        }

        res.init(node);
        return res;
    }

    private TreeNode obtain(TreeNode from, boolean copyChildren) {
        TreeNode res = mPool.acquire();
        if (res == null) {
            res = new TreeNode();
        }
        res.sealed = false;
        if (Ln.isDebugEnabled()) {
            dumpTreeNodePool("obtain A " + super.toString());
        }
        res.copy(from, copyChildren);

        return res;
    }

    public TreeNode obtainForDisplay(TreeNode node, boolean isInWebView) {
        TreeNode res = null;
        if (node.mIsWebView && !isInWebView) { //for some reason webviews are occasionally nested!
            isInWebView = true;
            res = obtain(node, false);
            node.processWebView(res.mChildren);

        }

        if (node.mIsVisibleToUser && (
                (node.isEncoded() /*&& parent.isTextNode()*/)
                        || node.mIsEditableTextNode
                /*WebViewXyzzy: Enable to detect WebView input || (isInWebView && node.isFocused()) */
        )) {
            if (res == null) {
                res = obtain(node, false);
                if (isInWebView) {
                    res.mangleWebViewChild();
                }
            }
        } else {
            for (TreeNode fromChild : node.mChildren) {
                TreeNode subTree = obtainForDisplay(fromChild, isInWebView);
                if (subTree != null) {
                    if (res == null) {
                        res = obtain(node, false);
                    }
                    res.mChildren.add(subTree);
                }
            }

        }

        return res;
    }

    final static String BEGIN_PGP_MESSAGE = "-----BEGIN PGP MESSAGE-----";
    final static String END_PGP_MESSAGE = "-----END PGP MESSAGE-----";

    private final class AsciiArmorFinderState {
        public boolean armorCompleted;
        public Rect rectInParent = new Rect();
        public Rect rectInScreen = new Rect();
        public StringBuilder sb = new StringBuilder();
        public TreeNode firstNodeOfAsciiArmor;
        private boolean mIsAtLeastOneNodeVisibleTouser;

        public boolean isAtLeastOneNodeVisibleTouser() {
            return mIsAtLeastOneNodeVisibleTouser;
        }

        public void recordVisibility(TreeNode root) {
            mIsAtLeastOneNodeVisibleTouser = mIsAtLeastOneNodeVisibleTouser | root.mIsVisibleToUser;
        }
    }

    private final class Base64FinderState {
        public Rect rectInParent = new Rect();
        public Rect rectInScreen = new Rect();
        public StringBuilder sb = new StringBuilder();
        public TreeNode firstNodeOfBase64;
        private boolean mIsAtLeastOneNodeVisibleTouser;

        public boolean isAtLeastOneNodeVisibleTouser() {
            return mIsAtLeastOneNodeVisibleTouser;
        }

        public void recordVisibility(TreeNode root) {
            mIsAtLeastOneNodeVisibleTouser = mIsAtLeastOneNodeVisibleTouser | root.mIsVisibleToUser;
        }
    }

    Base64FinderState mB64State;

    private void findBase64(TreeNode root, Base64FoundCallback cb) {
        mB64State = null;
        findBase64_int(root, cb);
        mB64State = null;
    }

    private void findBase64_int(TreeNode root, Base64FoundCallback cb) {

        String ownText = root.mTextString;
        if (ownText != null) {
            ownText = ownText.trim();
            if (Base64.isProbablyBase64Encoded(ownText)) {
                try {
                    Base64.decode(ownText);
                    mB64State = new Base64FinderState();
                    mB64State.firstNodeOfBase64 = root;
                    mB64State.rectInParent.set(root.mBoundsInParent);
                    mB64State.rectInScreen.set(root.mBoundsInScreen);
                    mB64State.sb.append(ownText);
                    mB64State.recordVisibility(root);
                    cb.onBase64Found(mB64State);
                    mB64State = null;
                } catch (Base64DecoderException e) {
                    //e.printStackTrace();

                }
            }


        } else { // own text is null
            if (mB64State != null) {
                mB64State.sb.append("\n");
                mB64State.recordVisibility(root);
            }

            for (TreeNode child : root.mChildren) {
                findBase64_int(child, cb);

            }
        }


    }

    private AsciiArmorFinderState mAaState;

    private void findPgpAsciiArmor(TreeNode root, PgpAsciiArmorFoundCallback cb) {
        mAaState = null;
        findPgpAsciiArmor_int(root, cb);
        mAaState = null;
    }

    private void findPgpAsciiArmor_int(TreeNode root, PgpAsciiArmorFoundCallback cb) {

        String ownText = root.mTextString;
        if (ownText != null) {
            if (ownText.startsWith(BEGIN_PGP_MESSAGE)) {
                mAaState = new AsciiArmorFinderState();
                mAaState.firstNodeOfAsciiArmor = root;
                mAaState.rectInParent.set(root.mBoundsInParent);
                mAaState.rectInScreen.set(root.mBoundsInScreen);
                mAaState.sb.append(ownText);
                mAaState.recordVisibility(root);
            } else if (ownText.contains(END_PGP_MESSAGE)) {
                if (mAaState != null) {
                    mAaState.armorCompleted = true;
                    mAaState.rectInParent.union(root.mBoundsInParent);
                    mAaState.rectInScreen.union(root.mBoundsInScreen);

                    mAaState.sb.append(ownText);
                    mAaState.recordVisibility(root);
                    cb.onAsciiArmorFound(mAaState);
                    mAaState = null;
                }
            } else {
                if (mAaState != null) {
                    if (mAaState.sb.length() > 0 && mAaState.sb.charAt(mAaState.sb.length() - 1) != '\n') {
                        mAaState.sb.append("\n");
                    }
                    mAaState.sb.append(ownText);
                    mAaState.recordVisibility(root);
                    mAaState.rectInParent.union(root.mBoundsInParent);
                    mAaState.rectInScreen.union(root.mBoundsInScreen);
                }
            }

        } else { // own text is null
            if (mAaState != null) {
                mAaState.sb.append("\n");
                mAaState.recordVisibility(root);
            }
        }

        if (mAaState == null || !mAaState.armorCompleted) {
            for (TreeNode child : root.mChildren) {
                findPgpAsciiArmor_int(child, cb);

            }
        }
    }

    public boolean isEmpty() {
        return mRootNode == null;
    }

    public TreeNode getRoot() {
        return mRootNode;
    }

    public CharSequence getFirstEncodedChildText(AccessibilityNodeInfo n) {
        TreeNode tn = get(n.hashCode());
        if (tn != null) {
            return tn.getFirstEncodedChildText();
        }
        return null;
    }

    public class TreeNode {
        private boolean sealed;

        private CharSequence mClassName;
        private CharSequence mPackageName;

        private boolean mIsEditText;
        private boolean mIsTextView;
        private boolean mIsWebView;
        private boolean mIsEncoded;
        private boolean mIsVisibleToUser;
        private Integer mKey;
        private CharSequence mText;
        private String mTextString;
        private Rect mBoundsInScreen;
        private Rect mBoundsInParent;


        private boolean mIsEditableTextNode;
        private boolean mFocused;

        private ArrayList<TreeNode> mChildren = new ArrayList<>(20);


        public void copy(TreeNode from, boolean copyChildren) {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }
            if (from.sealed) {
                throw new IllegalArgumentException("from is sealed! " + super.toString());
            }

            mClassName = from.mClassName;
            mPackageName = from.mPackageName;
            mIsEditText = from.mIsEditText;
            mIsTextView = from.mIsTextView;
            mIsWebView = from.mIsWebView;
            mKey = from.mKey;
            mText = from.mText;
            mTextString = from.mTextString;
            mBoundsInScreen = from.mBoundsInScreen;
            mBoundsInParent = from.mBoundsInParent;
            mIsEditableTextNode = from.mIsEditableTextNode;
            mIsEncoded = from.mIsEncoded;
            mFocused = from.mFocused;
            mIsVisibleToUser = from.mIsVisibleToUser;
            if (copyChildren) {
                for (TreeNode fromChild : from.mChildren) {
                    mChildren.add(obtain(fromChild, true));
                }
            }
        }

        private TreeNode() {

        }

        public void recycle(boolean recycleChildren) {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            sealed = true;

            mClassName = null;
            mPackageName = null;
            mIsEditText = false;
            mIsTextView = false;
            mIsWebView = false;
            mIsEncoded = false;
            mKey = null;
            mText = null;
            mTextString = null;
            mBoundsInScreen = null;
            mBoundsInParent = null;
            mIsEditableTextNode = false;
            mIsEncoded = false;
            mFocused = false;
            mIsVisibleToUser = false;
            if (recycleChildren) {
                for (TreeNode child : mChildren) {
                    child.recycle(true);
                }
            }
            mChildren.clear();
            mPool.release(this);

            dumpTreeNodePool("recycle");
        }


        public void init(AccessibilityNodeInfo node) {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            mKey = node.hashCode();
            mClassName = node.getClassName();
            mPackageName = node.getPackageName();
            mIsTextView = isATextView(node);
            mIsEditText = isAEditText(node);
            mIsWebView = isAWebView(node);
            mBoundsInScreen = new Rect();
            mBoundsInParent = new Rect();
            mIsVisibleToUser = node.isVisibleToUser();
            refresh(node);

        }

        public void refresh(AccessibilityNodeInfo node) {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            mFocused = node.isFocused();
            node.getBoundsInScreen(mBoundsInScreen);
            node.getBoundsInParent(mBoundsInParent);


            mText = AccessibilityNodeInfoUtils.getNodeText(node);
            mTextString = mText == null ? null : mText.toString();

            mIsEncoded = mText == null ? false : CryptoHandlerFacade.Companion.isEncoded(mCtx, mTextString);
            if (mIsEncoded) {
                mIsTextView = true;
            }
            mIsEditableTextNode = (node.isEditable()
                    && node.isEnabled()
                    && mIsEditText
                    && !node.isPassword());
        }


        private boolean equalsIncludingNull(CharSequence t1, CharSequence t2) {
            if (t1 == null && t2 == null) return true;
            if (t1 != null && t2 == null) return false;
            if (t1 == null && t2 != null) return false;
            return (t1.equals(t2));
        }

        public void clearChildren() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            mChildren.clear();
        }

        public void addChild(TreeNode childTreeNode) {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            mChildren.add(childTreeNode);
        }


        public Integer getKey() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mKey;
        }

        public CharSequence getClassName() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mClassName;
        }

        public int getChildCount() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mChildren.size();
        }

        public TreeNode getChildAt(int i) {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mChildren.get(i);
        }


//        public boolean isDisplayInOverlay() {
//            if (sealed) {
//                throw new IllegalArgumentException("sealed! " + super.toString());
//            }
//
//
//            boolean res = ( isEncoded()) || mIsEditableTextNode;
//            if (!res) {
//                //check children
//                for (TreeNode child : mChildren) {
//                    if (child.isDisplayInOverlay()) {
//                        res = true;
//                        break;
//                    }
//                }
//            }
//            return res;
//        }


        public boolean isEditableEditText() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mIsEditableTextNode;
        }


        public Rect getBoundsInScreen() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }
            return mBoundsInScreen;
        }


        public CharSequence getOrigText() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }
            return mText;
        }

        public String getOrigTextString() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }
            return mTextString;
        }


        @Override
        public String toString() {
            return "TreeNode{" +
                    "mClassName=" + mClassName +
                    ", mKey=" + mKey +
                    ", mIsEditText=" + mIsEditText +
                    ", mIsTextView=" + mIsTextView +
                    ", mIsEncoded=" + mIsEncoded +
                    ", mIsFocused=" + mFocused +
                    ", mText=" + mText +
                    ", mBoundsInScreen=" + mBoundsInScreen +
                    ", mIsEditableTextNode=" + mIsEditableTextNode +
                    ", mNumChildren=" + mChildren.size() +
                    '}';
        }


        public void dump(StringBuffer sb, String p) {
            sb.append(p);
            sb.append(mKey);
            sb.append("\t");
            sb.append("mIsVisibleToUser=");
            sb.append(mIsVisibleToUser);
            sb.append("\t");
            sb.append(mClassName);
            sb.append("\t");
            sb.append(mBoundsInScreen);
            sb.append("\t");
            sb.append(mBoundsInParent);
            sb.append("\tisEncoded: ");
            sb.append(mIsEncoded);
            sb.append("\tisFocused: ");
            sb.append(mFocused);
            sb.append("\tisTextView: ");
            sb.append(mIsTextView);
            sb.append("\t");
            String s = mText == null ? "NULL" : mText.toString();
//            if (s.length() > 50) {
//                s = s.substring(s.length() - 50, s.length() - 1);
//            }
            sb.append("[[");
            sb.append(s);
            sb.append("]]");
            Ln.d("DUMP: %s", sb);
            sb.setLength(0);


            for (TreeNode c : mChildren) {
                c.dump(sb, p + "    ");
            }
            if (mChildren.size() > 0) {
                sb.append(p);
                sb.append("/ ");
                sb.append(mKey);
                sb.append("\t");
                sb.append(mClassName);
                Ln.d("DUMP: %s", sb);
                sb.setLength(0);

            }
        }

        public boolean isFocused() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mFocused;
        }

        public Rect getBoundsInParent() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mBoundsInParent;
        }

        public boolean isTextNode() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }


            return mIsTextView;
        }


        public boolean isEncoded() {
            if (sealed) {
                throw new IllegalArgumentException("sealed! " + super.toString());
            }

            return mIsEncoded;
        }


        public CharSequence getPackageName() {
            return mPackageName;
        }

        public String getPackageNameS() {
            return mPackageName.toString();
        }

        public boolean isSealed() {
            return sealed;
        }

        public CharSequence getFirstEncodedChildText() {
            if (sealed) {
                Ln.w("getFirstEncodedChildText : SEALED!");
                return null;
            }
            try {
                for (TreeNode cn : mChildren) {
                    if (cn.mTextString != null) {
                        Outer.Msg cs = CryptoHandlerFacade.Companion.getEncodedData(mCtx, cn.mTextString);
                        if (cs != null) {
                            return cn.mText;
                        }
                    }
                    CharSequence xx = cn.getFirstEncodedChildText();
                    if (xx != null) {
                        return xx;
                    }
                }

            } catch (Exception ex) {
                //not really well tested in terms of concurrency, so for now just catch all!
                Ln.w(ex);
            }
            return null;
        }


        public TreeNode processWebView(final ArrayList<TreeNode> children) {
            TreeNode res = null;


            final boolean[] found = new boolean[1];

            // TreeNode firstNodeOfAsciiArmor = findPgpAsciiArmor(this, sb, rectInParent, rectInScreen, new AsciiArmorFinderState());
            findPgpAsciiArmor(this, new PgpAsciiArmorFoundCallback() {
                @Override
                public void onAsciiArmorFound(AsciiArmorFinderState state) {
                    String pgpAsciiArmor = state.sb.toString();
                    if (pgpAsciiArmor.length() > 0) {
                        pgpAsciiArmor = GpgCryptoHandler.Companion.sanitizeAsciiArmor(pgpAsciiArmor);
                    }

                    if (pgpAsciiArmor != null && pgpAsciiArmor.length() > 0) {

                        TreeNode anode = obtain(state.firstNodeOfAsciiArmor, false);

                        //bounds in parent reported wrong from webView, so we somehow reconstruct them
                        state.rectInParent.offsetTo(state.rectInScreen.left, state.rectInScreen.top);
                        state.rectInParent.set(state.rectInScreen.left, state.rectInScreen.top, Integer.MAX_VALUE, state.rectInScreen.top + state.rectInParent.height());
                        boolean i = state.rectInParent.intersect(state.rectInScreen);
                        state.rectInParent.offsetTo(0, 0);

                        anode.mBoundsInParent = state.rectInParent;
                        anode.mBoundsInScreen = state.rectInScreen;
                        anode.mText = pgpAsciiArmor;
                        anode.mTextString = pgpAsciiArmor;
                        anode.mIsEncoded = true;
                        anode.mIsTextView = true;


                        if (state.isAtLeastOneNodeVisibleTouser() /* && parent.mIsVisibleToUser*/) {
                            children.add(anode);
                        }

                        found[0] = true;
                    }
                }
            });

            if (!found[0]) {

                findBase64(this, new Base64FoundCallback() {
                    @Override
                    public void onBase64Found(Base64FinderState state) {
                        String base64 = state.sb.toString();


                        if (base64 != null && base64.length() > 0) {

                            TreeNode anode = obtain(state.firstNodeOfBase64, false);

                            //bounds in parent reported wrong from webView, so we somehow reconstruct them
                            state.rectInParent.offsetTo(state.rectInScreen.left, state.rectInScreen.top);
                            state.rectInParent.set(state.rectInScreen.left, state.rectInScreen.top, Integer.MAX_VALUE, state.rectInScreen.top + state.rectInParent.height());
                            boolean i = state.rectInParent.intersect(state.rectInScreen);
                            state.rectInParent.offsetTo(0, 0);

                            anode.mBoundsInParent = state.rectInParent;
                            anode.mBoundsInScreen = state.rectInScreen;
                            anode.mText = base64;
                            anode.mTextString = base64;
                            anode.mIsEncoded = true;
                            anode.mIsTextView = true;


                            if (state.isAtLeastOneNodeVisibleTouser() /* && parent.mIsVisibleToUser*/) {
                                children.add(anode);
                            }
                        }
                    }
                });
            }

            return res;
        }

        public void mangleWebViewChild() {
            //children of WebViews occasionally do get wrong boundsInParent,
            //as w workaround use their bundsInScreen (which will only be used to determine width/height anyway)
            mBoundsInParent = mBoundsInScreen;

            /*
            WebViewXyzzy: Enable to detect editable WebView
            if (isFocused()) {
                mIsEditableTextNode = true;
                mIsTextView = true;
            }
            */
        }
    }

    public synchronized void dump() {
        StringBuffer sb = new StringBuffer();
        if (mRootNode != null) {
            mRootNode.dump(sb, "");
        }


    }

    public static void dumpTreeNodePool(String msg) {
        //  Ln.d("POOL: TreeNodes %s / %s   [%s]", mPool.getCurrentSize(), mPool.getMaxSize(), msg);
    }

    private interface PgpAsciiArmorFoundCallback {
        void onAsciiArmorFound(AsciiArmorFinderState state);
    }

    private interface Base64FoundCallback {
        void onBase64Found(Base64FinderState state);
    }
}
