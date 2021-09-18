package com.balsikandar.crashreporter.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.balsikandar.crashreporter.CrashReporter;
import com.balsikandar.crashreporter.adapter.CrashLogAdapter;
import com.balsikandar.crashreporter.utils.Constants;
import com.balsikandar.crashreporter.utils.CrashUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import de.mm20.launcher2.crashreporter.R;

/**
 * Created by bali on 11/08/17.
 */

public class ExceptionLogFragment extends Fragment {

    private CrashLogAdapter logAdapter;

    private RecyclerView exceptionRecyclerView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.exception_log, container, false);
        exceptionRecyclerView = (RecyclerView) view.findViewById(R.id.exceptionRecyclerView);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAdapter(getActivity(), exceptionRecyclerView);
    }

    private void loadAdapter(Context context, RecyclerView exceptionRecyclerView) {

        logAdapter = new CrashLogAdapter(context, getAllExceptions());
        exceptionRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        exceptionRecyclerView.setAdapter(logAdapter);
    }

    public void clearLog() {
        if (logAdapter != null) {
            logAdapter.updateList(getAllExceptions());
        }
    }

    public ArrayList<File> getAllExceptions() {
        String directoryPath;
        String crashReportPath = CrashReporter.getCrashReportPath();

        if (TextUtils.isEmpty(crashReportPath)){
            directoryPath = CrashUtil.getDefaultPath();
        } else{
            directoryPath = crashReportPath;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()){
            throw new RuntimeException("The path provided doesn't exists : " + directoryPath);
        }

        ArrayList<File> listOfFiles = new ArrayList<>(Arrays.asList(directory.listFiles()));
        for (Iterator<File> iterator = listOfFiles.iterator(); iterator.hasNext(); ) {
            if (iterator.next().getName().contains(Constants.CRASH_SUFFIX)) {
                iterator.remove();
            }
        }
        Collections.sort(listOfFiles, Collections.reverseOrder());
        return listOfFiles;
    }

}
