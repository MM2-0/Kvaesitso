package com.github.promeg.tinypinyin.android.asset.lexicons;

import com.github.promeg.pinyinhelper.PinyinMapDict;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 从Asset中的文本文件构建词典的辅助类
 *
 * 词典格式为：每行一个词和对应的拼音，拼音在前，词在后，空格分隔，拼音间以'分隔
 *        例：  CHONG'QING 重庆
 *
 * Created by guyacong on 2016/12/23.
 */
public abstract class AndroidAssetDict extends PinyinMapDict {

    /**
     * 返回Asset中存储词典信息的文本文档的路径，必须非空
     *
     * @return
     */
    protected abstract String assetFileName();


    final Context mContext;

    final Map<String, String[]> mDict;

    public AndroidAssetDict(Context context) {
        mContext = context.getApplicationContext();
        mDict = new HashMap<>();
        init();
    }

    @Override
    public Map<String, String[]> mapping() {
        return mDict;
    }

    private void init() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(mContext.getAssets().open(assetFileName()), "utf-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                // process the line.
                String[] keyAndValue = line.split("\\s+");
                if (keyAndValue != null && keyAndValue.length == 2) {
                    String[] pinyinStrs = keyAndValue[0].split("'");
                    mDict.put(keyAndValue[1], pinyinStrs);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
