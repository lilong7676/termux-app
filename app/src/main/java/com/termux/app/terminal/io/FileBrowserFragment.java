package com.termux.app.terminal.io;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.termux.R;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileBrowserFragment extends BottomSheetDialogFragment {

    private static final String ARG_INITIAL_PATH = "initial_path";

    private RecyclerView mFileList;
    private TextView mPathView;
    private File mCurrentDir;
    private FileAdapter mAdapter;
    private TerminalSession mSession;

    public interface OnFileSelectedListener {
        void onFileSelected(String path, boolean isDirectory);
    }

    @Nullable
    private OnFileSelectedListener mListener;

    public static FileBrowserFragment newInstance(@Nullable String initialPath) {
        FileBrowserFragment frag = new FileBrowserFragment();
        Bundle args = new Bundle();
        if (initialPath != null) args.putString(ARG_INITIAL_PATH, initialPath);
        frag.setArguments(args);
        return frag;
    }

    public void setOnFileSelectedListener(@Nullable OnFileSelectedListener listener) {
        mListener = listener;
    }

    public void setTerminalSession(@Nullable TerminalSession session) {
        mSession = session;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_file_browser, container, false);

        mPathView = view.findViewById(R.id.browser_path);
        mFileList = view.findViewById(R.id.file_list);
        mFileList.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new FileAdapter();
        mFileList.setAdapter(mAdapter);

        // Close button
        view.findViewById(R.id.browser_close).setOnClickListener(v -> dismiss());

        // Quick access buttons
        view.findViewById(R.id.qa_up).setOnClickListener(v -> {
            if (mCurrentDir != null && mCurrentDir.getParentFile() != null) {
                navigateTo(mCurrentDir.getParentFile());
            }
        });
        view.findViewById(R.id.qa_home).setOnClickListener(v -> navigateTo(getHomeDir()));
        view.findViewById(R.id.qa_sdcard).setOnClickListener(v -> {
            File sdcard = new File("/sdcard");
            if (sdcard.exists()) navigateTo(sdcard);
            else {
                File storage = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
                if (storage.exists()) navigateTo(storage);
            }
        });
        view.findViewById(R.id.qa_root).setOnClickListener(v -> navigateTo(new File("/")));

        // Initial directory
        String initialPath = null;
        if (getArguments() != null) {
            initialPath = getArguments().getString(ARG_INITIAL_PATH);
        }
        File startDir = initialPath != null ? new File(initialPath) : getHomeDir();
        if (!startDir.exists() || !startDir.isDirectory()) {
            startDir = getHomeDir();
        }
        navigateTo(startDir);

        return view;
    }

    private void navigateTo(@NonNull File dir) {
        mCurrentDir = dir;
        mPathView.setText(dir.getAbsolutePath());
        mAdapter.loadDirectory(dir);
    }

    private File getHomeDir() {
        // Try Termux home first
        File termuxHome = new File(Environment.getDataDirectory(), "data/com.termux/files/home");
        if (termuxHome.exists()) return termuxHome;

        // Fallback to environment home
        String home = System.getenv("HOME");
        if (home != null) {
            File f = new File(home);
            if (f.exists()) return f;
        }

        return Environment.getExternalStorageDirectory();
    }

    // ---- RecyclerView Adapter ----

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        private final List<File> mFiles = new ArrayList<>();
        private final SimpleDateFormat mDateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.US);

        void loadDirectory(File dir) {
            mFiles.clear();
            File[] children = dir.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                mFiles.addAll(Arrays.asList(children));
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_browser, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = mFiles.get(position);

            boolean isDir = file.isDirectory();
            holder.icon.setText(isDir ? "📁" : emojiForFile(file.getName()));
            holder.name.setText(file.getName());

            String info;
            if (isDir) {
                info = "Folder";
            } else {
                info = formatSize(file.length());
                info += " · " + mDateFormat.format(new Date(file.lastModified()));
            }
            holder.info.setText(info);

            holder.select.setText(isDir ? "▶" : "→");

            holder.itemView.setOnClickListener(v -> {
                if (isDir) {
                    navigateTo(file);
                } else {
                    String path = file.getAbsolutePath();
                    if (mSession != null && mSession.isRunning()) {
                        mSession.write(path);
                    }
                    if (mListener != null) {
                        mListener.onFileSelected(path, false);
                    }
                    dismiss();
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (isDir) {
                    // cd into directory
                    String cmd = "cd \"" + file.getAbsolutePath() + "\"\r";
                    if (mSession != null && mSession.isRunning()) {
                        mSession.write(cmd);
                    }
                    if (mListener != null) {
                        mListener.onFileSelected(file.getAbsolutePath(), true);
                    }
                    dismiss();
                } else {
                    // Inject full path with newline
                    String path = file.getAbsolutePath() + "\r";
                    if (mSession != null && mSession.isRunning()) {
                        mSession.write(path);
                    }
                    if (mListener != null) {
                        mListener.onFileSelected(file.getAbsolutePath(), false);
                    }
                    dismiss();
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mFiles.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView icon, name, info, select;

            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.file_icon);
                name = v.findViewById(R.id.file_name);
                info = v.findViewById(R.id.file_info);
                select = v.findViewById(R.id.file_select);
            }
        }
    }

    // ---- Helpers ----

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static String emojiForFile(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
            || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp"))
            return "🖼";
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")
            || name.endsWith(".mov") || name.endsWith(".webm"))
            return "🎬";
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")
            || name.endsWith(".ogg") || name.endsWith(".m4a"))
            return "🎵";
        if (name.endsWith(".pdf")) return "📕";
        if (name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz")
            || name.endsWith(".bz2") || name.endsWith(".xz") || name.endsWith(".7z"))
            return "📦";
        if (name.endsWith(".apk")) return "📱";
        if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh"))
            return "⚙";
        if (name.endsWith(".py") || name.endsWith(".java") || name.endsWith(".kt")
            || name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".rs")
            || name.endsWith(".go") || name.endsWith(".js") || name.endsWith(".ts"))
            return "📝";
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log"))
            return "📄";
        if (name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".yml")
            || name.endsWith(".yaml") || name.endsWith(".toml"))
            return "📋";
        if (name.endsWith(".html") || name.endsWith(".css")) return "🌐";
        if (name.startsWith(".")) return "👻";
        return "📄";
    }
}
