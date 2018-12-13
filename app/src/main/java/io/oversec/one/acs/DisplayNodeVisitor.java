package io.oversec.one.acs;

import io.oversec.one.ovl.NodeTextView;

public interface DisplayNodeVisitor {
    void visit(NodeTextView ntv);
}
