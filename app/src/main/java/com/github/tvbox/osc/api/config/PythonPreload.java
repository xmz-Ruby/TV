package com.github.tvbox.osc.api.config;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicInteger;

public final class PythonPreload {

    private static final AtomicInteger TOKEN = new AtomicInteger(0);
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(State.idle());

    private PythonPreload() {
    }

    public static LiveData<State> observe() {
        return STATE;
    }

    public static int start(int total) {
        int token = TOKEN.incrementAndGet();
        STATE.postValue(State.running(token, total, 0, 0, 0, ""));
        return token;
    }

    public static void progress(int token, int total, int completed, int success, int fail, String siteName) {
        if (TOKEN.get() != token) return;
        STATE.postValue(State.running(token, total, completed, success, fail, siteName));
    }

    public static void finish(int token, int total, int completed, int success, int fail) {
        if (TOKEN.get() != token) return;
        STATE.postValue(State.finished(token, total, completed, success, fail));
    }

    public static void hide() {
        STATE.postValue(State.idle());
    }

    public static final class State {

        private final int token;
        private final int total;
        private final int completed;
        private final int success;
        private final int fail;
        private final boolean completedAll;
        private final String siteName;
        private final long startedAt;

        private State(int token, int total, int completed, int success, int fail, boolean completedAll, String siteName, long startedAt) {
            this.token = token;
            this.total = total;
            this.completed = completed;
            this.success = success;
            this.fail = fail;
            this.completedAll = completedAll;
            this.siteName = siteName == null ? "" : siteName;
            this.startedAt = startedAt;
        }

        public static State idle() {
            return new State(0, 0, 0, 0, 0, false, "", 0);
        }

        public static State running(int token, int total, int completed, int success, int fail, String siteName) {
            return new State(token, total, completed, success, fail, false, siteName, System.currentTimeMillis());
        }

        public static State finished(int token, int total, int completed, int success, int fail) {
            State current = STATE.getValue();
            long startedAt = current != null && current.token == token ? current.startedAt : System.currentTimeMillis();
            return new State(token, total, completed, success, fail, true, "", startedAt);
        }

        public int getToken() {
            return token;
        }

        public int getTotal() {
            return total;
        }

        public int getCompleted() {
            return completed;
        }

        public int getSuccess() {
            return success;
        }

        public int getFail() {
            return fail;
        }

        public boolean isCompletedAll() {
            return completedAll;
        }

        public String getSiteName() {
            return siteName;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public boolean isVisible() {
            return total > 0 && (completed < total || completedAll);
        }
    }
}
