package com.k2.music;

import android.text.Editable;
import android.text.TextWatcher;

final class SimpleTextWatcher implements TextWatcher {
    private final Runnable afterChanged;

    SimpleTextWatcher(Runnable afterChanged) {
        this.afterChanged = afterChanged;
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (afterChanged != null) {
            afterChanged.run();
        }
    }
}
