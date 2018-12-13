package io.oversec.one.ovl;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsoluteLayout;
import android.widget.TextView;

import com.google.protobuf.InvalidProtocolBufferException;

import io.oversec.one.Core;
import io.oversec.one.R;
import io.oversec.one.acs.Tree;
import io.oversec.one.crypto.BaseDecryptResult;
import io.oversec.one.crypto.CryptoHandlerFacade;
import io.oversec.one.crypto.DoDecryptHandler;
import io.oversec.one.crypto.gpg.GpgCryptoHandler;
import io.oversec.one.crypto.gpg.GpgDecryptResult;
import io.oversec.one.crypto.proto.Inner;
import io.oversec.one.crypto.proto.Outer;
import io.oversec.one.crypto.sym.OversecKeystore2;
import io.oversec.one.crypto.ui.Stuff;

import org.openintents.openpgp.OpenPgpSignatureResult;

import roboguice.util.Ln;

import java.io.UnsupportedEncodingException;
import java.util.List;

public class NodeTextView extends TextView implements NodeView {
    private final GradientDrawable mBgShape;
    private final CryptoHandlerFacade mCryptoHandlerFacade;
    private final OverlayDecryptView mOverlayDecryptView;
    private final Core mCore;
    private Tree.TreeNode mNode;
    private boolean mUnused;
    private int mBgColor;
    private float mCornerRadius;
    private float mTextSize;
    private int mTextColor;
    private int mPaddingLeft, mPaddingTop;
    private int mRightDrawableWidth;
    private boolean mShowStatusIcon;
    private int mVisibility = ViewGroup.VISIBLE;
    private boolean mSingleLine;
    private int mDrawableLeft, mDrawableRight, mDrawableTop, mDrawableBottom;

    private Outer.Msg mEncodedData;
    private BaseDecryptResult mTryDecryptResult;
    private boolean mUserInteractionRequired;
    private String mOrigText;
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private boolean mDecryptING;
    private int mVisibleHeight;


    // private DoDecryptHandler mOneTimeDecryptHandler;

    @SuppressWarnings("deprecation")
    public NodeTextView(Core core, Tree.TreeNode node, Rect parentBoundsInScreen, OverlayDecryptView overlayDecryptView, CryptoHandlerFacade cryptoHandlerFacade) {
        super(core.getCtx());
        mCore = core;
        mNode = node;
        mCryptoHandlerFacade = cryptoHandlerFacade;
        mOverlayDecryptView = overlayDecryptView;

        //--------------------- update layout / dimensions

        AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams(0, 0, 0, 0);
        calcLayoutParams(params, node, parentBoundsInScreen);
        setLayoutParams(params);


        //----------------------- update contents


        mBgShape = new GradientDrawable();
        setBackground(mBgShape);

        overlayDecryptView.applyDesign(this);

        readNodeInfo(true);
    }

    private boolean isFocusedEditableText() {
        return mNode.isEditableEditText() && mNode.isFocused();
    }

    private boolean calcLayoutParams(AbsoluteLayout.LayoutParams in, Tree.TreeNode node, Rect parentBoundsInScreen) {
        //render the overlay right ABOVE the edit text so that we can clearly see what is actualy being sent!
        boolean above = isFocusedEditableText() && mCore.getDb().isOverlayAboveInput(mNode.getPackageNameS());

        //ensure z-order
        if (getParent() != null) {
            ViewGroup vg = (ViewGroup) getParent();
            if (vg.indexOfChild(this) < vg.getChildCount() - 1) {
                vg.removeView(this);
                vg.addView(this);
            }
        }

        //bounds in parent reported by the node are only good for getting the nodes dimensions, top/left are alyways 0!
        int nodeWidth = node.getBoundsInParent().width() + mRightDrawableWidth;
        int nodeHeight = node.getBoundsInParent().height();

        Rect boundsInScreen = node.getBoundsInScreen();
        Rect boundsInParent = new Rect(boundsInScreen); //TODO pool
        boundsInParent.offset(-parentBoundsInScreen.left, -parentBoundsInScreen.top); //calculate the real bounds in parent.

        mVisibleHeight = boundsInScreen.height();

        if (boundsInScreen.height() < node.getBoundsInParent().height()) {
            //node seems to be cut off
            if (boundsInParent.top == 0) {
                //cut off at the top
                boundsInParent.offset(0, -(node.getBoundsInParent().height() - boundsInScreen.height()));
            }

        }
        boolean changed = false;
        changed = changed | (in.x != boundsInParent.left);
        in.x = boundsInParent.left;


        if (!above) {
            changed = changed | (in.y != boundsInParent.top);
            in.y = boundsInParent.top;
        } else {
            changed = changed | (in.y != boundsInParent.top - nodeHeight);
            in.y = boundsInParent.top - getHeight();

            if (in.y < 0) {
                //parent doesn't have enough space, try to increase size of parent
                ViewParent vp = getParent();
                if (vp != null && vp instanceof NodeGroupLayout) {
                    NodeGroupLayout parent = (NodeGroupLayout) vp;
                    parent.makeSpaceAbove(-in.y);
                    in.y = 0;
                }
            }

        }

        changed = changed | (in.width != nodeWidth);
        in.width = nodeWidth;

        changed = changed | (in.height != nodeHeight);
        in.height = nodeHeight;

        if (above || mCore.getDb().isVoverflow(mNode.getPackageNameS())) {
            in.height = AbsoluteLayout.LayoutParams.WRAP_CONTENT;
        }

        return changed;
    }

