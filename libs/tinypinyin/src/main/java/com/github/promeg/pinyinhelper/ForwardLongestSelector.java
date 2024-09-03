package com.github.promeg.pinyinhelper;

import org.ahocorasick.trie.Emit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 正向最大匹配选择器
 *
 * Created by guyacong on 2016/12/28.
 */

final class ForwardLongestSelector implements SegmentationSelector {

    static final Engine.EmitComparator HIT_COMPARATOR = new Engine.EmitComparator();

    @Override
    public List<Emit> select(final Collection<Emit> emits) {
        if (emits == null) {
            return null;
        }

        List<Emit> results = new ArrayList<Emit>(emits);

        Collections.sort(results, HIT_COMPARATOR);

        int endValueToRemove = -1;

        Set<Emit> emitToRemove = new TreeSet<Emit>();

        for (Emit emit : results) {
            if (emit.getStart() > endValueToRemove && emit.getEnd() > endValueToRemove) {
                endValueToRemove = emit.getEnd();
            } else {
                emitToRemove.add(emit);
            }
        }

        results.removeAll(emitToRemove);

        return results;
    }
}
