package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.History;

import java.util.List;

@Dao
public abstract class HistoryDao extends BaseDao<History> {

    @Query("SELECT * FROM History")
    public abstract List<History> findAll();

    @Query("SELECT * FROM History WHERE cid = :cid ORDER BY createTime DESC")
    public abstract List<History> find(int cid);

    @Query("SELECT * FROM History WHERE cid = :cid")
    public abstract List<History> findAll(int cid);

    @Query("SELECT * FROM History WHERE cid = :cid AND `key` = :key")
    public abstract History find(int cid, String key);

    @Query("SELECT * FROM History WHERE cid = :cid AND vodName = :vodName ORDER BY createTime DESC")
    public abstract List<History> findByName(int cid, String vodName);

    @Query("SELECT * FROM History WHERE cid = :cid AND `key` LIKE :keyPrefix || '%' ORDER BY createTime DESC")
    public abstract List<History> findByKeyPrefix(int cid, String keyPrefix);

    /**
     * 按 siteKey 精确取该站点的历史，最新的在前。
     *
     * <p>为什么用 {@code substr} 而不是 {@code LIKE}：`key` 是
     * {@code siteKey@@@vodId@@@cid}，而 siteKey 可能含 {@code %} 或 {@code _}
     * （SQL LIKE 的通配符）。用 {@code LIKE} 会让 {@code csp_a} 误命中
     * {@code csp_ab}，也会让含 {@code %} 的 siteKey 命中整个表。
     * {@code substr} 是精确前缀比较，无通配语义。
     *
     * <p>前缀必须由调用方带上分隔符（{@code siteKey + "@@@"}），否则
     * {@code csp_a} 仍会命中 {@code csp_ab@@@...}。
     */
    @Query("SELECT * FROM History WHERE cid = :cid AND substr(`key`, 1, length(:keyPrefix)) = :keyPrefix ORDER BY createTime DESC LIMIT :limit")
    public abstract List<History> findBySiteKey(int cid, String keyPrefix, int limit);

    @Query("SELECT * FROM History WHERE cid = :cid AND tmdbId = :tmdbId AND tmdbId > 0 ORDER BY createTime DESC")
    public abstract List<History> findByTmdbId(int cid, int tmdbId);

    @Query("SELECT * FROM History WHERE cid = :cid AND tmdbId = :tmdbId AND LOWER(TRIM(mediaType)) = :mediaType AND tmdbId > 0 ORDER BY createTime DESC")
    public abstract List<History> findByTmdbIdentity(int cid, String mediaType, int tmdbId);

    @Query("SELECT * FROM History WHERE tmdbId = :tmdbId AND LOWER(TRIM(mediaType)) = :mediaType AND tmdbId > 0 ORDER BY createTime DESC")
    public abstract List<History> findByTmdbIdentity(String mediaType, int tmdbId);

    @Query("DELETE FROM History WHERE cid = :cid AND `key` = :key")
    public abstract int delete(int cid, String key);

    @Query("DELETE FROM History WHERE cid = :cid AND `key` LIKE :keyPrefix || '%'")
    public abstract int deleteByKeyPrefix(int cid, String keyPrefix);

    @Query("DELETE FROM History WHERE cid = :cid")
    public abstract int delete(int cid);

    @Query("DELETE FROM History")
    public abstract int delete();
}