    @Override
    public void update(Tree.TreeNode node, Rect parentBoundsInScreen) {
        mUnused = false;
        mNode.recycle(false);
        mNode = node;


        //--------------------- update layout / dimensions

        AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) getLayoutParams();
        boolean changed = calcLayoutParams(params, node, parentBoundsInScreen);
        if (changed) {
            setLayoutParams(params);
        }


        //----------------------- update contents

        readNodeInfo(false);
    }


    private synchronized void setUserInteractionRequired() {
        mUserInteractionRequired = true;
        mTryDecryptResult = null;
        mDecryptING = false;
    }


    private synchronized void setTryDecryptResult(BaseDecryptResult tdr) {
        mTryDecryptResult = tdr;
        mUserInteractionRequired = false;
        mDecryptING = false;
    }

    private synchronized void readNodeInfo(boolean retryDecrypt) {


        Ln.d("REUSE: readNodeInfo mOrigText=%s   mNode.getOrigTextString()=%s", stringEndAndHash(mOrigText), stringEndAndHash(mNode.getOrigTextString()));

        if (mOrigText == null || !mOrigText.equals(mNode.getOrigTextString())) {
            Ln.d("REUSE: readNodeInfo ############################## MISMATCH, resetting this node!!!!");
            mOrigText = mNode.getOrigTextString();
            mEncodedData = CryptoHandlerFacade.Companion.getEncodedData(getContext(), mOrigText);
            mTryDecryptResult = null;
            mUserInteractionRequired = false;
            mDecryptING = false;
        }


        boolean doDecrypt = false;
        if (mTryDecryptResult != null) {
            switch (mTryDecryptResult.getResult()) {
                case OK:
                    // Ln.d("DECRYPT: REUSING previous TDR, no need to decrypt again!");
                    break;
                case RETRY:
                    // Ln.d("DECRYPT: RETRYING previos TDR");
                    doDecrypt = retryDecrypt;
                    break;
                case FAILED_PERMANENTLY:
                    //Ln.d("DECRYPT: REUSING previous [FAILED] TDR, no need to decrypt again!");
                    break;
            }
        } else if (mUserInteractionRequired) {
            //Ln.d("DECRYPT: RETRYING due to userInteractionRequired");
            doDecrypt = retryDecrypt;

        } else {
            if (mEncodedData == null) {
                //Ln.d("DECRYPT: SKIPPING not-encoded node");
            } else {
                //Ln.d("DECRYPT: Doing INITIAL decrypt");
                doDecrypt = true;
            }
        }


        if (doDecrypt && !mDecryptING) {


            mDecryptING = true;

            if (mOrigText != null) {
                mCore.getEncryptionCache().decryptMaybeAsync(mNode.getPackageName().toString(), mOrigText, mEncodedData, new TextViewDoDecryptHandler(mOrigText));
            } else {
                Ln.w("hmmm, got OrigText NULL, can't decrypt this ;-)");
            }

        }

        mCore.onDecryptResultShownOnScreen(mTryDecryptResult, mNode.getPackageNameS());


        int signatureIconResId = 0;
        int errorIconResId = 0;

        boolean hide = false;

        if (mTryDecryptResult != null)

        {
            if (mTryDecryptResult.isOk()) {
                setBackgroundColorChecked(mDefaultBackgroundColor);
                String text = ""; // or better "N/A" or "Error" ??
                try {
                    Inner.InnerData innerData = mTryDecryptResult.getDecryptedDataAsInnerData();
                    if (innerData.hasTextAndPaddingV0()) {
                        text = innerData.getTextAndPaddingV0().getText();
                    }
                } catch (InvalidProtocolBufferException e) {
                    try {
                        text = mTryDecryptResult.getDecryptedDataAsUtf8String();
                    } catch (UnsupportedEncodingException e1) {
                        e1.printStackTrace();
                    }
                }

                setTextChecked(text);


                setSingleLineChecked(false);
                setEllipsizeChecked(null);
                setTextColorChecked(mDefaultTextColor);
                if (mTryDecryptResult instanceof GpgDecryptResult && ((GpgDecryptResult) mTryDecryptResult).getSignatureResult() != null) {
                    OpenPgpSignatureResult sr = ((GpgDecryptResult) mTryDecryptResult).getSignatureResult();
                    signatureIconResId = GpgCryptoHandler.Companion.signatureResultToUiIconRes(sr, true);

                }

            } else {
                if (mCore.getDb().isDontShowDecryptionFailed(mNode.getPackageNameS())) {
                    hide = true;
                }

                setBackgroundColorChecked(mDefaultBackgroundColor);
                setTextChecked(Stuff.INSTANCE.getErrorText(mTryDecryptResult.getError()));
                setSingleLineChecked(true);
                setEllipsizeChecked(TextUtils.TruncateAt.END);
                errorIconResId = R.drawable.ic_error_red_18dp;
                setTextColorChecked(mDefaultTextColor);
            }
        } else if (mUserInteractionRequired)

        {
            setBackgroundColorChecked(mDefaultBackgroundColor);
            setTextChecked(Stuff.INSTANCE.getUserInteractionRequiredText(false));
            setSingleLineChecked(true);
            setEllipsizeChecked(TextUtils.TruncateAt.END);
            errorIconResId = R.drawable.ic_warning_red_18dp;
            setTextColorChecked(mDefaultTextColor);

        } else if (mDecryptING) {
            if (!isFocusedEditableText()) {

                setBackgroundColorChecked(mDefaultBackgroundColor);
                setTextChecked(getResources().getString(R.string.decrypt_in_progress));
                setSingleLineChecked(true);
                setEllipsizeChecked(TextUtils.TruncateAt.END);
                errorIconResId = R.drawable.ic_loop_orange_18dp;
                setTextColorChecked(ContextCompat.getColor(getContext(), R.color.colorWarning));
            }

        } else {
            setTextChecked("");
            setBackgroundColorChecked(Color.TRANSPARENT);

        }

        if (isEditableEditText() && isFocusedNode()) {
            mShowStatusIcon = false;
            setRightDrawableWidthChecked(0);
        }


        setCompoundDrawablesWithIntrinsicBoundsChecked(errorIconResId, 0, mShowStatusIcon ? signatureIconResId : 0, 0);


        //maybe hide totally
        hide = hide || mCore.isTextIgnored(mOrigText);

        if (hide) {
            setVisibilityChecked(GONE);
        } else {
            setVisibilityChecked(VISIBLE);

        }

    }

    private String stringEndAndHash(String s) {
        if (s == null) return null;
        int hash = s.hashCode();

        if (s.length() > 20) {
            s = s.substring(s.length() - 20);
        }
        return s + " [[" + hash + "]]";
    }


    @Override
    public void setUnused() {
        mUnused = true;
    }

    @Override
    public boolean isUnused() {
        return mUnused;
    }

    @Override
    public void recycle() {
        mNode.recycle(false);
    }

    @Override
    public int getNodeKey() {
        return mNode.getKey();
    }


    @Override
    public boolean matchesNodeType(Tree.TreeNode node) {
        return node.isTextNode();
    }


    public Tree.TreeNode getTreeNode() {
        return mNode;
    }


    private void setCompoundDrawablesWithIntrinsicBoundsChecked(int left, int top, int right, int bottom) {
        if (mDrawableLeft != left || mDrawableRight != right || mDrawableTop != top || mDrawableBottom != bottom) {
            mDrawableLeft = left;
            mDrawableRight = right;
            mDrawableTop = top;
            mDrawableBottom = bottom;
            setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
        }
    }

    private void setCompoundDrawableTintListChecked(ColorStateList ccc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!ccc.equals(getCompoundDrawableTintList())) {
                setCompoundDrawableTintList(ccc);
            }
        }
    }

    private void setEllipsizeChecked(TextUtils.TruncateAt t) {
        TextUtils.TruncateAt e = getEllipsize();
        if (e != t) {
            setEllipsize(t);
        }
    }

    private void setSingleLineChecked(boolean b) {
        if (mSingleLine != b) {
            mSingleLine = b;
            setSingleLine(b);
        }
    }

    private void setTextChecked(String t) {
        if (!getText().equals(t)) {
            setText(t);
        }
    }

    private void setTextChecked(int id) {
        String s = getResources().getString(id);
        setTextChecked(s);
    }


    public void setBackgroundColorChecked(int c) {
        if (mBgColor != c) {
            mBgColor = c;
            mBgShape.setColor(c);
        }
    }

    public void setCornerRadiusChecked(float r) {
        if (mCornerRadius != r) {
            mCornerRadius = r;
            mBgShape.setCornerRadius(r);
        }
    }

    public void setTextSizeChecked(float s) {
        if (mTextSize != s) {
            mTextSize = s;
            setTextSize(TypedValue.COMPLEX_UNIT_SP, s);
        }
    }

    public void setTextColorChecked(int c) {
        if (mTextColor != c) {
            mTextColor = c;
            setTextColor(c);
        }

    }

    public void setPaddingTopChecked(int p) {
        if (mPaddingTop != p) {
            mPaddingTop = p;
            setPadding(mPaddingLeft, mPaddingTop, mPaddingLeft, 0);
        }
    }


    public void setPaddingLeftChecked(int p) {
        if (mPaddingLeft != p) {
            mPaddingLeft = p;
            setPadding(mPaddingLeft, mPaddingTop, mPaddingLeft, 0);
        }
    }

    public void setRightDrawableWidthChecked(int width) {
        if (mRightDrawableWidth != width) {

            AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) getLayoutParams();
            params.width = params.width - mRightDrawableWidth + width;
            setLayoutParams(params);
            mRightDrawableWidth = width;
        }


    }

    public void setCompoundDrawablePaddingChecked(int p) {
        if (getCompoundDrawablePadding() != p) {
            setCompoundDrawablePadding(p);
        }
    }

    public void setShowStatusIconChecked(boolean v) {
        if (mShowStatusIcon != v) {
            mShowStatusIcon = v;
            readNodeInfo(false);
        }
    }

    public void setVisibilityChecked(int visibility) {
        if (mVisibility != visibility) {
            mVisibility = visibility;
            setVisibility(visibility);
        }

    }

    @Override
    public Tree.TreeNode getNode() {
        return mNode;
    }

