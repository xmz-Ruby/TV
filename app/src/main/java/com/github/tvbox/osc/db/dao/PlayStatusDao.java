package com.github.tvbox.osc.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.github.tvbox.osc.bean.PlayStatus;

@Dao
public abstract class PlayStatusDao extends BaseDao<PlayStatus> {

    @Query("SELECT * FROM PlayStatus WHERE vodName = :vodName")
    public abstract PlayStatus find(String vodName);

    @Query("DELETE FROM PlayStatus WHERE vodName = :vodName")
    public abstract void delete(String vodName);

    @Query("SELECT * FROM PlayStatus")
    public abstract java.util.List<PlayStatus> getAll();
}
