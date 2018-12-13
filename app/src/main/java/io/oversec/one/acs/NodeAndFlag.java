package io.oversec.one.acs;

import android.view.accessibility.AccessibilityNodeInfo;

class NodeAndFlag {
    int nodeHash;
    AccessibilityNodeInfo node;
    boolean cancelled;
    public OversecAccessibilityService.PerformNodeAction nodeAction;

    public NodeAndFlag(AccessibilityNodeInfo node, OversecAccessibilityService.PerformNodeAction nodeAction) {
        this.node = node;
        this.nodeHash = node.hashCode();
        this.nodeAction = nodeAction;
    }
}
