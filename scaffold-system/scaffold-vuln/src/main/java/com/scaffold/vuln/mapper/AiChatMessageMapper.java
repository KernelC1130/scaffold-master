package com.scaffold.vuln.mapper;

import com.scaffold.vuln.ai.domain.AiChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 对话消息表访问（ai_chat_message）。
 */
public interface AiChatMessageMapper {

    @Select("""
            SELECT id, session_id, role, content, create_time
            FROM ai_chat_message
            WHERE session_id = #{sessionId}
            ORDER BY create_time ASC, id ASC
            """)
    List<AiChatMessage> selectBySession(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO ai_chat_message (session_id, role, content, create_time)
            VALUES (#{sessionId}, #{role}, #{content}, #{createTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiChatMessage message);

    @Delete("DELETE FROM ai_chat_message WHERE create_time < #{time}")
    int deleteBefore(@Param("time") java.util.Date time);
}