//    @Override
//    public String getOrigText() {
//        return mNode.getOrigTextString();
//    }

    public BaseDecryptResult getTryDecryptResult() {
        return mTryDecryptResult;
    }

    public boolean isUserInteractionRequired() {
        return mUserInteractionRequired;
    }

    public boolean isFocusedNode() {
        return mNode.isFocused();
    }

    public Rect getNodeBoundsInScreen() {
        return mNode.getBoundsInScreen();
    }

    public boolean isEditableEditText() {
        return mNode.isEditableEditText();
    }

    public void setDefaultBackgroundColor(int c) {
        mDefaultBackgroundColor = c;
    }

    public void setDefaultTextColor(int color) {
        mDefaultTextColor = color;
    }

    public int getVisibleHeight() {
        return mVisibleHeight;
    }

    public String getOrigText() {
        return mNode.getOrigTextString();
    }

    public boolean isDecrypting() {
        return mDecryptING;
    }

    public class TextViewDoDecryptHandler implements DoDecryptHandler, Runnable {
        private BaseDecryptResult mThisTdr;
        private boolean mThisUIRE;
        private String mThisOrigText;

        TextViewDoDecryptHandler(String origText) {
            mThisOrigText = origText;
        }

        @Override
        public void onResult(BaseDecryptResult tdr) {
            mThisTdr = tdr;
            Core.getInstance(getContext()).postRunnableOnUiThread(this);
        }

        @Override
        public void onUserInteractionRequired() {
            mThisUIRE = true;
            Core.getInstance(getContext()).postRunnableOnUiThread(this);
        }


        @Override
        public void run() {
            try {
                if (mNode.isSealed()) {
                    // Ln.d("DECRYPT: ignoring async decryption result as the node-id of the view is sealed!");
                    return;
                }
                if (!mThisOrigText.equals(mNode.getOrigTextString())) {
                    //Ln.d("REUSE: ******  ignoring async decryption result as the TextView is already showing something else: %s  <->  %s",xxxThis,xxxNode);
                    return;
                }

                if (mThisTdr != null) {
                    setTryDecryptResult(mThisTdr);
                    readNodeInfo(false);

                } else if (mThisUIRE) {
                    setUserInteractionRequired();
                    readNodeInfo(false);
                }

                mCore.onNodeTextViewDecrypted_UI(NodeTextView.this, mThisTdr, mThisUIRE);


                mOverlayDecryptView.onOneNodeDecrypted(NodeTextView.this, mThisTdr, mThisUIRE, mThisOrigText);

            } catch (IllegalArgumentException ex) { //the first check of mNode.isSealed is not enough, the node can get sealed between that check and the call to "readNodeInfo", leading to this exception
                ex.printStackTrace();
                return;
            }
        }
    }
}
