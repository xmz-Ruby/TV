package com.github.tvbox.osc.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.github.tvbox.osc.bean.Site;

@Dao
public abstract class SiteDao extends BaseDao<Site> {

    @Query("SELECT * FROM Site WHERE `key` = :key")
    public abstract Site find(String key);
}
