package com.scaffold.system.notice.mapper;

import com.scaffold.system.notice.domain.SysNotice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 站内通知 Mapper。
 * 可见性口径统一为：recipient_id IS NULL（全员）或 recipient_id = 当前用户。
 */
@Mapper
public interface SysNoticeMapper {

    String COLUMNS = "n.id, n.title, n.content, n.category, n.level, n.recipient_id AS recipientId, "
            + "n.biz_type AS bizType, n.biz_id AS bizId, n.dedupe_key AS dedupeKey, "
            + "n.create_by AS createBy, DATE_FORMAT(n.create_time, '%Y-%m-%d %H:%i') AS createTime";

    @Insert("INSERT INTO sys_notice (title, content, category, level, recipient_id, biz_type, biz_id, dedupe_key, "
            + "create_by, create_time) VALUES (#{title}, #{content}, #{category}, #{level}, #{recipientId}, "
            + "#{bizType}, #{bizId}, #{dedupeKey}, #{createBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysNotice notice);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            ,
                   CASE WHEN r.notice_id IS NULL THEN 0 ELSE 1 END AS readFlag
            FROM sys_notice n
            LEFT JOIN sys_notice_read r ON r.notice_id = n.id AND r.user_id = #{userId}
            WHERE n.deleted = 0 AND (n.recipient_id IS NULL OR n.recipient_id = #{userId})
            <if test="unreadOnly">AND r.notice_id IS NULL</if>
            ORDER BY n.id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<SysNotice> selectMine(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly,
                               @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_notice n
            LEFT JOIN sys_notice_read r ON r.notice_id = n.id AND r.user_id = #{userId}
            WHERE n.deleted = 0 AND (n.recipient_id IS NULL OR n.recipient_id = #{userId})
            <if test="unreadOnly">AND r.notice_id IS NULL</if>
            </script>
            """)
    long countMine(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly);

    @Select("SELECT " + COLUMNS + " FROM sys_notice n "
            + "WHERE n.id = #{id} AND n.deleted = 0 AND (n.recipient_id IS NULL OR n.recipient_id = #{userId})")
    SysNotice selectVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    /** 发送记录：我发出的全部通知（含发给别人的），带接收人昵称 */
    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            ,
                   CASE WHEN n.recipient_id IS NULL THEN '全员' ELSE COALESCE(u.nickname, CONCAT('用户 ', n.recipient_id)) END AS recipientName
            FROM sys_notice n
            LEFT JOIN sys_user u ON u.id = n.recipient_id
            WHERE n.deleted = 0 AND n.create_by = #{operator}
            <if test="keyword != null and keyword != ''">AND n.title LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY n.id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<SysNotice> selectSent(@Param("operator") String operator, @Param("keyword") String keyword,
                               @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_notice n
            WHERE n.deleted = 0 AND n.create_by = #{operator}
            <if test="keyword != null and keyword != ''">AND n.title LIKE CONCAT('%', #{keyword}, '%')</if>
            </script>
            """)
    long countSent(@Param("operator") String operator, @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM sys_notice n WHERE n.dedupe_key = #{dedupeKey} AND n.deleted = 0")
    long countByDedupeKey(@Param("dedupeKey") String dedupeKey);

    @Insert("INSERT IGNORE INTO sys_notice_read (notice_id, user_id, read_time) VALUES (#{noticeId}, #{userId}, NOW())")
    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO sys_notice_read (notice_id, user_id, read_time) "
            + "SELECT n.id, #{userId}, NOW() FROM sys_notice n "
            + "WHERE n.deleted = 0 AND (n.recipient_id IS NULL OR n.recipient_id = #{userId})")
    int markAllRead(@Param("userId") Long userId);

    @Update("UPDATE sys_notice SET deleted = 1, update_by = #{updateBy}, update_time = NOW() WHERE id = #{id}")
    int softDelete(@Param("id") Long id, @Param("updateBy") String updateBy);
}
