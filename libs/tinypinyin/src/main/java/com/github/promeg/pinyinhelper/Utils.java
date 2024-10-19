package com.github.promeg.pinyinhelper;

import org.ahocorasick.trie.Trie;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by guyacong on 2016/12/28.
 */

final class Utils {

    private Utils() {
        //no instance
    }

    static Trie dictsToTrie(List<PinyinDict> pinyinDicts) {
        Set<String> all = new TreeSet<String>();

        Trie.TrieBuilder builder = Trie.builder();

        if (pinyinDicts != null) {
            for (PinyinDict dict : pinyinDicts) {
                if (dict != null && dict.words() != null) {
                    all.addAll(dict.words());
                }
            }
            if (all.size() > 0) {
                for (String key : all) {
                    builder.addKeyword(key);
                }
                return builder.build();
            }
        }

        return null;
    }
}
