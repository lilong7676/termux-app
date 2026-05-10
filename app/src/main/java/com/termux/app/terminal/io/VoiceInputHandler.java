package com.termux.app.terminal.io;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

public class VoiceInputHandler {

    public static final int REQUEST_RECORD_AUDIO = 100;

    private static final String LOG_TAG = "VoiceInputHandler";

    private final Activity mActivity;
    private SpeechRecognizer mSpeechRecognizer;
    private PopupWindow mOverlay;
    private boolean mIsListening;

    public VoiceInputHandler(@NonNull Activity activity) {
        mActivity = activity;
    }

    public boolean isVoiceRecognitionAvailable() {
        PackageManager pm = mActivity.getPackageManager();
        List<?> activities = pm.queryIntentActivities(
            new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        return !activities.isEmpty();
    }

    public void startListening(@NonNull final TerminalSession session) {
        if (mIsListening) {
            stopListening();
            return;
        }

        if (!isVoiceRecognitionAvailable()) {
            Logger.showToast(mActivity, "Voice recognition not available. Install Google Voice Search.", true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            mActivity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            mActivity.requestPermissions(
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        mIsListening = true;
        showOverlay();

        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mActivity);
        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                List<String> recognitions = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (recognitions != null && !recognitions.isEmpty()) {
                    String text = recognitions.get(0);
                    injectText(session, text);
                }
                dismiss();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                List<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    updateOverlayText(partial.get(0));
                }
            }

            @Override
            public void onError(int error) {
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO: msg = "Audio error"; break;
                    case SpeechRecognizer.ERROR_CLIENT: msg = "Client error"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: msg = "Permission denied"; break;
                    case SpeechRecognizer.ERROR_NETWORK: msg = "Network error"; break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: msg = "Network timeout"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH: msg = "No speech match"; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: msg = "Recognizer busy"; break;
                    case SpeechRecognizer.ERROR_SERVER: msg = "Server error"; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "No speech detected"; break;
                    default: msg = "Error code: " + error;
                }
                Logger.logError(LOG_TAG, "Speech recognition error: " + msg);
                dismiss();
            }

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        mSpeechRecognizer.startListening(intent);
    }

    public void stopListening() {
        dismiss();
    }

    private void injectText(TerminalSession session, String text) {
        if (session != null && session.isRunning()) {
            session.write(text);
        }
    }

    private void showOverlay() {
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        View overlayView = inflater.inflate(R.layout.view_voice_input_overlay, null);

        mOverlay = new PopupWindow(overlayView,
            PopupWindow.LayoutParams.WRAP_CONTENT,
            PopupWindow.LayoutParams.WRAP_CONTENT, true);
        mOverlay.setAnimationStyle(android.R.style.Animation_Toast);

        View rootView = mActivity.findViewById(android.R.id.content);
        int[] location = new int[2];
        rootView.getLocationOnScreen(location);

        mOverlay.showAtLocation(rootView, Gravity.CENTER_HORIZONTAL | Gravity.TOP,
            0, location[1] + 80);
    }

    private void updateOverlayText(String text) {
        if (mOverlay != null && mOverlay.isShowing()) {
            View v = mOverlay.getContentView();
            TextView tv = v.findViewById(R.id.voice_overlay_text);
            if (tv != null) {
                tv.setText(text);
            }
        }
    }

    private void dismiss() {
        mIsListening = false;
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;
        }
        if (mOverlay != null && mOverlay.isShowing()) {
            mOverlay.dismiss();
        }
        mOverlay = null;
    }
}
