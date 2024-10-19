package com.github.promeg.pinyinhelper;

import java.util.HashMap;
import java.util.Map;

public final class PinyinRules {
    private final Map<String, String[]> mOverrides = new HashMap<String, String[]>();

    public PinyinRules add(char c, String pinyin) {
        mOverrides.put(String.valueOf(c), new String[]{pinyin});
        return this;
    }

    public PinyinRules add(String str, String pinyin) {
        mOverrides.put(str, new String[]{pinyin});
        return this;
    }

    String toPinyin(char c) {
        return mOverrides.get(String.valueOf(c))[0];
    }

    PinyinMapDict toPinyinMapDict() {
        return new PinyinMapDict() {
            @Override
            public Map<String, String[]> mapping() {
                return mOverrides;
            }
        };
    }
}
