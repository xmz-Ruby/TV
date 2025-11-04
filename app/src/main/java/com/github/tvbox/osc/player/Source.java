package com.github.tvbox.osc.player;

import com.github.tvbox.osc.Constant;
import com.github.tvbox.osc.bean.Channel;
import com.github.tvbox.osc.bean.Episode;
import com.github.tvbox.osc.bean.Flag;
import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.player.extractor.Force;
import com.github.tvbox.osc.player.extractor.JianPian;
import com.github.tvbox.osc.player.extractor.Proxy;
import com.github.tvbox.osc.player.extractor.Push;
import com.github.tvbox.osc.player.extractor.TVBus;
import com.github.tvbox.osc.player.extractor.Thunder;
import com.github.tvbox.osc.player.extractor.Video;
import com.github.tvbox.osc.player.extractor.Youtube;
import com.github.tvbox.osc.player.extractor.ZLive;
import com.github.tvbox.osc.utils.UrlUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Source {

    private final List<Extractor> extractors;

    private static class Loader {
        static volatile Source INSTANCE = new Source();
    }

    public static Source get() {
        return Loader.INSTANCE;
    }

    public Source() {
        extractors = new ArrayList<>();
        extractors.add(new Force());
        extractors.add(new JianPian());
        extractors.add(new Proxy());
        extractors.add(new Push());
        extractors.add(new Thunder());
        extractors.add(new TVBus());
        extractors.add(new Video());
        extractors.add(new Youtube());
        extractors.add(new ZLive());
    }

    private Extractor getExtractor(String url) {
        String host = UrlUtil.host(url);
        String scheme = UrlUtil.scheme(url);
        for (Extractor extractor : extractors) if (extractor.match(scheme, host)) return extractor;
        return null;
    }

    private void addCallable(Iterator<Episode> iterator, List<Callable<List<Episode>>> items) {
        String url = iterator.next().getUrl();
        if (Thunder.Parser.match(url)) {
            items.add(Thunder.Parser.get(url));
            iterator.remove();
        } else if (Youtube.Parser.match(url)) {
            items.add(Youtube.Parser.get(url));
            iterator.remove();
        }
    }

    public void parse(List<Flag> flags) throws Exception {
        for (Flag flag : flags) {
            ExecutorService executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
            List<Callable<List<Episode>>> items = new ArrayList<>();
            Iterator<Episode> iterator = flag.getEpisodes().iterator();
            while (iterator.hasNext()) addCallable(iterator, items);
            for (Future<List<Episode>> future : executor.invokeAll(items, 30, TimeUnit.SECONDS)) flag.getEpisodes().addAll(future.get());
            executor.shutdownNow();
        }
    }

    public String fetch(Result result) throws Exception {
        String url = result.getUrl().v();
        Extractor extractor = getExtractor(url);
        if (extractor != null) result.setParse(0);
        if (extractor instanceof Video) result.setParse(1);
        return extractor == null ? url : extractor.fetch(url);
    }

    public String fetch(Channel channel) throws Exception {
        String url = channel.getCurrent().split("\\$")[0];
        Extractor extractor = getExtractor(url);
        if (extractor != null) channel.setParse(0);
        if (extractor instanceof Video) channel.setParse(1);
        return extractor == null ? url : extractor.fetch(url);
    }

    public void stop() {
        if (extractors == null) return;
        for (Extractor extractor : extractors) extractor.stop();
    }

    public void exit() {
        if (extractors == null) return;
        for (Extractor extractor : extractors) extractor.exit();
    }

    public interface Extractor {

        boolean match(String scheme, String host);

        String fetch(String url) throws Exception;

        void stop();

        void exit();
    }
}
