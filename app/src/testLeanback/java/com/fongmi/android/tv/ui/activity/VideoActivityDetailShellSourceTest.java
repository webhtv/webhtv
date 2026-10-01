package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-contract regression checks for the TV detail shell. */
public class VideoActivityDetailShellSourceTest {

    @Test
    public void emptyDetailFromSearchKeepsPlaybackPageForRetry() throws Exception {
        Path path = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        String source = Files.readString(path, StandardCharsets.UTF_8);
        int start = source.indexOf("private void setEmpty(boolean finish) {");
        int end = source.indexOf("\n    private void showEmpty()", start);

        assertTrue("VideoActivity must keep a dedicated empty-detail path", start >= 0 && end > start);
        String empty = source.substring(start, end);
        assertTrue("explicit detail errors must still close the failed playback page",
                empty.contains("if (finish) {"));
        assertFalse("a failed search-result detail must not close the playback page and reveal search results",
                empty.contains("isFromCollect() || finish"));
        assertTrue("the failed detail must retain the title and enter the existing retry path",
                empty.contains("mBinding.name.setText(getName());")
                        && empty.contains("checkSearch(false);"));
    }
}
