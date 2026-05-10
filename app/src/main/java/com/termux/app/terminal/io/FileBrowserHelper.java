package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.termux.terminal.TerminalSession;

public class FileBrowserHelper {

    private final FragmentActivity mActivity;

    public FileBrowserHelper(@NonNull FragmentActivity activity) {
        mActivity = activity;
    }

    public void show(@NonNull TerminalSession session) {
        FileBrowserFragment fragment = FileBrowserFragment.newInstance(null);
        fragment.setTerminalSession(session);
        fragment.show(mActivity.getSupportFragmentManager(), "file_browser");
    }
}
