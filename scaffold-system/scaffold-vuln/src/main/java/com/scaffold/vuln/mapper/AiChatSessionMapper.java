package com.scaffold.vuln.mapper;

import com.scaffold.vuln.ai.domain.AiChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 对话会话表访问（ai_chat_session）。
 */
public interface AiChatSessionMapper {

    @Select("SELECT session_id, user_id, title, create_time, update_time FROM ai_chat_session WHERE session_id = #{sessionId}")
    AiChatSession selectById(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT session_id, user_id, title, create_time, update_time
            FROM ai_chat_session
            WHERE user_id = #{userId}
            ORDER BY update_time DESC
            """)
    List<AiChatSession> selectByUser(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO ai_chat_session (user_id, title, create_time, update_time)
            VALUES (#{userId}, #{title}, #{createTime}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "sessionId")
    int insert(AiChatSession session);

    @Update("UPDATE ai_chat_session SET title = #{title}, update_time = #{updateTime} WHERE session_id = #{sessionId}")
    int update(AiChatSession session);

    @Delete("DELETE FROM ai_chat_session WHERE update_time < #{time}")
    int deleteBefore(@Param("time") java.util.Date time);
}
