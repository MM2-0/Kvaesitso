package com.github.promeg.pinyinhelper;

import java.util.Map;
import java.util.Set;

/**
 * 基于{@link java.util.Map}的字典实现，利于添加自定义字典
 *
 * Created by guyacong on 2016/12/23.
 */

public abstract class PinyinMapDict implements PinyinDict {

    /**
     * Key为字典的词，Value为该词所对应的拼音
     *
     * @return 包含词和对应拼音的 {@link java.util.Map}
     */
    public abstract Map<String, String[]> mapping();


    @Override
    public Set<String> words() {
        return mapping() != null ? mapping().keySet() : null;
    }

    @Override
    public String[] toPinyin(String word) {
        return mapping() != null ? mapping().get(word) : null;
    }
}
