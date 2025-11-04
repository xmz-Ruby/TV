package com.github.tvbox.osc.impl;

import com.github.tvbox.osc.bean.Site;

public interface SiteCallback {

    void setSite(Site item);

    void onChanged();
}
