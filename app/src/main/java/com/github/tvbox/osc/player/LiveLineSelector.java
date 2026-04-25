package com.github.tvbox.osc.player;

import com.github.tvbox.osc.bean.Channel;

public class LiveLineSelector {

    private boolean[] failed;
    private boolean[] usable;
    private int[] widths;
    private int[] heights;
    private int count;

    public void reset(Channel channel) {
        count = channel == null ? 0 : channel.getUrls().size();
        failed = new boolean[count];
        usable = new boolean[count];
        widths = new int[count];
        heights = new int[count];
    }

    public void sync(Channel channel) {
        int newCount = channel == null ? 0 : channel.getUrls().size();
        if (newCount == count && failed != null) return;
        boolean[] oldFailed = failed;
        boolean[] oldUsable = usable;
        int[] oldWidths = widths;
        int[] oldHeights = heights;
        failed = new boolean[newCount];
        usable = new boolean[newCount];
        widths = new int[newCount];
        heights = new int[newCount];
        int copy = Math.min(count, newCount);
        for (int i = 0; i < copy; i++) {
            failed[i] = oldFailed != null && oldFailed[i];
            usable[i] = oldUsable != null && oldUsable[i];
            widths[i] = oldWidths == null ? 0 : oldWidths[i];
            heights[i] = oldHeights == null ? 0 : oldHeights[i];
        }
        count = newCount;
    }

    public void markUsable(int line, int width, int height) {
        if (!isValid(line)) return;
        usable[line] = true;
        failed[line] = false;
        if (isBetter(width, height, widths[line], heights[line])) {
            widths[line] = Math.max(width, 0);
            heights[line] = Math.max(height, 0);
        }
    }

    public void markFailed(int line) {
        if (!isValid(line)) return;
        failed[line] = true;
        usable[line] = false;
    }

    public int bestUsable(int currentLine) {
        int best = -1;
        for (int i = 0; i < count; i++) {
            if (i == currentLine || failed[i] || !usable[i]) continue;
            if (best == -1 || isBetter(widths[i], heights[i], widths[best], heights[best])) best = i;
        }
        return best;
    }

    public int bestCandidate(int currentLine) {
        int best = bestUsable(currentLine);
        if (best != -1) return best;
        for (int i = 1; i <= count; i++) {
            int line = (currentLine + i) % count;
            if (!failed[line]) return line;
        }
        return -1;
    }

    public boolean allFailed() {
        if (count == 0) return true;
        for (int i = 0; i < count; i++) if (!failed[i]) return false;
        return true;
    }

    public int getUsableCount() {
        int result = 0;
        if (usable == null) return result;
        for (int i = 0; i < count; i++) if (usable[i] && !failed[i]) result++;
        return result;
    }

    public int getTotalCount() {
        return count;
    }

    public boolean isBetterThanCurrent(int line, int currentLine) {
        if (!isValid(line) || !isValid(currentLine) || failed[line] || !usable[line]) return false;
        return isBetter(widths[line], heights[line], widths[currentLine], heights[currentLine]);
    }

    private boolean isValid(int line) {
        return failed != null && line >= 0 && line < count;
    }

    private boolean isBetter(int width, int height, int currentWidth, int currentHeight) {
        long score = Math.max(width, 0L) * Math.max(height, 0L);
        long currentScore = Math.max(currentWidth, 0L) * Math.max(currentHeight, 0L);
        return score > currentScore || score == currentScore && width > currentWidth;
    }
}
