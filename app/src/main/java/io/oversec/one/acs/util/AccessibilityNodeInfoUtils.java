/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.oversec.one.acs.util;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;


import java.util.Collection;
import java.util.Comparator;


/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo
 * objects. NOTE: This class only recycles unused nodes that were collected
 * internally. Any node passed into or returned from a public method is retained
 * and TalkBack should recycle it when appropriate.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class AccessibilityNodeInfoUtils {

    /**
     * Whether isVisibleToUser() is supported by the current SDK.
     */
    private static final boolean SUPPORTS_VISIBILITY = (Build.VERSION.SDK_INT >= 16);

    private AccessibilityNodeInfoUtils() {
        // This class is not instantiable.
    }

    /**
     * Gets the text of a <code>node</code> by returning the content description
     * (if available) or by returning the text.
     *
     * @param node The node.
     * @return The node text.
     */
    public static CharSequence getNodeText(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }


        CharSequence text = node.getText();
        if (!TextUtils.isEmpty(text)
                && (TextUtils.getTrimmedLength(text) > 0)) {
            return text;
        }

        //Use contentDescription only if there are no child nodes -

        if (node.getChildCount() == 0) {
            final CharSequence contentDescription = node.getContentDescription();
            if (!TextUtils.isEmpty(contentDescription)
                    && (TextUtils.getTrimmedLength(contentDescription) > 0)) {
                return contentDescription;
            }


        }
        return null;

//        // Prefer content description over text.
//        // TODO: Why are we checking the trimmed length?
//        final CharSequence contentDescription = node.getContentDescription();
//        if (!TextUtils.isEmpty(contentDescription)
//                && (TextUtils.getTrimmedLength(contentDescription) > 0)) {
//            return contentDescription;
//        }
//
//        CharSequence text = node.getText();
//        if (!TextUtils.isEmpty(text)
//                && (TextUtils.getTrimmedLength(text) > 0)) {
//            return text;
//        }
//
//        return null;
    }

    /**
     * Returns the root node of the tree containing {@code node}.
     */
    public static AccessibilityNodeInfo getRoot(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        AccessibilityNodeInfo current = null;
        AccessibilityNodeInfo parent = AccessibilityNodeInfo.obtain(node);

        do {
            current = parent;
            parent = current.getParent();
        } while (parent != null);

        return current;
    }


    /**
     * Returns whether the specified node has text.
     *
     * @param node The node to check.
     * @return {@code true} if the node has text.
     */
    private static boolean hasText(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence text = node.getText();
        return (!TextUtils.isEmpty(text));
    }


    /**
     * Determines if the generating class of an
     * {@link AccessibilityNodeInfo} matches a given {@link Class} by
     * type.
     *
     * @param node           A sealed {@link AccessibilityNodeInfo} dispatched by
     *                       the accessibility framework.
     * @param referenceClass A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfo} object
     * matches the {@link Class} by type or inherited type,
     * {@code false} otherwise.
     */
    public static boolean nodeMatchesClassByType(
            Context context, AccessibilityNodeInfo node, Class<?> referenceClass) {
        if ((node == null) || (referenceClass == null)) {
            return false;
        }

        // Attempt to take a shortcut.
        final CharSequence nodeClassName = node.getClassName();
        if (TextUtils.equals(nodeClassName, referenceClass.getName())) {
            return true;
        }

        final ClassLoadingManager loader = ClassLoadingManager.getInstance();
        final CharSequence appPackage = node.getPackageName();
        return loader.checkInstanceOf(context, nodeClassName, appPackage, referenceClass);
    }

    /**
     * Determines if the generating class of an
     * {@link AccessibilityNodeInfo} matches any of the given
     * {@link Class}es by type.
     *
     * @param node             A sealed {@link AccessibilityNodeInfo} dispatched by
     *                         the accessibility framework.
     * @param referenceClasses A variable-length list of {@link Class} objects
     *                         to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfo} object
     * matches the {@link Class} by type or inherited type,
     * {@code false} otherwise.
     */
    public static boolean nodeMatchesAnyClassByType(
            Context context, AccessibilityNodeInfo node, Class<?>... referenceClasses) {
        for (Class<?> referenceClass : referenceClasses) {
            if (nodeMatchesClassByType(context, node, referenceClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the class of an {@link AccessibilityNodeInfo} matches
     * a given {@link Class} by package and name.
     *
     * @param node               A sealed {@link AccessibilityNodeInfo} dispatched by
     *                           the accessibility framework.
     * @param referenceClassName A class name to match.
     * @return {@code true} if the {@link AccessibilityNodeInfo} matches
     * the class name.
     */
    public static boolean nodeMatchesClassByName(
            Context context, AccessibilityNodeInfo node, CharSequence referenceClassName) {
        if ((node == null) || (referenceClassName == null)) {
            return false;
        }

        // Attempt to take a shortcut.
        final CharSequence nodeClassName = node.getClassName();
        if (TextUtils.equals(nodeClassName, referenceClassName)) {
            return true;
        }

        final ClassLoadingManager loader = ClassLoadingManager.getInstance();
        final CharSequence appPackage = node.getPackageName();
        return loader.checkInstanceOf(context, nodeClassName, appPackage, referenceClassName);
    }

    /**
     * Recycles the given nodes.
     *
     * @param nodes The nodes to recycle.
     */
    public static void recycleNodes(Collection<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) {
                node.recycle();
            }
        }

        nodes.clear();
    }

    /**
     * Recycles the given nodes.
     *
     * @param nodes The nodes to recycle.
     */
    public static void recycleNodes(AccessibilityNodeInfo... nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) {
                node.recycle();
            }
        }
    }

    /**
     * Returns a fresh copy of {@code node} with properties that are
     * less likely to be stale.  Returns {@code null} if the node can't be
     * found anymore.
     */
    public static AccessibilityNodeInfo refreshNode(
            AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        AccessibilityNodeInfo result = refreshFromChild(node);
        if (result == null) {
            result = refreshFromParent(node);
        }
        return result;
    }

    private static AccessibilityNodeInfo refreshFromChild(
            AccessibilityNodeInfo node) {
        if (node.getChildCount() > 0) {
            AccessibilityNodeInfo firstChild = node.getChild(0);
            if (firstChild != null) {
                AccessibilityNodeInfo parent = firstChild.getParent();
                firstChild.recycle();
                if (node.equals(parent)) {
                    return parent;
                } else {
                    recycleNodes(parent);
                }
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo refreshFromParent(
            AccessibilityNodeInfo node) {
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            try {
                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    AccessibilityNodeInfo child = parent.getChild(i);
                    if (node.equals(child)) {
                        return child;
                    }
                    recycleNodes(child);
                }
            } finally {
                parent.recycle();
            }
        }
        return null;
    }

    /**
     * Helper method that returns {@code true} if the specified node is visible
     * to the user or if the current SDK doesn't support checking visibility.
     */
    public static boolean isVisibleOrLegacy(AccessibilityNodeInfo node) {
        return (!AccessibilityNodeInfoUtils.SUPPORTS_VISIBILITY || node.isVisibleToUser());
    }


    /**
     * Compares two AccessibilityNodeInfos in left-to-right and top-to-bottom
     * fashion.
     */
    public static class TopToBottomLeftToRightComparator implements
            Comparator<AccessibilityNodeInfo> {
        private final Rect mFirstBounds = new Rect();
        private final Rect mSecondBounds = new Rect();

        private static final int BEFORE = -1;
        private static final int AFTER = 1;

        @Override
        public int compare(AccessibilityNodeInfo first, AccessibilityNodeInfo second) {
            final Rect firstBounds = mFirstBounds;
            first.getBoundsInScreen(firstBounds);

            final Rect secondBounds = mSecondBounds;
            second.getBoundsInScreen(secondBounds);

            // First is entirely above second.
            if (firstBounds.bottom <= secondBounds.top) {
                return BEFORE;
            }

            // First is entirely below second.
            if (firstBounds.top >= secondBounds.bottom) {
                return AFTER;
            }

            // Smaller left-bound.
            final int leftDifference = (firstBounds.left - secondBounds.left);
            if (leftDifference != 0) {
                return leftDifference;
            }

            // Smaller top-bound.
            final int topDifference = (firstBounds.top - secondBounds.top);
            if (topDifference != 0) {
                return topDifference;
            }

            // Smaller bottom-bound.
            final int bottomDifference = (firstBounds.bottom - secondBounds.bottom);
            if (bottomDifference != 0) {
                return bottomDifference;
            }

            // Smaller right-bound.
            final int rightDifference = (firstBounds.right - secondBounds.right);
            if (rightDifference != 0) {
                return rightDifference;
            }

            // Just break the tie somehow. The hash codes are unique
            // and stable, hence this is deterministic tie breaking.
            return first.hashCode() - second.hashCode();
        }
    }


}